package com.vojtkovszky.billinghelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceUtilTest {

    // --- Dot-decimal, currency prefix ---

    @Test
    fun `USD dot-decimal prefix - simple divide`() {
        // "$16.80" / 4 = "$4.20"
        val result = PriceUtil.reformatPrice("$16.80", 16_800_000L, 4_200_000L)
        assertEquals("$4.20", result)
    }

    @Test
    fun `USD dot-decimal prefix - large amount with grouping`() {
        // "$1,680.80" / 2 = "$840.40"
        val result = PriceUtil.reformatPrice("$1,680.80", 1_680_800_000L, 840_400_000L)
        assertEquals("$840.40", result)
    }

    @Test
    fun `GBP dot-decimal prefix`() {
        // "£9.99" / 3 = "£3.33"
        val result = PriceUtil.reformatPrice("£9.99", 9_990_000L, 3_330_000L)
        assertEquals("£3.33", result)
    }

    // --- Comma-decimal, currency suffix ---

    @Test
    fun `EUR comma-decimal suffix`() {
        // "16,80 €" / 4 = "4,20 €"
        val result = PriceUtil.reformatPrice("16,80 \u20AC", 16_800_000L, 4_200_000L)
        assertEquals("4,20 \u20AC", result)
    }

    @Test
    fun `EUR comma-decimal prefix`() {
        // "€16,80" / 4 = "€4,20"
        val result = PriceUtil.reformatPrice("\u20AC16,80", 16_800_000L, 4_200_000L)
        assertEquals("\u20AC4,20", result)
    }

    @Test
    fun `PLN comma-decimal suffix`() {
        // "16,80 zł" / 2 = "8,40 zł"
        val result = PriceUtil.reformatPrice("16,80 z\u0142", 16_800_000L, 8_400_000L)
        assertEquals("8,40 z\u0142", result)
    }

    // --- Comma-decimal with dot grouping ---

    @Test
    fun `BRL dot-grouping comma-decimal`() {
        // "R$ 1.680,80" / 4 = "R$ 420,20"
        val result = PriceUtil.reformatPrice("R$ 1.680,80", 1_680_800_000L, 420_200_000L)
        assertEquals("R$ 420,20", result)
    }

    @Test
    fun `EUR German locale dot-grouping comma-decimal`() {
        // "1.680,80 €" / 2 = "840,40 €"
        val result = PriceUtil.reformatPrice("1.680,80 \u20AC", 1_680_800_000L, 840_400_000L)
        assertEquals("840,40 \u20AC", result)
    }

    @Test
    fun `BRL large amount dot-grouping comma-decimal`() {
        // "R$ 10.000,00" / 4 = "R$ 2.500,00"
        val result = PriceUtil.reformatPrice("R$ 10.000,00", 10_000_000_000L, 2_500_000_000L)
        assertEquals("R$ 2.500,00", result)
    }

    // --- Space grouping ---

    @Test
    fun `RUB space-grouping comma-decimal`() {
        // "1 680,80 ₽" / 4 = "420,20 ₽"
        val result = PriceUtil.reformatPrice("1 680,80 \u20BD", 1_680_800_000L, 420_200_000L)
        assertEquals("420,20 \u20BD", result)
    }

    @Test
    fun `RUB large space-grouping`() {
        // "16 800,00 ₽" / 4 = "4 200,00 ₽"
        val result = PriceUtil.reformatPrice("16 800,00 \u20BD", 16_800_000_000L, 4_200_000_000L)
        assertEquals("4 200,00 \u20BD", result)
    }

    // --- Zero-decimal currencies ---

    @Test
    fun `JPY zero-decimal with comma grouping`() {
        // "¥1,680" / 4 = "¥420"
        val result = PriceUtil.reformatPrice("\u00A51,680", 1_680_000_000L, 420_000_000L)
        assertEquals("\u00A5420", result)
    }

    @Test
    fun `JPY large zero-decimal`() {
        // "¥16,800" / 4 = "¥4,200"
        val result = PriceUtil.reformatPrice("\u00A516,800", 16_800_000_000L, 4_200_000_000L)
        assertEquals("\u00A54,200", result)
    }

    @Test
    fun `KRW zero-decimal`() {
        // "₩1,000" / 2 = "₩500"
        val result = PriceUtil.reformatPrice("\u20A91,000", 1_000_000_000L, 500_000_000L)
        assertEquals("\u20A9500", result)
    }

    // --- Indian grouping ---

    @Test
    fun `INR Indian grouping`() {
        // "₹1,68,000.80" / 4 = "₹42,000.20"
        val result = PriceUtil.reformatPrice("\u20B91,68,000.80", 168_000_800_000L, 42_000_200_000L)
        assertEquals("\u20B942,000.20", result)
    }

    @Test
    fun `INR Indian grouping large`() {
        // "₹16,80,000.00" = ₹16,80,000 = 1,680,000.00 / 4 = 420,000.00 = "₹4,20,000.00"
        val result = PriceUtil.reformatPrice("\u20B916,80,000.00", 1_680_000_000_000L, 420_000_000_000L)
        assertEquals("\u20B94,20,000.00", result)
    }

    // --- Apostrophe grouping (Swiss) ---

    @Test
    fun `CHF apostrophe grouping`() {
        // "CHF 1'680.80" / 4 = "CHF 420.20"
        val result = PriceUtil.reformatPrice("CHF 1'680.80", 1_680_800_000L, 420_200_000L)
        assertEquals("CHF 420.20", result)
    }

    @Test
    fun `CHF large apostrophe grouping`() {
        // "CHF 16'800.00" / 4 = "CHF 4'200.00"
        val result = PriceUtil.reformatPrice("CHF 16'800.00", 16_800_000_000L, 4_200_000_000L)
        assertEquals("CHF 4'200.00", result)
    }

    // --- Scandinavian ---

    @Test
    fun `SEK kr prefix comma-decimal`() {
        // "kr 16,80" / 4 = "kr 4,20"
        val result = PriceUtil.reformatPrice("kr 16,80", 16_800_000L, 4_200_000L)
        assertEquals("kr 4,20", result)
    }

    // --- Edge cases ---

    @Test
    fun `single digit price no separators`() {
        // "$5" / 1 = "$5"
        val result = PriceUtil.reformatPrice("$5", 5_000_000L, 5_000_000L)
        assertEquals("$5", result)
    }

    @Test
    fun `divide to very small amount rounds correctly`() {
        // "$0.99" with micros 990000, / 100 -> 9900 micros -> $0.01 (rounds up from $0.0099)
        val result = PriceUtil.reformatPrice("$0.99", 990_000L, 9_900L)
        assertEquals("$0.01", result)
    }

    @Test
    fun `divide to actual zero`() {
        // "$0.99" with micros 990000, / 1000000 -> 0 micros -> "$0.00"
        val result = PriceUtil.reformatPrice("$0.99", 990_000L, 0L)
        assertEquals("$0.00", result)
    }

    @Test
    fun `exact division no remainder`() {
        // "$10.00" / 2 = "$5.00"
        val result = PriceUtil.reformatPrice("$10.00", 10_000_000L, 5_000_000L)
        assertEquals("$5.00", result)
    }

    @Test
    fun `price with no currency symbol`() {
        // "16.80" / 4 = "4.20"
        val result = PriceUtil.reformatPrice("16.80", 16_800_000L, 4_200_000L)
        assertEquals("4.20", result)
    }

    @Test
    fun `rounding fractional micros`() {
        // "$9.99" / 3 = micros 3330000 -> "$3.33"
        val result = PriceUtil.reformatPrice("$9.99", 9_990_000L, 3_330_000L)
        assertEquals("$3.33", result)
    }

    @Test
    fun `rounding up fractional micros`() {
        // "$10.00" / 3 = micros 3333333 -> "$3.33"
        val result = PriceUtil.reformatPrice("$10.00", 10_000_000L, 3_333_333L)
        assertEquals("$3.33", result)
    }

    @Test
    fun `empty string returns null`() {
        val result = PriceUtil.reformatPrice("", 0L, 0L)
        assertNull(result)
    }

    @Test
    fun `non-price string returns null`() {
        val result = PriceUtil.reformatPrice("free", 0L, 0L)
        assertNull(result)
    }

    @Test
    fun `divided result needs grouping when original had it`() {
        // "$100.00" with original "£1,000.00" template / divide to get 100
        // Here original is "$1,000.00" and divided is small enough to not need grouping
        val result = PriceUtil.reformatPrice("$1,000.00", 1_000_000_000L, 250_000_000L)
        assertEquals("$250.00", result)
    }

    @Test
    fun `divided result grows grouping`() {
        // Original "$1,000.00", divider makes it stay above 1000
        val result = PriceUtil.reformatPrice("$8,000.00", 8_000_000_000L, 2_000_000_000L)
        assertEquals("$2,000.00", result)
    }

    // --- The original bug: comma as decimal was broken ---

    @Test
    fun `regression - comma decimal was previously parsed as thousands separator`() {
        // "16,80 €" was previously parsed as 1680.0 instead of 16.80
        // With micros-based approach, this is now correct
        val result = PriceUtil.reformatPrice("16,80 \u20AC", 16_800_000L, 4_200_000L)
        assertEquals("4,20 \u20AC", result)
    }

    @Test
    fun `regression - comma decimal small price`() {
        // "4,99 €" / 1 = "4,99 €"
        val result = PriceUtil.reformatPrice("4,99 \u20AC", 4_990_000L, 4_990_000L)
        assertEquals("4,99 \u20AC", result)
    }
}
