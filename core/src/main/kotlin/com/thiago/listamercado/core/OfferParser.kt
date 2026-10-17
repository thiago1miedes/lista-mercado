package com.thiago.listamercado.core

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/** Uma oferta lida de um encarte (PDF, foto ou texto colado). */
data class ParsedOffer(
    val name: String,
    val priceCents: Long,
    val regularPriceCents: Long? = null,
    val unit: String? = null,
)

/** Um trecho de texto com sua posição na página (coordenadas com y crescendo para baixo). */
data class TextBox(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerY: Float get() = (top + bottom) / 2f
}

internal data class PriceHit(
    val start: Int,
    val end: Int,
    val cents: Long,
    val unit: String?,
    val hasCurrency: Boolean,
)

/** Encontra preços no formato brasileiro ("R$ 12,99", "12,99/kg", "R$ 4 99"...). */
internal object PriceScanner {
    private val withComma = Regex("""(R\$\s*)?(?<![\d.,])(\d{1,3}(?:\.\d{3})+|\d{1,4})\s?,\s?(\d{2})(?![\d%])""", RegexOption.IGNORE_CASE)
    private val currencySeparated = Regex("""(R\$\s*)(\d{1,4})[.\s](\d{2})(?![\d,.%])""", RegexOption.IGNORE_CASE)
    private val currencyJoined = Regex("""(R\$\s*)(\d{1,2})(\d{2})(?![\d,.%])""", RegexOption.IGNORE_CASE)
    private val unitAfter = Regex(
        """^\s*(/\s*)?(kg|un|und|unid|unidade|cada|pct|pacote|cx|caixa|bandeja|bdj|dz|duzia|dúzia|l|lt|litro|100\s?g)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val sizeUnits = setOf("l", "lt", "litro")

    const val MIN_CENTS = 10L
    const val MAX_CENTS = 500_000L

    fun scan(text: String): List<PriceHit> {
        val raw = mutableListOf<PriceHit>()
        fun add(m: MatchResult) {
            val start = m.range.first
            val end = m.range.last + 1
            if (raw.any { start < it.end && end > it.start }) return
            val reais = m.groupValues[2].replace(".", "").toLongOrNull() ?: return
            val cents = reais * 100 + m.groupValues[3].toLong()
            raw += PriceHit(start, end, cents, null, m.groupValues[1].isNotEmpty())
        }
        withComma.findAll(text).forEach(::add)
        currencySeparated.findAll(text).forEach(::add)
        currencyJoined.findAll(text).forEach(::add)
        raw.sortBy { it.start }

        data class Candidate(val hit: PriceHit, val looksLikeSize: Boolean)
        val candidates = raw.map { hit ->
            val match = unitAfter.find(text.substring(hit.end))
            if (match == null) {
                Candidate(hit, false)
            } else {
                val slash = match.groupValues[1].isNotEmpty()
                val unit = canonicalUnit(match.groupValues[2])
                // "1,50 l" sem "R$" e sem barra é tamanho de embalagem, não preço.
                val sizeLike = !hit.hasCurrency && !slash && (unit == "kg" || unit in sizeUnits)
                Candidate(hit.copy(end = hit.end + match.range.last + 1, unit = unit), sizeLike)
            }
        }
        val hasRealPrice = candidates.any { !it.looksLikeSize }
        return candidates
            .filter { !it.looksLikeSize || (!hasRealPrice && it.hit.unit == "kg") }
            .map { it.hit }
            .filter { it.cents in MIN_CENTS..MAX_CENTS }
    }

    private fun canonicalUnit(raw: String): String = when (val u = raw.lowercase().replace(" ", "")) {
        "und", "unid", "unidade", "cada" -> "un"
        "pacote" -> "pct"
        "caixa" -> "cx"
        "bdj" -> "bandeja"
        "duzia", "dúzia" -> "dz"
        "lt", "litro" -> "l"
        else -> u
    }
}

internal data class Segment(
    val name: String,
    val priceCents: Long,
    val regularCents: Long?,
    val unit: String?,
    /** "De R$ 9,99" sozinho: é o preço antigo de uma oferta próxima. */
    val regularMarker: Boolean,
)

internal data class LineSegments(val segments: List<Segment>, val trailing: String)

internal object OfferText {
    private val porWords = setOf("por", "por apenas", "por so", "agora", "agora por", "sai por", "p", "so")
    private val combo = Regex("""(\bleve\b|\b\d+\s*(un\w*\s*)?(por|x)\s*$)""")
    private val edgeWords = setOf(
        "de", "por", "apenas", "so", "oferta", "r", "rs", "cada", "a", "o", "un", "und", "unid", "kg",
        "agora", "sai", "preco", "unidade", "em", "no", "na", "ate", "ou", "x", "e", "com", "/", "-",
    )
    private val edgePunctuation = charArrayOf(' ', '-', '–', '—', ':', '•', '*', '|', '.', ',', ';', '/', '(', ')', '+', '!', '=', '·')
    private val leftoverPrice = Regex("""R\$|\d+\s?%|%""", RegexOption.IGNORE_CASE)
    private val sizeWord = Regex("""^\d+([.,]\d+)?(kg|g|gr|grs|ml|l|lt|un|m|cm|x)?$""", RegexOption.IGNORE_CASE)
    private val connectors = setOf("de", "da", "do", "das", "dos", "com", "e", "c/", "s/", "em", "para")
    private val noiseExact = setOf(
        "oferta", "ofertas", "super oferta", "superoferta", "imperdivel", "promocao", "promocoes",
        "so hoje", "cada", "kg", "un", "unidade", "a unidade", "o kg", "apenas", "por apenas",
        "preco baixo", "precinho", "exclusivo", "leve", "pague", "r", "rs", "de", "por", "novidade",
        "lancamento", "aproveite", "confira", "ofertas da semana", "oferta da semana",
    )
    private val noiseContains = listOf(
        "valid", "ilustrativ", "enquanto durarem", "estoque", "www", "http", "com br", "instagram",
        "facebook", "whatsapp", "televendas", "cnpj", "por cliente", "reservamo", "direito de",
        "limite de", "consulte", "sujeito", "imagens", "fotos", "erros", "grafic",
    )
    private val leveePague = Regex("""leve\s*\d+\s*pague\s*\d+""")

    fun letters(s: String): Int = s.count { it.isLetter() }

    fun isNoise(text: String): Boolean {
        val n = TextNormalizer.normalize(text)
        if (n.isEmpty()) return true
        if (text.length > 90) return true
        if (n in noiseExact) return true
        if (noiseContains.any { n.contains(it) }) return true
        if (leveePague.containsMatchIn(n)) return true
        return false
    }

    fun segmentLine(line: String): LineSegments {
        val hits = PriceScanner.scan(line)
        if (hits.isEmpty()) return LineSegments(emptyList(), line)
        val segments = mutableListOf<Segment>()
        var cursor = 0
        var i = 0
        while (i < hits.size) {
            val hit = hits[i]
            val before = if (hit.start >= cursor) line.substring(cursor, hit.start) else ""
            val beforeNorm = TextNormalizer.normalize(before)
            val beforeName = cleanName(before)
            if (segments.isNotEmpty() && letters(beforeName) < 3 && combo.containsMatchIn(beforeNorm)) {
                // "2 por R$ 10,00", "leve 3": condição de atacado da oferta anterior.
                cursor = hit.end
                i++
                continue
            }
            var price = hit
            var regular: Long? = null
            if (i + 1 < hits.size) {
                val next = hits[i + 1]
                val between = TextNormalizer.normalize(line.substring(hit.end, max(hit.end, next.start)))
                if (between in porWords && next.cents < hit.cents) {
                    regular = hit.cents
                    price = next
                    i++
                }
            }
            i++
            val marker = regular == null && letters(beforeName) < 3 &&
                beforeNorm.split(' ').lastOrNull() == "de"
            segments += Segment(beforeName, price.cents, regular, price.unit, marker)
            cursor = price.end
        }
        return LineSegments(segments, line.substring(min(cursor, line.length)))
    }

    /** Remove sobras de preço, palavras de propaganda e pontuação; corrige texto TODO EM MAIÚSCULAS. */
    fun cleanName(raw: String): String {
        var words = raw.replace(leftoverPrice, " ").split(Regex("\\s+"))
            .map { it.trim(*edgePunctuation) }
            .filter { it.isNotEmpty() }
        while (words.isNotEmpty() && TextNormalizer.normalize(words.first()).let { it.isEmpty() || it in edgeWords }) {
            words = words.drop(1)
        }
        while (words.isNotEmpty() && TextNormalizer.normalize(words.last()).let { it.isEmpty() || it in edgeWords }) {
            words = words.dropLast(1)
        }
        val text = words.joinToString(" ")
        val upper = text.count { it.isUpperCase() }
        val lower = text.count { it.isLowerCase() }
        if (upper < 3 || upper < lower * 2) return text
        return words.mapIndexed { index, word ->
            val lw = word.lowercase()
            when {
                sizeWord.matches(word) -> lw
                index > 0 && lw in connectors -> lw
                else -> lw.replaceFirstChar { it.titlecase() }
            }
        }.joinToString(" ")
    }
}

object OfferParser {

    /**
     * Lê texto corrido (copiado de um site, PDF com texto, etc). Associa cada preço ao nome
     * escrito antes dele na mesma linha ou nas linhas anteriores.
     */
    fun parseText(text: String): List<ParsedOffer> {
        val offers = mutableListOf<ParsedOffer>()
        val pending = ArrayDeque<String>()
        var pendingRegular: Long? = null
        var orphan: Segment? = null

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val parsed = OfferText.segmentLine(line)
            if (parsed.segments.isEmpty()) {
                if (OfferText.isNoise(line)) continue
                val name = OfferText.cleanName(line)
                if (OfferText.letters(name) < 3) continue
                val waiting = orphan
                if (waiting != null) {
                    offers += ParsedOffer(name, waiting.priceCents, waiting.regularCents, waiting.unit)
                    orphan = null
                    continue
                }
                pending.addLast(name)
                while (pending.size > 3) pending.removeFirst()
                continue
            }
            for (segment in parsed.segments) {
                if (segment.regularMarker) {
                    pendingRegular = segment.priceCents
                    continue
                }
                val regular = segment.regularCents ?: pendingRegular?.takeIf { it > segment.priceCents }
                pendingRegular = null
                val name = when {
                    OfferText.letters(segment.name) >= 3 -> segment.name
                    pending.isNotEmpty() -> pending.toList().takeLast(2).joinToString(" ")
                    else -> null
                }
                if (name == null) {
                    orphan = segment.copy(regularCents = regular)
                } else {
                    offers += ParsedOffer(name, segment.priceCents, regular, segment.unit)
                    orphan = null
                }
                pending.clear()
            }
            val trailing = OfferText.cleanName(parsed.trailing)
            if (OfferText.letters(trailing) >= 3 && !OfferText.isNoise(trailing)) pending.addLast(trailing)
        }
        return dedupe(offers)
    }

    /**
     * Lê páginas com posição (PDF com texto ou OCR de imagem). Em encartes cada produto fica num
     * "quadrado": o nome em cima e o preço embaixo (ou ao lado). Cada preço é ligado ao texto
     * mais próximo acima/à esquerda dele.
     */
    fun parsePages(pages: List<List<TextBox>>): List<ParsedOffer> =
        dedupe(pages.flatMap { parsePage(it) })

    fun parsePage(input: List<TextBox>): List<ParsedOffer> {
        val boxes = mergePriceFragments(input.filter { it.text.isNotBlank() && it.width >= 0f && it.height > 0f })
        if (boxes.isEmpty()) return emptyList()
        val refHeight = boxes.map { it.height }.sorted()[boxes.size / 2]

        class Entry(val box: TextBox, var name: String, val segment: Segment)

        val ready = mutableListOf<Entry>()
        val needName = mutableListOf<Entry>()
        val markers = mutableListOf<Pair<TextBox, Long>>()
        val names = mutableListOf<TextBox>()

        for (box in boxes) {
            val parsed = OfferText.segmentLine(box.text)
            if (parsed.segments.isEmpty()) {
                if (!OfferText.isNoise(box.text) && OfferText.letters(OfferText.cleanName(box.text)) >= 3) names += box
                continue
            }
            for (segment in parsed.segments) {
                when {
                    segment.regularMarker -> markers += box to segment.priceCents
                    OfferText.letters(segment.name) >= 3 -> ready += Entry(box, segment.name, segment)
                    else -> needName += Entry(box, "", segment)
                }
            }
        }

        // Associação gulosa preço ↔ nome pelo menor "custo" de distância.
        data class Pair3(val entry: Int, val name: Int, val cost: Float)
        val pairs = mutableListOf<Pair3>()
        needName.forEachIndexed { ei, entry ->
            names.forEachIndexed { ni, nameBox ->
                val cost = associationCost(entry.box, nameBox, refHeight)
                if (cost.isFinite()) pairs += Pair3(ei, ni, cost)
            }
        }
        pairs.sortBy { it.cost }
        val entryName = HashMap<Int, Int>()
        val usedNames = HashSet<Int>()
        for (pair in pairs) {
            if (pair.entry in entryName || pair.name in usedNames) continue
            entryName[pair.entry] = pair.name
            usedNames += pair.name
        }
        // Nomes em mais de uma linha: junta as linhas logo acima do nome escolhido.
        for ((ei, ni) in entryName.entries.sortedBy { it.key }) {
            val lines = mutableListOf(names[ni])
            repeat(2) {
                val top = lines.first()
                val above = names.indices
                    .filter { it !in usedNames }
                    .map { it to names[it] }
                    .filter { (_, b) ->
                        b.bottom <= top.top + top.height * 0.4f &&
                            top.top - b.bottom < refHeight * 1.2f &&
                            overlapRatio(b, top) >= 0.3f
                    }
                    .minByOrNull { (_, b) -> top.top - b.bottom }
                if (above != null) {
                    lines.add(0, above.second)
                    usedNames += above.first
                }
            }
            needName[ei].name = OfferText.cleanName(lines.joinToString(" ") { it.text })
        }

        val result = (ready + needName.filter { OfferText.letters(it.name) >= 3 })
        // "De R$ 9,99" solto: vira preço normal da oferta mais próxima com preço menor.
        val regularOf = HashMap<Entry, Long>()
        for ((box, cents) in markers) {
            val target = result
                .filter { it.segment.regularCents == null && it.segment.priceCents < cents }
                .minByOrNull { distance(box, it.box) }
            if (target != null && distance(box, target.box) < refHeight * 6f) regularOf[target] = cents
        }
        return result.map {
            ParsedOffer(it.name, it.segment.priceCents, it.segment.regularCents ?: regularOf[it], it.segment.unit)
        }
    }

    private fun associationCost(price: TextBox, name: TextBox, refHeight: Float): Float {
        val horizontalGap = max(0f, max(price.left, name.left) - min(price.right, name.right))
        val verticalOverlap = min(price.bottom, name.bottom) - max(price.top, name.top)
        return when {
            // Nome acima do preço.
            name.centerY < price.top + price.height * 0.3f && name.bottom <= price.top + price.height * 0.5f -> {
                val dy = max(0f, price.top - name.bottom)
                if (dy > refHeight * 8f || horizontalGap > refHeight * 6f) Float.POSITIVE_INFINITY
                else dy + horizontalGap * 1.5f
            }
            // Nome na mesma linha, à esquerda.
            verticalOverlap > min(price.height, name.height) * 0.4f && name.right <= price.left + refHeight * 0.5f -> {
                val dx = max(0f, price.left - name.right)
                if (dx > refHeight * 10f) Float.POSITIVE_INFINITY else dx * 0.8f
            }
            // Nome abaixo do preço (menos comum).
            name.top >= price.bottom - price.height * 0.3f -> {
                val dy = max(0f, name.top - price.bottom)
                if (dy > refHeight * 5f || horizontalGap > refHeight * 4f) Float.POSITIVE_INFINITY
                else (dy + horizontalGap * 1.5f) * 2.5f + refHeight
            }
            else -> Float.POSITIVE_INFINITY
        }
    }

    private fun overlapRatio(a: TextBox, b: TextBox): Float {
        val overlap = min(a.right, b.right) - max(a.left, b.left)
        val narrower = min(a.width, b.width)
        return if (narrower <= 0f) 0f else overlap / narrower
    }

    private fun distance(a: TextBox, b: TextBox): Float {
        val dx = max(0f, max(a.left, b.left) - min(a.right, b.right))
        val dy = max(0f, max(a.top, b.top) - min(a.bottom, b.bottom))
        return dx + dy
    }

    private val integerFragment = Regex("""^(R\$\s*)?\d{1,4}\s?[,.]?$""", RegexOption.IGNORE_CASE)
    private val centsFragment = Regex("""^[,.]?\s?\d{2}(\s.*)?$""")
    private val currencyOnly = Regex("""^R\$$""", RegexOption.IGNORE_CASE)

    /** OCR costuma separar "R$", "4" e "99" (centavos pequenos) em pedaços diferentes. */
    internal fun mergePriceFragments(input: List<TextBox>): List<TextBox> {
        val boxes = input.sortedWith(compareBy({ it.top }, { it.left })).toMutableList()
        var changed = true
        while (changed) {
            changed = false
            loop@ for (i in boxes.indices) {
                val a = boxes[i]
                val aText = a.text.trim()
                val isCurrency = currencyOnly.matches(aText)
                if (!isCurrency && !integerFragment.matches(aText)) continue
                for (j in boxes.indices) {
                    if (i == j) continue
                    val b = boxes[j]
                    val bText = b.text.trim()
                    val gap = b.left - a.right
                    val sameBand = b.top < a.bottom && b.bottom > a.top - a.height * 0.3f
                    if (!sameBand || gap < -a.height * 0.3f || gap > a.height * 0.9f) continue
                    val merged = when {
                        isCurrency && integerFragment.matches(bText) -> "$aText $bText"
                        !isCurrency && centsFragment.matches(bText) && !aText.endsWith(",") && !bText.startsWith(",") &&
                            !aText.endsWith(".") && !bText.startsWith(".") -> "$aText,$bText"
                        !isCurrency && centsFragment.matches(bText) -> "$aText$bText"
                        else -> continue
                    }
                    boxes[i] = TextBox(merged, min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))
                    boxes.removeAt(j)
                    changed = true
                    break@loop
                }
            }
        }
        return boxes
    }

    private fun dedupe(offers: List<ParsedOffer>): List<ParsedOffer> =
        offers.distinctBy { TextNormalizer.normalize(it.name) to it.priceCents }

    private val dateRegex = Regex("""(?<!\d)(\d{1,2})\s?/\s?(\d{1,2})(?:\s?/\s?(\d{4}|\d{2}))?(?!\d)""")
    private val validityHints = listOf("valid", "ate ", "ofertas", "periodo", "vigencia", " a ")

    /** Procura "válido até 20/10" / "ofertas de 15/10 a 20/10/2026" e devolve a data final. */
    fun detectValidity(text: String, today: LocalDate): LocalDate? {
        var best: LocalDate? = null
        for (line in text.lines()) {
            val normalized = " " + TextNormalizer.normalize(line) + " "
            if (validityHints.none { normalized.contains(it) }) continue
            for (m in dateRegex.findAll(line)) {
                val day = m.groupValues[1].toInt()
                val month = m.groupValues[2].toInt()
                val yearText = m.groupValues[3]
                var year = when {
                    yearText.length == 4 -> yearText.toInt()
                    yearText.length == 2 -> 2000 + yearText.toInt()
                    else -> today.year
                }
                val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: continue
                var candidate = date
                if (yearText.isEmpty() && candidate.isBefore(today.minusMonths(6))) {
                    year += 1
                    candidate = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: continue
                }
                if (candidate.isBefore(today.minusDays(7)) || candidate.isAfter(today.plusDays(180))) continue
                if (best == null || candidate.isAfter(best)) best = candidate
            }
        }
        return best
    }

    /** Agrupa palavras soltas (com posição) em linhas/blocos, separando colunas distantes. */
    fun groupWords(words: List<TextBox>): List<TextBox> {
        val sorted = words.filter { it.text.isNotBlank() && it.height > 0f }.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<TextBox>>()
        for (word in sorted) {
            val row = rows.lastOrNull { r ->
                val top = r.minOf { it.top }
                val bottom = r.maxOf { it.bottom }
                val overlap = min(bottom, word.bottom) - max(top, word.top)
                overlap >= min(bottom - top, word.height) * 0.5f
            }
            if (row != null) row += word else rows += mutableListOf(word)
        }
        val chunks = mutableListOf<TextBox>()
        for (row in rows) {
            val ordered = row.sortedBy { it.left }
            var current = mutableListOf(ordered.first())
            for (word in ordered.drop(1)) {
                val previous = current.last()
                val gap = word.left - previous.right
                val threshold = (previous.height + word.height) / 2f * 1.0f
                if (gap > threshold) {
                    chunks += join(current)
                    current = mutableListOf(word)
                } else {
                    current += word
                }
            }
            chunks += join(current)
        }
        return chunks
    }

    private fun join(words: List<TextBox>): TextBox = TextBox(
        words.joinToString(" ") { it.text.trim() },
        words.minOf { it.left }, words.minOf { it.top }, words.maxOf { it.right }, words.maxOf { it.bottom },
    )
}
