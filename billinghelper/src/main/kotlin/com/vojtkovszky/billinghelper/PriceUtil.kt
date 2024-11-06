@file:Suppress("unused")

package com.vojtkovszky.billinghelper

import android.annotation.SuppressLint
import android.util.Log
import com.android.billingclient.api.ProductDetails

object PriceUtil {
    internal const val TAG = "BillingHelper/PriceUtil"

    var enableLogging: Boolean = false

    /**
     * Convert a formatted price to a formatted price with a given divider.
     * For example: "16.80 EUR" with a divider of 4 will return "4.20 EUR"
     *
     * @param price formatted price input
     * @param divider divide the extracted price
     * @return formatted divided price or null on error
     */
    @SuppressLint("DefaultLocale")
    fun convertFullPriceToDivided(
        price: String,
        divider: Int
    ): String? {
        // Regex to match currency before or after the numeric part, with comma or dot as decimal separator
        val regex = """(\D*)\s*([\d,.]+)\s*(\D*)""".toRegex()
        val matchResult = regex.matchEntire(price)

        return if (matchResult != null) {
            val (currencyPrefix, numericPart, currencySuffix) = matchResult.destructured

            // Clean numeric part by replacing comma with dot if needed and converting to Double
            val normalizedNumber = numericPart.replace(",", ".").toDoubleOrNull()
            if (normalizedNumber != null) {
                // Divide and format the result with two decimal places
                val dividedPrice = normalizedNumber / divider
                val formattedPrice = String.format("%.2f", dividedPrice)

                // Construct the new price string with currency in the original position
                "${currencyPrefix.trim()}$formattedPrice${currencySuffix.trim()}"
            } else {
                null // Return null if numeric conversion fails
            }
        } else {
            null // Return null if the price format is not as expected
        }
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
            // Find the first matching offer based on the base plan ID and offer ID, or default to the first
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