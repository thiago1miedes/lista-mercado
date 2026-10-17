package com.thiago.listamercado.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceComparatorTest {
    private val today = 20_000L
    private val storeA = 1L
    private val storeB = 2L
    private val arroz = 10L
    private val feijao = 11L
    private val cafe = 12L

    private fun purchase(product: Long, store: Long, cents: Long, day: Long) =
        PricePoint(product, store, cents, day, day, PriceKind.PURCHASE)

    private fun promo(product: Long, store: Long, cents: Long, until: Long?, regular: Long? = null) =
        PricePoint(product, store, cents, today - 1, today, PriceKind.PROMOTION, until, regular)

    @Test
    fun activePromotionWinsOverLastPurchase() {
        val prices = PriceComparator.currentPrices(
            listOf(
                purchase(arroz, storeA, 2500, today - 10),
                promo(arroz, storeA, 1990, until = today + 3, regular = 2500),
            ),
            today,
        )
        val current = prices.getValue(PriceKey(arroz, storeA))
        assertEquals(1990L, current.priceCents)
        assertTrue(current.isPromotion)
    }

    @Test
    fun expiredPromotionFallsBackToLatestPurchase() {
        val prices = PriceComparator.currentPrices(
            listOf(
                purchase(arroz, storeA, 2500, today - 30),
                purchase(arroz, storeA, 2650, today - 5),
                promo(arroz, storeA, 1990, until = today - 1),
            ),
            today,
        )
        val current = prices.getValue(PriceKey(arroz, storeA))
        assertEquals(2650L, current.priceCents)
        assertFalse(current.isPromotion)
    }

    @Test
    fun ranksStoresAndEstimatesMissingPrices() {
        val prices = PriceComparator.currentPrices(
            listOf(
                purchase(arroz, storeA, 2500, today - 2),
                purchase(feijao, storeA, 800, today - 2),
                purchase(cafe, storeA, 1800, today - 2),
                promo(arroz, storeB, 2000, until = today + 2, regular = 2600),
                purchase(feijao, storeB, 900, today - 4),
            ),
            today,
        )
        val comparison = PriceComparator.compare(
            listOf(BasketItem(arroz, 1.0), BasketItem(feijao, 2.0), BasketItem(cafe, 1.0)),
            listOf(storeA, storeB),
            prices,
        )
        // A: 2500 + 1600 + 1800 = 5900. B: 2000 + 1800 + (café estimado 1800) = 5600.
        val best = comparison.best!!
        assertEquals(storeB, best.storeId)
        assertEquals(5600L, best.estimatedTotalCents)
        assertEquals(3800L, best.knownTotalCents)
        assertEquals(1, best.missingItems)
        assertEquals(1, best.promoItems)
        assertEquals(600L, best.promoSavingsCents)
        // Dividindo: arroz em B (2000) + feijão em A (1600) + café em A (1800) = 5400.
        assertEquals(5400L, comparison.splitTotalCents)
        assertEquals(200L, comparison.splitSavingsCents)
        assertEquals(storeA, comparison.items.first { it.productId == feijao }.bestStoreId)
    }
}
