@file:Suppress("unused")

package com.vojtkovszky.billinghelper

import android.util.Log
import com.android.billingclient.api.ProductDetails

internal object PriceUtil {
    var enableLogging: Boolean = false
    const val TAG = "BillingHelper/PriceUtil"

    /**
     * Holds a formatted price string alongside its precise micro-unit value.
     */
    data class PriceInfo(
        val formattedPrice: String,
        val priceAmountMicros: Long
    )

    /**
     * Given an original formatted price and the divided micro-unit amount,
     * produces a new formatted price string that preserves the original locale
     * formatting (currency symbol position, decimal separator, grouping separator, spacing).
     *
     * Supports formats like:
     * - "$16.80", "€16,80", "16,80 €", "R$ 1.680,80", "¥1,680", "1 680,80 ₽",
     *   "CHF 16.80", "kr 16,80", "16.80 zł", "₹1,68,000.80" (Indian grouping)
     */
    fun reformatPrice(formattedPrice: String, originalMicros: Long, dividedMicros: Long): String? {
        // Match: optional currency prefix, numeric part (digits + separators), optional currency suffix
        // Supports dot, comma, space, narrow no-break space, and apostrophe as separators
        val regex = """^(\D*?[\s\u00A0\u202F]?)([\d][\d.,'\s\u00A0\u202F]*[\d]|[\d])(\s*\D*)$""".toRegex()
        val match = regex.matchEntire(formattedPrice.trim()) ?: return null

        val prefix = match.groupValues[1]
        val numericPart = match.groupValues[2]
        val suffix = match.groupValues[3]

        // Detect decimal separator and decimal places from the original numeric string.
        // The last occurrence of '.' or ',' is the decimal separator IF followed by
        // exactly 1-2 digits (typical for currency). Otherwise there are no decimals
        // (e.g. JPY "¥1,680").
        val lastDotIdx = numericPart.lastIndexOf('.')
        val lastCommaIdx = numericPart.lastIndexOf(',')
        val lastSepIdx = maxOf(lastDotIdx, lastCommaIdx)

        val decimalSeparator: Char?
        val decimalPlaces: Int
        val groupingSeparator: Char?

        if (lastSepIdx >= 0) {
            val afterSep = numericPart.substring(lastSepIdx + 1)
            if (afterSep.length in 1..2 && afterSep.all { it.isDigit() }) {
                // Last separator is the decimal separator
                decimalSeparator = numericPart[lastSepIdx]
                decimalPlaces = afterSep.length
                // Grouping separator is any other separator found in the integer part
                val integerPart = numericPart.substring(0, lastSepIdx)
                groupingSeparator = integerPart.firstOrNull { !it.isDigit() }
            } else {
                // No decimal portion (zero-decimal currency like JPY, KRW)
                decimalSeparator = null
                decimalPlaces = 0
                groupingSeparator = numericPart.firstOrNull { !it.isDigit() }
            }
        } else {
            // Single number with no separators at all
            decimalSeparator = null
            decimalPlaces = 0
            groupingSeparator = null
        }

        // Compute whole and fractional parts from divided micros
        val absMicros = kotlin.math.abs(dividedMicros)
        val wholePart = absMicros / 1_000_000L
        val remainderMicros = absMicros % 1_000_000L

        // Format the integer part with grouping if the original had it
        // Only pass the decimal separator index when there actually is a decimal portion;
        // for zero-decimal currencies (JPY, KRW) lastSepIdx points to a grouping separator.
        val wholeStr = if (groupingSeparator != null) {
            val decSepIdx = if (decimalPlaces > 0) lastSepIdx else -1
            formatWithGrouping(wholePart, groupingSeparator, numericPart, decSepIdx, decimalSeparator)
        } else {
            wholePart.toString()
        }

        // Format fractional part
        val numericResult = if (decimalPlaces > 0 && decimalSeparator != null) {
            val scale = when (decimalPlaces) {
                1 -> 10L
                else -> 100L
            }
            val fraction = ((remainderMicros * scale + 500_000L) / 1_000_000L)
                .coerceAtMost(scale - 1)
            val fracStr = fraction.toString().padStart(decimalPlaces, '0')
            "$wholeStr$decimalSeparator$fracStr"
        } else {
            wholeStr
        }

        val sign = if (dividedMicros < 0) "-" else ""
        return "$sign$prefix$numericResult$suffix"
    }

    /**
     * Formats [value] with the given [groupingSeparator], preserving the grouping pattern
     * found in the original numeric string (handles standard 3-digit grouping as well as
     * Indian-style grouping like ₹1,68,000).
     */
    private fun formatWithGrouping(
        value: Long,
        groupingSeparator: Char,
        originalNumeric: String,
        decimalSepIdx: Int,
        decimalSeparator: Char?
    ): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits

        // Extract the grouping pattern from the original integer part
        val intPart = if (decimalSepIdx >= 0) originalNumeric.substring(0, decimalSepIdx) else originalNumeric
        val groups = intPart.split(groupingSeparator).filter { it.isNotEmpty() }

        if (groups.size <= 1) return digits

