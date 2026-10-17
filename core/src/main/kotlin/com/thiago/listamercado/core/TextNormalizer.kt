package com.thiago.listamercado.core

import java.text.Normalizer

/** Palavras significativas e tamanhos (5kg, 500g, 1p5l...) extraídos do nome de um produto. */
data class NameAnalysis(val words: List<String>, val sizes: Set<String>)

object TextNormalizer {
    private val diacritics = Regex("\\p{Mn}+")
    private val decimalSeparator = Regex("(\\d)[.,](\\d)")
    private val nonAlphanumeric = Regex("[^a-z0-9]+")
    private val spaces = Regex("\\s+")
    private val numberThenUnit =
        Regex("\\b(\\d+(?:p\\d+)?) (kg|g|gr|grs|ml|l|lt|lts|litro|litros|un|und|m)\\b")
    private val sizeToken = Regex("^\\d+(?:p\\d+)?(kg|g|gr|grs|ml|l|lt|lts|litro|litros|un|und|m)$")

    private val stopWords = setOf(
        "de", "da", "do", "das", "dos", "com", "sem", "e", "c", "s", "tipo", "para", "p", "em",
        "a", "o", "as", "os", "no", "na", "un", "und", "unid", "unidade", "cada", "pct", "pacote",
        "emb", "embalagem", "oferta", "ofertas", "leve", "pague", "gratis", "r", "rs", "kg", "g",
        "gr", "ml", "l", "lt", "por", "apenas", "so", "preco", "cx", "caixa", "ou", "x",
    )

    /** "Feijão Carioca 1,5 KG!" → "feijao carioca 1p5 kg" */
    fun normalize(text: String): String {
        val noAccents = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(diacritics, "")
        return noAccents
            .replace(decimalSeparator, "$1p$2")
            .replace(nonAlphanumeric, " ")
            .replace(spaces, " ")
            .trim()
    }

    fun analyze(text: String): NameAnalysis {
        val normalized = normalize(text).replace(numberThenUnit, "$1$2")
        val words = mutableListOf<String>()
        val sizes = mutableSetOf<String>()
        for (token in normalized.split(' ')) {
            if (token.isEmpty()) continue
            when {
                token[0].isDigit() -> if (sizeToken.matches(token)) sizes += canonicalSize(token)
                token in stopWords -> Unit
                token.length < 2 -> Unit
                else -> words += token
            }
        }
        return NameAnalysis(words.distinct(), sizes)
    }

    private fun canonicalSize(token: String): String {
        val unitStart = token.indexOfFirst { it.isLetter() && it != 'p' }.let { if (it < 0) token.length else it }
        val number = token.substring(0, unitStart)
        val unit = when (val u = token.substring(unitStart)) {
            "gr", "grs" -> "g"
            "lt", "lts", "litro", "litros" -> "l"
            "und" -> "un"
            else -> u
        }
        return number + unit
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
    }

    /** Compara palavras tolerando abreviações ("refrig" ~ "refrigerante") e pequenos erros de OCR. */
    fun wordsMatch(a: String, b: String): Boolean {
        if (a == b) return true
        val shortest = minOf(a.length, b.length)
        if (shortest >= 4 && (a.startsWith(b) || b.startsWith(a))) return true
        if (shortest >= 5 && levenshtein(a, b) <= 1) return true
        return false
    }
}
