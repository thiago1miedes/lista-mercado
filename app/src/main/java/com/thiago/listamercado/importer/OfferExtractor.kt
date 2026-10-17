package com.thiago.listamercado.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.thiago.listamercado.core.OfferParser
import com.thiago.listamercado.core.ParsedOffer
import com.thiago.listamercado.core.TextBox
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ExtractionResult(
    val offers: List<ParsedOffer>,
    /** Texto bruto lido (usado para detectar validade e o nome do mercado). */
    val text: String,
    val usedOcr: Boolean,
    val pages: Int,
)

/**
 * Lê ofertas de um encarte:
 * - PDF com texto: extrai palavras com posição (PdfBox);
 * - PDF "imagem" (escaneado) ou foto: reconhece o texto com OCR no próprio aparelho (ML Kit).
 */
class OfferExtractor(private val context: Context) {

    suspend fun fromPdf(uri: Uri, forceOcr: Boolean, onProgress: (String) -> Unit): ExtractionResult =
        withContext(Dispatchers.IO) {
            val file = copyToCache(uri, "encarte.pdf")
            try {
                if (!forceOcr) {
                    onProgress("Lendo o texto do PDF…")
                    val pages = readPdfText(file)
                    val text = pages.joinToString("\n") { page -> page.joinToString("\n") { it.text } }
                    val letters = text.count { it.isLetter() }
                    if (pages.isNotEmpty() && letters >= 40 * pages.size) {
                        val offers = OfferParser.parsePages(pages)
                        if (offers.isNotEmpty()) return@withContext ExtractionResult(offers, text, false, pages.size)
                    }
                }
                val pages = ocrPdf(file, onProgress)
                val text = pages.joinToString("\n") { page -> page.joinToString("\n") { it.text } }
                ExtractionResult(withTextFallback(OfferParser.parsePages(pages), text), text, true, pages.size)
            } finally {
                file.delete()
            }
        }

    suspend fun fromImage(uri: Uri, onProgress: (String) -> Unit): ExtractionResult =
        withContext(Dispatchers.IO) {
            onProgress("Reconhecendo o texto da imagem…")
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val result = recognizer.process(InputImage.fromFilePath(context, uri)).await()
                val boxes = linesOf(result)
                val text = boxes.joinToString("\n") { it.text }
                ExtractionResult(withTextFallback(OfferParser.parsePage(boxes), text), text, true, 1)
            } finally {
                recognizer.close()
            }
        }

    fun fromText(text: String): ExtractionResult =
        ExtractionResult(OfferParser.parseText(text), text, false, 0)

    private fun withTextFallback(spatial: List<ParsedOffer>, text: String): List<ParsedOffer> =
        spatial.ifEmpty { OfferParser.parseText(text) }

    private fun copyToCache(uri: Uri, name: String): File {
        val file = File(context.cacheDir, name)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Não foi possível abrir o arquivo.")
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        return file
    }

    /** Palavras de cada página, agrupadas em blocos de texto com posição. */
    private fun readPdfText(file: File): List<List<TextBox>> {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file).use { document ->
            val stripper = PositionStripper()
            val pages = mutableListOf<List<TextBox>>()
            for (page in 1..document.numberOfPages) {
                stripper.words.clear()
                stripper.startPage = page
                stripper.endPage = page
                stripper.getText(document)
                pages += OfferParser.groupWords(stripper.words.toList())
            }
            return pages
        }
    }

    private class PositionStripper : PDFTextStripper() {
        val words = mutableListOf<TextBox>()

        init {
            sortByPosition = true
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            val positions = textPositions ?: return
            val value = text?.trim().orEmpty()
            if (value.isEmpty() || positions.isEmpty()) return
            var left = Float.MAX_VALUE
            var right = 0f
            var top = Float.MAX_VALUE
            var bottom = 0f
            for (p in positions) {
                val height = max(p.heightDir, p.fontSizeInPt * 0.7f).coerceAtLeast(1f)
                left = min(left, p.xDirAdj)
                right = max(right, p.xDirAdj + p.widthDirAdj)
                top = min(top, p.yDirAdj - height)
                bottom = max(bottom, p.yDirAdj)
            }
            words += TextBox(value, left, top, right, bottom)
        }
    }

    private suspend fun ocrPdf(file: File, onProgress: (String) -> Unit): List<List<TextBox>> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val descriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor).use { renderer ->
                val pages = mutableListOf<List<TextBox>>()
                for (index in 0 until renderer.pageCount) {
                    onProgress("Reconhecendo texto: página ${index + 1} de ${renderer.pageCount}…")
                    val bitmap = renderer.openPage(index).use { page ->
                        // Resolução alta ajuda o OCR com as letras pequenas dos encartes.
                        val scale = (MAX_SIDE_PX / max(page.width, page.height).toFloat()).coerceIn(1f, 4f)
                        val bmp = Bitmap.createBitmap(
                            (page.width * scale).toInt(),
                            (page.height * scale).toInt(),
                            Bitmap.Config.ARGB_8888,
                        )
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                    try {
                        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                        pages += linesOf(result)
                    } finally {
                        bitmap.recycle()
                    }
                }
                return pages
            }
        } finally {
            recognizer.close()
        }
    }

    private fun linesOf(result: Text): List<TextBox> =
        result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                TextBox(line.text, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
            }
        }

    private companion object {
        const val MAX_SIDE_PX = 3000f
    }
}
