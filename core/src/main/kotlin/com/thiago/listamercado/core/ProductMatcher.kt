package com.thiago.listamercado.core

data class CatalogItem(val id: Long, val name: String, val favorite: Boolean = false)

data class ProductMatch(val item: CatalogItem, val score: Double)

/**
 * Encontra, no cadastro do usuário, o produto que corresponde a um nome lido de um encarte.
 * Ex.: o produto "Arroz Tio João" corresponde à oferta "ARROZ TIO JOÃO TIPO 1 5KG".
 */
class ProductMatcher(catalog: List<CatalogItem>) {

    private val prepared = catalog
        .map { it to TextNormalizer.analyze(it.name) }
        .filter { it.second.words.isNotEmpty() }

    fun bestMatch(offerName: String, minScore: Double = MIN_SCORE): ProductMatch? {
        val offer = TextNormalizer.analyze(offerName)
        if (offer.words.isEmpty()) return null
        var best: ProductMatch? = null
        for ((item, product) in prepared) {
            val score = score(product, offer)
            if (score < minScore) continue
            val current = best
            if (current == null || score > current.score + 1e-9 ||
                (score > current.score - 1e-9 && item.favorite && !current.item.favorite)
            ) {
                best = ProductMatch(item, score)
            }
        }
        return best
    }

    companion object {
        const val MIN_SCORE = 0.7

        fun score(product: NameAnalysis, offer: NameAnalysis): Double {
            if (product.words.isEmpty() || offer.words.isEmpty()) return 0.0
            // A primeira palavra do produto (normalmente o "tipo": arroz, leite, café...) precisa aparecer.
            val first = product.words.first()
            if (offer.words.none { TextNormalizer.wordsMatch(first, it) }) return 0.0

            val matched = product.words.count { pw -> offer.words.any { TextNormalizer.wordsMatch(pw, it) } }
            val ratio = matched.toDouble() / product.words.size
            val required = if (product.words.size <= 3) 1.0 else 0.75
            if (ratio < required) return 0.0

            val explained = offer.words.count { ow -> product.words.any { TextNormalizer.wordsMatch(it, ow) } }
            val coverage = explained.toDouble() / offer.words.size
            var score = 0.7 * ratio + 0.3 * coverage
            if (product.sizes.isNotEmpty() && offer.sizes.isNotEmpty() && product.sizes.intersect(offer.sizes).isEmpty()) {
                score -= 0.2
            }
            return score
        }
    }
}
