package com.thiago.listamercado.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun formatsBrazilianCurrency() {
        assertEquals("R$ 0,05", Money.format(5))
        assertEquals("R$ 12,99", Money.format(1299))
        assertEquals("R$ 1.299,90", Money.format(129990))
        assertEquals("R$ 1.000.000,00", Money.format(100000000))
        assertEquals("-R$ 3,50", Money.format(-350))
        assertEquals("12,99", Money.formatInput(1299))
    }

    @Test
    fun parsesUserInput() {
        assertEquals(1299L, Money.parse("12,99"))
        assertEquals(1299L, Money.parse("12.99"))
        assertEquals(1299L, Money.parse("R$ 12,99"))
        assertEquals(123456L, Money.parse("1.234,56"))
        assertEquals(123400L, Money.parse("1.234"))
        assertEquals(700L, Money.parse("7"))
        assertEquals(750L, Money.parse("7,5"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-3"))
    }

    @Test
    fun quantities() {
        assertEquals("2", Quantity.format(2.0))
        assertEquals("1,5", Quantity.format(1.5))
        assertEquals(1.5, Quantity.parse("1,5")!!, 0.0001)
        assertNull(Quantity.parse("0"))
        assertEquals(1050L, Money.lineTotal(1.5, 700))
        assertEquals(20, Money.discountPercent(1000, 800))
        assertNull(Money.discountPercent(null, 800))
    }
}
