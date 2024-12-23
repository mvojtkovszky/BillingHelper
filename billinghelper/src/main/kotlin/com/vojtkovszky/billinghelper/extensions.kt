@file:Suppress("unused")

package com.vojtkovszky.billinghelper

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase

/**
 * Determine if purchaseState is purchased ([Purchase.PurchaseState.PURCHASED])
 */
fun Purchase.isPurchased(): Boolean =
    purchaseState == Purchase.PurchaseState.PURCHASED

/**
 * Filters out in app purchases ([BillingClient] with type [BillingClient.ProductType.INAPP])
 */
fun List<ProductDetails>.getInAppPurchases(): List<ProductDetails> =
    this.filter { it.productType == BillingClient.ProductType.INAPP }

/**
 * Filters out subscriptions ([BillingClient] with type [BillingClient.ProductType.SUBS])
 */
fun List<ProductDetails>.getSubscriptions(): List<ProductDetails> =
    this.filter { it.productType == BillingClient.ProductType.SUBS }

/**
 * Determine if type is in app purchase ([BillingClient.ProductType.INAPP])
 */
fun ProductDetails.isInAppPurchase(): Boolean =
    productType == BillingClient.ProductType.INAPP

/**
 * Determine if type is a subscription ([BillingClient.ProductType.SUBS])
 */
fun ProductDetails.isSubscription(): Boolean =
    productType == BillingClient.ProductType.SUBS