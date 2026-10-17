package com.thiago.listamercado.core

enum class PriceKind { PURCHASE, PROMOTION }

data class PriceKey(val productId: Long, val storeId: Long)

/**
 * Um preço conhecido de um produto num mercado: pago numa compra ou anunciado numa promoção.
 * [day] é o dia (epochDay) e [stamp] desempata registros do mesmo dia.
 */
data class PricePoint(
    val productId: Long,
    val storeId: Long,
    val priceCents: Long,
    val day: Long,
    val stamp: Long,
    val kind: PriceKind,
    val validUntilDay: Long? = null,
    val regularPriceCents: Long? = null,
)

data class CurrentPrice(
    val priceCents: Long,
    val day: Long,
    val isPromotion: Boolean,
    val regularPriceCents: Long?,
    val validUntilDay: Long?,
)

data class BasketItem(val productId: Long, val quantity: Double)

data class StoreTotal(
    val storeId: Long,
    /** Soma dos itens com preço conhecido neste mercado. */
    val knownTotalCents: Long,
    /** Total estimado: itens sem preço aqui entram pela média dos outros mercados. */
    val estimatedTotalCents: Long,
    val pricedItems: Int,
    val missingItems: Int,
    val promoItems: Int,
    val promoSavingsCents: Long,
)

data class ItemComparison(
    val productId: Long,
    val quantity: Double,
    val prices: Map<Long, CurrentPrice>,
    val bestStoreId: Long?,
    val bestPriceCents: Long?,
)

data class BasketComparison(
    val ranking: List<StoreTotal>,
    val items: List<ItemComparison>,
    /** Comprando cada item no mercado mais barato. */
    val splitTotalCents: Long,
    /** Itens que têm preço em pelo menos um mercado. */
    val comparableItems: Int,
) {
    val best: StoreTotal? get() = ranking.firstOrNull()?.takeIf { it.pricedItems > 0 }
    val splitSavingsCents: Long get() = best?.let { (it.estimatedTotalCents - splitTotalCents).coerceAtLeast(0) } ?: 0
}

object PriceComparator {

    /**
     * Preço atual de cada produto em cada mercado:
     * 1. a promoção válida mais barata (com data de validade em vigor);
     * 2. senão, o preço mais recente (compra registrada ou preço informado sem validade).
     */
    fun currentPrices(points: List<PricePoint>, today: Long): Map<PriceKey, CurrentPrice> {
        val result = HashMap<PriceKey, CurrentPrice>()
        for ((key, list) in points.groupBy { PriceKey(it.productId, it.storeId) }) {
            pick(list, today)?.let { result[key] = it }
        }
        return result
    }

    private fun pick(points: List<PricePoint>, today: Long): CurrentPrice? {
        val active = points.filter {
            it.kind == PriceKind.PROMOTION && it.validUntilDay != null && it.validUntilDay >= today
        }
        if (active.isNotEmpty()) {
            val p = active.minWith(compareBy<PricePoint> { it.priceCents }.thenByDescending { it.stamp })
            return CurrentPrice(p.priceCents, p.day, true, p.regularPriceCents, p.validUntilDay)
        }
        val latest = points
            .filter { it.kind == PriceKind.PURCHASE || it.validUntilDay == null }
            .maxWithOrNull(compareBy<PricePoint> { it.day }.thenBy { it.stamp })
            ?: return null
        val promo = latest.kind == PriceKind.PROMOTION &&
            latest.regularPriceCents != null && latest.regularPriceCents > latest.priceCents
        return CurrentPrice(latest.priceCents, latest.day, promo, latest.regularPriceCents, null)
    }

    fun compare(
        basket: List<BasketItem>,
        storeIds: Collection<Long>,
        prices: Map<PriceKey, CurrentPrice>,
    ): BasketComparison {
        val merged = basket.groupBy { it.productId }
            .map { (id, items) -> BasketItem(id, items.sumOf { it.quantity }) }

        val items = merged.map { item ->
            val byStore = LinkedHashMap<Long, CurrentPrice>()
            for (store in storeIds) prices[PriceKey(item.productId, store)]?.let { byStore[store] = it }
            val best = byStore.entries.minByOrNull { it.value.priceCents }
            ItemComparison(item.productId, item.quantity, byStore, best?.key, best?.value?.priceCents)
        }
        val comparable = items.filter { it.prices.isNotEmpty() }

        val ranking = storeIds.map { store ->
            var known = 0L
            var estimated = 0L
            var priced = 0
            var promos = 0
            var savings = 0L
            for (item in comparable) {
                val price = item.prices[store]
                if (price != null) {
                    val line = Money.lineTotal(item.quantity, price.priceCents)
                    known += line
                    estimated += line
                    priced++
                    if (price.isPromotion) {
                        promos++
                        val regular = price.regularPriceCents
                        if (regular != null && regular > price.priceCents) {
                            savings += Money.lineTotal(item.quantity, regular - price.priceCents)
                        }
                    }
                } else {
                    val average = item.prices.values.map { it.priceCents }.average()
                    estimated += Money.lineTotal(item.quantity, Math.round(average))
                }
            }
            StoreTotal(store, known, estimated, priced, comparable.size - priced, promos, savings)
        }.sortedWith(
            compareByDescending<StoreTotal> { it.pricedItems > 0 }
                .thenBy { it.estimatedTotalCents }
                .thenByDescending { it.pricedItems },
        )

        val split = comparable.sumOf { Money.lineTotal(it.quantity, it.bestPriceCents ?: 0) }
        return BasketComparison(ranking, items, split, comparable.size)
    }
}
