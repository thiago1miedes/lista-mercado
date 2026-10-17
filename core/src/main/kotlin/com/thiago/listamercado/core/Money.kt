package com.thiago.listamercado.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** Valores monetários são sempre guardados em centavos (Long) para evitar erros de arredondamento. */
object Money {

    /** 129990 → "R$ 1.299,90" */
    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val value = abs(cents)
        val reais = (value / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        val centavos = (value % 100).toString().padStart(2, '0')
        return "${sign}R$ $reais,$centavos"
    }

    /** Formato para campos de edição: 129990 → "1299,90" */
    fun formatInput(cents: Long): String {
        val value = abs(cents)
        return (if (cents < 0) "-" else "") + "${value / 100}," + (value % 100).toString().padStart(2, '0')
    }

    /**
     * Interpreta o que o usuário digitou: "12,99", "12.99", "R$ 1.234,56", "1.234", "7".
     * Retorna null se não for um valor válido (ou se for negativo).
     */
    fun parse(input: String): Long? {
        var s = input.trim()
            .replace("R$", "", ignoreCase = true)
            .replace(" ", "")
            .replace(" ", "")
        if (s.isEmpty()) return null
        val commas = s.count { it == ',' }
        val dots = s.count { it == '.' }
        s = when {
            commas > 0 && dots > 0 ->
                if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.')
                else s.replace(",", "")
            commas > 0 -> {
                val last = s.lastIndexOf(',')
                s.substring(0, last).replace(",", "") + "." + s.substring(last + 1)
            }
            dots > 1 -> s.replace(".", "")
            dots == 1 -> {
                val decimals = s.length - s.lastIndexOf('.') - 1
                if (decimals == 3) s.replace(".", "") else s
            }
            else -> s
        }
        val number = s.toBigDecimalOrNull() ?: return null
        if (number.signum() < 0) return null
        return number.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    fun lineTotal(quantity: Double, unitCents: Long): Long = (quantity * unitCents).roundToLong()

    /** Percentual de desconto (0..100) entre preço normal e promocional. */
    fun discountPercent(regularCents: Long?, priceCents: Long): Int? {
        if (regularCents == null || regularCents <= priceCents || regularCents <= 0) return null
        return (((regularCents - priceCents) * 100.0) / regularCents).roundToLong().toInt()
    }
}

object Quantity {
    /** 2.0 → "2", 1.5 → "1,5", 0.333 → "0,333" */
    fun format(quantity: Double): String {
        if (quantity == floor(quantity) && abs(quantity) < 1e12) return quantity.toLong().toString()
        return BigDecimal.valueOf(quantity).setScale(3, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString().replace('.', ',')
    }

    fun parse(input: String): Double? =
        input.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
}
