package com.thiago.listamercado.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductMatcherTest {
    private val catalog = listOf(
        CatalogItem(1, "Arroz Tio João", favorite = true),
        CatalogItem(2, "Feijão carioca"),
        CatalogItem(3, "Leite"),
        CatalogItem(4, "Leite condensado"),
        CatalogItem(5, "Café Pilão 500g", favorite = true),
        CatalogItem(6, "Refrigerante Coca-Cola 2L"),
        CatalogItem(7, "Papel higiênico"),
    )
    private val matcher = ProductMatcher(catalog)

    @Test
    fun normalizesAccentsAndSizes() {
        assertEquals("feijao carioca 1p5 kg", TextNormalizer.normalize("Feijão Carioca 1,5 KG!"))
        val analysis = TextNormalizer.analyze("Café PILÃO tradicional 500 GR")
        assertEquals(listOf("cafe", "pilao", "tradicional"), analysis.words)
        assertEquals(setOf("500g"), analysis.sizes)
    }

    @Test
    fun matchesFlyerNamesToCatalog() {
        assertEquals(1L, matcher.bestMatch("ARROZ TIO JOÃO TIPO 1 5KG")?.item?.id)
        assertEquals(2L, matcher.bestMatch("Feijao Carioca Kicaldo 1kg")?.item?.id)
        assertEquals(4L, matcher.bestMatch("Leite Condensado Moça 395g")?.item?.id)
        assertEquals(3L, matcher.bestMatch("Leite Integral Italac 1L")?.item?.id)
        assertEquals(5L, matcher.bestMatch("Café Pilão Tradicional 500g")?.item?.id)
        assertEquals(6L, matcher.bestMatch("Refrig. Coca Cola 2L")?.item?.id)
        assertEquals(7L, matcher.bestMatch("PAPEL HIGIENICO NEVE 12 ROLOS")?.item?.id)
    }

    @Test
    fun doesNotMatchUnrelatedProducts() {
        assertNull(matcher.bestMatch("Detergente Ypê 500ml"))
        assertNull(matcher.bestMatch("Arroz integral Camil"))
        assertNull(matcher.bestMatch("Café Melitta 500g"))
    }

    @Test
    fun toleratesSmallOcrErrors() {
        assertEquals(2L, matcher.bestMatch("Feijâo Carioca")?.item?.id)
        assertEquals(7L, matcher.bestMatch("Papel Higienlco Personal")?.item?.id)
    }
}
