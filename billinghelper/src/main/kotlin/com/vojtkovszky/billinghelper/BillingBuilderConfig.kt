package com.vojtkovszky.billinghelper

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.UserChoiceBillingListener

/**
 * Configuration used when building [BillingClient].
 *
 * @param enableAlternativeBillingOnly build client with [BillingClient.Builder.enableAlternativeBillingOnly]
 * For more info see [https://developer.android.com/reference/com/android/billingclient/api/BillingClient.Builder#enableAlternativeBillingOnly()]
 * @param enableExternalOffer build client with [BillingClient.Builder.enableExternalOffer]
 * For more info see [https://developer.android.com/reference/com/android/billingclient/api/BillingClient.Builder#enableExternalOffer()]
 * @param enablePendingPurchasesOneTimeProducts build client [BillingClient.Builder.enablePendingPurchases] with pending purchase for one-time products enabled
 * For more info see [https://developer.android.com/reference/com/android/billingclient/api/PendingPurchasesParams.Builder#enableOneTimeProducts()]
 * @param enablePendingPurchasesPrepaidPlans build client [BillingClient.Builder.enablePendingPurchases] with pending purchase for prepaid plans enabled
 * For more info see [https://developer.android.com/reference/com/android/billingclient/api/PendingPurchasesParams.Builder#enablePrepaidPlans()]
 * @param enableAutoServiceReconnection build client with [BillingClient.Builder.enableAutoServiceReconnection]
 * For more info see [https://developer.android.com/google/play/billing/integrate#automatic-service-reconnection]
 * @param userChoiceBillingListener build client with [BillingClient.Builder.enableUserChoiceBilling] and provide given listener.
 * For more info see [https://developer.android.com/reference/com/android/billingclient/api/UserChoiceBillingListener]
 * and [https://support.google.com/googleplay/android-developer/answer/13821247]
 */
class BillingBuilderConfig(
    val enableAlternativeBillingOnly: Boolean = false,
    val enableExternalOffer: Boolean = false,
    val enablePendingPurchasesOneTimeProducts: Boolean = true,
    val enablePendingPurchasesPrepaidPlans: Boolean = true,
    val enableAutoServiceReconnection: Boolean = true,
    val userChoiceBillingListener: UserChoiceBillingListener? = null,
)