        // Determine group sizes from right to left (last group is the rightmost)
        // e.g. "1,68,000" -> groups ["1","68","000"] -> sizes from right: [3, 2, ...]
        val rightmostGroupSize = groups.last().length
        val secondGroupSize = if (groups.size >= 2) groups[groups.size - 2].length else rightmostGroupSize

        // Build result from right to left
        val result = StringBuilder()
        var remaining = digits
        var isFirst = true
        var groupSize = rightmostGroupSize

        while (remaining.isNotEmpty()) {
            if (!isFirst) {
                val take = minOf(groupSize, remaining.length)
                val group = remaining.takeLast(take)
                remaining = remaining.dropLast(take)
                result.insert(0, group)
                if (remaining.isNotEmpty()) {
                    result.insert(0, groupingSeparator)
                }
                // After the first separator, use secondGroupSize for remaining groups
                groupSize = secondGroupSize
            } else {
                val take = minOf(rightmostGroupSize, remaining.length)
                val group = remaining.takeLast(take)
                remaining = remaining.dropLast(take)
                result.insert(0, group)
                if (remaining.isNotEmpty()) {
                    result.insert(0, groupingSeparator)
                }
                isFirst = false
                groupSize = secondGroupSize
            }
        }

        return result.toString()
    }
}

/**
 * A helper method making it easier to retrieve a formatted price from product details.
 * Parameters only apply to subscription products.
 *
 * @param subscriptionBasePlanId applies to a subscription, define base plan id to get price for. If no value is applied, first offer will be used. Can be combined with [subscriptionOfferId]
 * @param subscriptionOfferId applies to a subscription, define offer id to get price for. If no value is applied, first offer will be used. Can be combined with [subscriptionBasePlanId]
 * @param subscriptionPricingPhaseIndex in case your offer has multiple phases, retrieve price for a phase with a given index.
 * @return formatted price or null on error
 */
fun ProductDetails.getFormattedPrice(
    subscriptionBasePlanId: String? = null,
    subscriptionOfferId: String? = null,
    subscriptionPricingPhaseIndex: Int = 0
): String? {
    return if (isInAppPurchase()) {
        oneTimePurchaseOfferDetails?.formattedPrice
    } else try {
        if (isSubscription()) {
            val offer = subscriptionOfferDetails?.firstOrNull { offer ->
                (subscriptionBasePlanId == null || offer.basePlanId == subscriptionBasePlanId) &&
                        (subscriptionOfferId == null || offer.offerId == subscriptionOfferId)
            }
            return offer?.pricingPhases?.pricingPhaseList?.getOrNull(subscriptionPricingPhaseIndex)?.formattedPrice
        } else {
            null
        }
    } catch (e: Exception) {
        if (PriceUtil.enableLogging) Log.e(PriceUtil.TAG, e.message ?: "")
        null
    }
}

/**
 * Same as [getFormattedPrice], but apply divider to the actual price.
 * Uses [priceAmountMicros] from the billing API for precise arithmetic, then
 * reconstructs the formatted price preserving the original locale formatting
 * (currency position, decimal separator, grouping separator, spacing).
 *
 * For example: "16,80 €" (micros=16800000) with a divider of 4 will return "4,20 €".
 *
 * Handles all locale formats including:
 * - Dot-decimal: "$16.80", "£1,680.80"
 * - Comma-decimal: "16,80 €", "R$ 1.680,80"
 * - Space grouping: "1 680,80 ₽"
 * - Zero-decimal currencies: "¥1,680"
 * - Indian grouping: "₹1,68,000.80"
 * - Apostrophe grouping: "CHF 1'680.80"
 *
 * @param subscriptionBasePlanId see [getFormattedPrice]
 * @param subscriptionOfferId see [getFormattedPrice]
 * @param subscriptionPricingPhaseIndex see [getFormattedPrice]
 * @param divider price divider
 */
fun ProductDetails.getFormattedPriceDivided(
    subscriptionBasePlanId: String? = null,
    subscriptionOfferId: String? = null,
    subscriptionPricingPhaseIndex: Int = 0,
    divider: Int
): String? {
    return try {
        val priceInfo = if (isInAppPurchase()) {
            oneTimePurchaseOfferDetails?.let {
                PriceUtil.PriceInfo(it.formattedPrice, it.priceAmountMicros)
            }
        } else if (isSubscription()) {
            val offer = subscriptionOfferDetails?.firstOrNull { offer ->
                (subscriptionBasePlanId == null || offer.basePlanId == subscriptionBasePlanId) &&
                        (subscriptionOfferId == null || offer.offerId == subscriptionOfferId)
            }
            offer?.pricingPhases?.pricingPhaseList?.getOrNull(subscriptionPricingPhaseIndex)?.let {
                PriceUtil.PriceInfo(it.formattedPrice, it.priceAmountMicros)
            }
        } else {
            null
        }

        priceInfo?.let { info ->
            val dividedMicros = info.priceAmountMicros / divider
            PriceUtil.reformatPrice(info.formattedPrice, info.priceAmountMicros, dividedMicros)
        }
    } catch (e: Exception) {
        if (PriceUtil.enableLogging) Log.e(PriceUtil.TAG, e.message ?: "")
        null
    }
}