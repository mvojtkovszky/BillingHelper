@file:Suppress("unused")

package com.vojtkovszky.billinghelper

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails

/**
 * Determine if purchaseState is purchased ([Purchase.PurchaseState.PURCHASED])
 */
fun Purchase.isPurchased(): Boolean =
    purchaseState == Purchase.PurchaseState.PURCHASED

/**
 * Filters out in app purchases ([SkuDetails] with type [BillingClient.ProductType.INAPP])
 */
fun List<ProductDetails>.getInAppPurchases(): List<ProductDetails> =
    this.filter { it.productType == BillingClient.ProductType.INAPP }

/**
 * Filters out subscriptions ([SkuDetails] with type [BillingClient.ProductType.SUBS])
 */
fun List<ProductDetails>.getSubscriptions(): List<ProductDetails> =
    this.filter { it.productType == BillingClient.ProductType.SUBS }

/**
 * Determine if type is in app purchase ([BillingClient.ProductType.INAPP])
 */
fun ProductDetails.isInAppPurchase(): Boolean =
    productType == BillingClient.ProductType.INAPP

/**
 * Determine if type is a subscription ([BillingClient.SkuType.SUBS])
 */
fun ProductDetails.isSubscription(): Boolean =
    productType == BillingClient.ProductType.SUBS