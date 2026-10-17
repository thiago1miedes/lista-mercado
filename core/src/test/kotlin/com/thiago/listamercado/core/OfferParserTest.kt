package com.thiago.listamercado.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferParserTest {

    private fun List<ParsedOffer>.byName(name: String): ParsedOffer =
        firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw AssertionError("Oferta '$name' não encontrada em: $this")

    @Test
    fun parsesNameAndPriceOnSameLine() {
        val offers = OfferParser.parseText(
            """
            Arroz Tio João 5kg R$ 24,90
            Feijão Carioca Kicaldo 1kg ...... 7,49
            Banana prata R$ 5,99/kg
            Detergente Ypê 500ml 2,39 cada
            """.trimIndent(),
        )
        assertEquals(4, offers.size)
        assertEquals(2490L, offers.byName("Arroz Tio João 5kg").priceCents)
        assertEquals(749L, offers.byName("Feijão Carioca Kicaldo 1kg").priceCents)
        assertEquals("kg", offers.byName("Banana prata").unit)
        assertEquals(239L, offers.byName("Detergente Ypê 500ml").priceCents)
    }

    @Test
    fun parsesNameOnPreviousLines() {
        val offers = OfferParser.parseText(
            """
            OFERTAS DA SEMANA
            CAFÉ PILÃO
            TRADICIONAL 500G
            R$ 18,98
            LEITE INTEGRAL ITALAC 1L
            4,79
            Imagens meramente ilustrativas
            """.trimIndent(),
        )
        assertEquals(2, offers.size)
        assertEquals(1898L, offers.byName("Café Pilão Tradicional 500g").priceCents)
        assertEquals(479L, offers.byName("Leite Integral Italac 1l").priceCents)
    }

    @Test
    fun parsesRegularAndPromotionalPrice() {
        val offers = OfferParser.parseText(
            """
            Óleo de soja Soya 900ml De R$ 9,99 Por R$ 7,49
            Açúcar refinado União 1kg
            De R$ 6,49
            Por R$ 4,99
            """.trimIndent(),
        )
        assertEquals(2, offers.size)
        val oleo = offers.byName("Óleo de soja Soya 900ml")
        assertEquals(749L, oleo.priceCents)
        assertEquals(999L, oleo.regularPriceCents)
        val acucar = offers.byName("Açúcar refinado União 1kg")
        assertEquals(499L, acucar.priceCents)
        assertEquals(649L, acucar.regularPriceCents)
    }

    @Test
    fun ignoresPackageSizesThatLookLikePrices() {
        val offers = OfferParser.parseText("Refrigerante Guaraná 1,50 L R$ 6,99")
        assertEquals(1, offers.size)
        assertEquals(699L, offers[0].priceCents)
    }

    @Test
    fun parsesSeveralOffersOnOneLine() {
        val offers = OfferParser.parseText("Arroz 5kg 19,90 Feijão 1kg 7,49 Açúcar 1kg 4,99")
        assertEquals(listOf(1990L, 749L, 499L), offers.map { it.priceCents })
        assertEquals(listOf("Arroz 5kg", "Feijão 1kg", "Açúcar 1kg"), offers.map { it.name })
    }

    @Test
    fun parsesSuperscriptCentsWithCurrency() {
        val offers = OfferParser.parseText("Sabão em pó Omo 1,6kg R$ 19 90")
        assertEquals(1990L, offers.single().priceCents)
    }

    @Test
    fun parsesPriceBeforeName() {
        val offers = OfferParser.parseText(
            """
            R$ 3,99
            Macarrão espaguete Renata 500g
            """.trimIndent(),
        )
        assertEquals("Macarrão espaguete Renata 500g", offers.single().name)
        assertEquals(399L, offers.single().priceCents)
    }

    @Test
    fun spatialParserLinksPricesToNamesAbove() {
        // Encarte em grade 2x1: nome em cima, preço embaixo, colunas lado a lado.
        val page = listOf(
            TextBox("OFERTAS IMPERDÍVEIS", 0f, 0f, 600f, 40f),
            TextBox("ARROZ TIO JOÃO", 20f, 100f, 260f, 120f),
            TextBox("TIPO 1 5KG", 20f, 124f, 160f, 144f),
            TextBox("FEIJÃO CARIOCA", 320f, 100f, 560f, 120f),
            TextBox("CAMIL 1KG", 320f, 124f, 450f, 144f),
            TextBox("R$", 20f, 160f, 50f, 180f),
            TextBox("24", 55f, 150f, 120f, 210f),
            TextBox("90", 125f, 150f, 150f, 170f),
            TextBox("R$ 7,49", 320f, 160f, 470f, 210f),
            TextBox("De R$ 9,99", 320f, 214f, 420f, 230f),
        )
        val offers = OfferParser.parsePage(page)
        assertEquals(2, offers.size)
        val arroz = offers.byName("Arroz Tio João Tipo 1 5kg")
        assertEquals(2490L, arroz.priceCents)
        val feijao = offers.byName("Feijão Carioca Camil 1kg")
        assertEquals(749L, feijao.priceCents)
        assertEquals(999L, feijao.regularPriceCents)
    }

    @Test
    fun groupsWordsIntoColumns() {
        val words = listOf(
            TextBox("Arroz", 10f, 10f, 60f, 22f),
            TextBox("5kg", 64f, 10f, 90f, 22f),
            TextBox("Feijão", 300f, 10f, 350f, 22f),
            TextBox("1kg", 354f, 10f, 380f, 22f),
        )
        val chunks = OfferParser.groupWords(words)
        assertEquals(listOf("Arroz 5kg", "Feijão 1kg"), chunks.map { it.text })
    }

    @Test
    fun detectsValidity() {
        val today = LocalDate.of(2026, 10, 17)
        assertEquals(
            LocalDate.of(2026, 10, 20),
            OfferParser.detectValidity("Ofertas válidas de 15/10 a 20/10/2026", today),
        )
        assertEquals(
            LocalDate.of(2027, 1, 5),
            OfferParser.detectValidity("Válido até 05/01", LocalDate.of(2026, 12, 28)),
        )
        assertNull(OfferParser.detectValidity("Arroz 5kg 19,90", today))
    }

    @Test
    fun ignoresNoiseLines() {
        val offers = OfferParser.parseText(
            """
            Ofertas válidas enquanto durarem os estoques
            www.supermercado.com.br
            Leve 3 Pague 2
            """.trimIndent(),
        )
        assertTrue(offers.isEmpty())
    }
    @Test
    fun parsesGridFromPdfWords() {
        // Palavras soltas como o PdfBox entrega: grade 2x2 com nome em cima e preço embaixo.
        fun word(text: String, x: Float, y: Float, h: Float = 12f) =
            TextBox(text, x, y, x + text.length * h * 0.55f, y + h)
        val words = listOf(
            word("Leite", 20f, 100f), word("Integral", 56f, 100f), word("Piracanjuba", 116f, 100f), word("1L", 200f, 100f),
            word("R$", 20f, 130f), word("4,99", 42f, 122f, 24f),
            word("Café", 320f, 100f), word("Melitta", 356f, 100f), word("500g", 412f, 100f),
            word("R$", 320f, 130f), word("19,90", 342f, 122f, 24f),
            word("Sabão", 20f, 300f), word("em", 64f, 300f), word("Pó", 86f, 300f), word("Omo", 108f, 300f), word("1,6kg", 132f, 300f),
            word("De", 20f, 320f, 10f), word("R$", 40f, 320f, 10f), word("29,90", 60f, 320f, 10f),
            word("Por", 20f, 336f, 10f), word("R$", 44f, 336f, 10f), word("22,90", 64f, 330f, 24f),
            word("Papel", 320f, 300f), word("Higiênico", 360f, 300f), word("Neve", 430f, 300f),
            word("12", 320f, 316f), word("rolos", 340f, 316f),
            word("R$", 320f, 340f), word("15,49", 342f, 334f, 24f),
            word("Ofertas", 20f, 500f, 8f), word("válidas", 56f, 500f, 8f), word("até", 92f, 500f, 8f), word("20/10/2026", 110f, 500f, 8f),
        )
        val offers = OfferParser.parsePage(OfferParser.groupWords(words))
        assertEquals(4, offers.size)
        assertEquals(499L, offers.byName("Leite Integral Piracanjuba 1L").priceCents)
        assertEquals(1990L, offers.byName("Café Melitta 500g").priceCents)
        val sabao = offers.byName("Sabão em Pó Omo 1,6kg")
        assertEquals(2290L, sabao.priceCents)
        assertEquals(2990L, sabao.regularPriceCents)
        assertEquals(1549L, offers.byName("Papel Higiênico Neve 12 rolos").priceCents)
    }
}
