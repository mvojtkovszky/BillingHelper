package com.vojtkovszky.billinghelper

/**
 * Additional parameters often required for subscription purchases.
 *
 * @param basePlanId define base plan id to initiate a purchase with. If no value is provided, first offer will be used. Can be combined with [offerId]
 * @param offerId define offer id to initiate a purchase with. If no value is provided, first offer will be used. Can be combined with [basePlanId]
 * @param updateOldToken Google Play Billing purchase token that the user is upgrading or downgrading from.
 *        See [https://developer.android.com/reference/com/android/billingclient/api/BillingFlowParams.SubscriptionUpdateParams].
 *        Note that [productName] must also be a subscription for this to take effect.
 * @param updateExternalTransactionId If the originating transaction for the subscription
 *        that the user is upgrading or downgrading from was processed via alternative billing.
 *        See [https://developer.android.com/reference/com/android/billingclient/api/BillingFlowParams.SubscriptionUpdateParams.Builder#setOriginalExternalTransactionId(java.lang.String)].
 * @param updateReplacementMode Supported replacement modes to replace an existing subscription with a new one.
 *        See [https://developer.android.com/reference/com/android/billingclient/api/BillingFlowParams.SubscriptionUpdateParams.ReplacementMode].
 */
data class SubscriptionPurchaseParams(
    val basePlanId: String? = null,
    val offerId: String? = null,
    val updateOldToken: String? = null,
    val updateExternalTransactionId: String? = null,
    val updateReplacementMode: Int? = null
)
