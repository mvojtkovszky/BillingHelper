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

/*
 * Owned purchases are identified by purchase token: it stays the same while a purchase moves from
 * pending to purchased, while orderId is missing for pending purchases.
 */

// all purchases containing the given product
internal fun List<Purchase>.withProduct(productName: String): List<Purchase> =
    filter { it.products.contains(productName) }

// this list with the given purchase added at the end, replacing an older copy of the same purchase
internal fun List<Purchase>.withPurchase(purchase: Purchase): List<Purchase> =
    filter { it.purchaseToken != purchase.purchaseToken } + purchase

// this list without the purchase with the given token
internal fun List<Purchase>.withoutPurchase(purchaseToken: String): List<Purchase> =
    filter { it.purchaseToken != purchaseToken }
