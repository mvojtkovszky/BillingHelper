package com.vojtkovszky.billinghelper

import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails

/**
 * Additional parameters often required for subscription purchases.
 *
 * @param basePlanId define base plan id to initiate a purchase with. If no value is provided, first offer will be used. Can be combined with [offerId]
 * @param offerId define offer id to initiate a purchase with. If no value is provided, first offer will be used. Can be combined with [basePlanId]
 * @param updateOldToken Google Play Billing purchase token that the user is upgrading or downgrading from.
 *        See [https://developer.android.com/reference/com/android/billingclient/api/BillingFlowParams.SubscriptionUpdateParams].
 *        Note that the product must also be a subscription for this to take effect.
 * @param updateExternalTransactionId If the originating transaction for the subscription
 *        that the user is upgrading or downgrading from was processed via alternative billing.
 *        See [https://developer.android.com/reference/com/android/billingclient/api/BillingFlowParams.SubscriptionUpdateParams.Builder#setOriginalExternalTransactionId(java.lang.String)].
 * @param replacementOldProductId The old product id that needs to be replaced by the new product.
 *        Used with [replacementMode] to build [BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams] at the product details level.
 *        See [https://developer.android.com/reference/com/android/billingclient/api/SubscriptionProductReplacementParams].
 * @param replacementMode Supported replacement modes to replace an existing subscription with a new one.
 *        Used at the product details level via [BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams].
 *        See [https://developer.android.com/reference/com/android/billingclient/api/SubscriptionProductReplacementParams.ReplacementMode].
 */
data class SubscriptionPurchaseParams(
    val basePlanId: String? = null,
    val offerId: String? = null,
    val updateOldToken: String? = null,
    val updateExternalTransactionId: String? = null,
    val replacementOldProductId: String? = null,
    val replacementMode: Int? = null
) {
    // return offer token for given product details
    internal fun getOfferToken(productDetails: ProductDetails): String? {
        // set if found, or apply first found by default.
        return productDetails.subscriptionOfferDetails?.firstOrNull { offer ->
            (basePlanId == null || offer.basePlanId == basePlanId) &&
                    (offerId == null || offer.offerId == offerId)
        }?.offerToken
    }

    // return SubscriptionUpdateParams if we can build it
    internal fun getSubscriptionUpdateParams(): BillingFlowParams.SubscriptionUpdateParams? {
        if (updateOldToken != null || updateExternalTransactionId != null) {
            return BillingFlowParams.SubscriptionUpdateParams
                .newBuilder()
                .apply {
                    updateOldToken?.let { token ->
                        setOldPurchaseToken(token)
                    }
                    updateExternalTransactionId?.let { id ->
                        setOriginalExternalTransactionId(id)
                    }
                }
                .build()
        }
        return null
    }

    // return SubscriptionProductReplacementParams if we can build it
    internal fun getSubscriptionProductReplacementParams(): BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams? {
        if (replacementOldProductId != null || replacementMode != null) {
            return BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams
                .newBuilder()
                .apply {
                    replacementOldProductId?.let { setOldProductId(it) }
                    replacementMode?.let { setReplacementMode(it) }
                }
                .build()
        }
        return null
    }
}
