@file:Suppress("unused")

package com.vojtkovszky.billinghelper

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails

/**
 * Determine if purchaseState is purchased ([Purchase.PurchaseState.PURCHASED])
 */
fun Purchase.isPurchased(): Boolean =
    purchaseState == Purchase.PurchaseState.PURCHASED

/**
 * Filters out in app purchases ([SkuDetails] with type [BillingClient.SkuType.INAPP])
 */
fun List<SkuDetails>.getInAppPurchases(): List<SkuDetails> =
    this.filter { it.type == BillingClient.SkuType.INAPP }

/**
 * Filters out subscriptions ([SkuDetails] with type [BillingClient.SkuType.SUBS])
 */
fun List<SkuDetails>.getSubscriptions(): List<SkuDetails> =
    this.filter { it.type == BillingClient.SkuType.SUBS }

/**
 * Determine if type is in app purchase ([BillingClient.SkuType.INAPP])
 */
fun SkuDetails.isInAppPurchase(): Boolean =
    type == BillingClient.SkuType.INAPP

/**
 * Determine if type is a subscription ([BillingClient.SkuType.SUBS])
 */
fun SkuDetails.isSubscription(): Boolean =
    type == BillingClient.SkuType.SUBS