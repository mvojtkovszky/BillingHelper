package com.vojtkovszky.billinghelper

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds real billing objects from the JSON the store returns, so tests run against the
 * library's own parsing instead of mocks.
 */
internal object TestData {

    // purchaseState in store JSON: 0 purchased, 4 pending
    fun purchase(
        token: String,
        products: List<String> = listOf("product"),
        orderId: String? = "GPA.$token",
        pending: Boolean = false,
        acknowledged: Boolean = false,
    ): Purchase {
        val json = JSONObject()
            .put("packageName", "com.example")
            .put("productIds", JSONArray(products))
            .put("purchaseTime", 1_700_000_000_000L)
            .put("purchaseState", if (pending) 4 else 0)
            .put("purchaseToken", token)
            .put("acknowledged", acknowledged)
        orderId?.let { json.put("orderId", it) }
        return Purchase(json.toString(), "signature")
    }

    data class Offer(val basePlanId: String, val offerId: String?, val token: String)

    fun subscription(productId: String, offers: List<Offer>): ProductDetails =
        productDetails(productId, BillingClient.ProductType.SUBS) { json ->
            json.put("subscriptionOfferDetails", JSONArray().apply {
                offers.forEach { offer ->
                    put(JSONObject()
                        .put("basePlanId", offer.basePlanId)
                        .apply { offer.offerId?.let { put("offerId", it) } }
                        .put("offerIdToken", offer.token)
                        .put("offerTags", JSONArray())
                        .put("pricingPhases", JSONArray().put(JSONObject()
                            .put("priceAmountMicros", 4_990_000L)
                            .put("priceCurrencyCode", "USD")
                            .put("formattedPrice", "$4.99")
                            .put("billingPeriod", "P1M")
                            .put("recurrenceMode", 1)
                            .put("billingCycleCount", 0))))
                }
            })
        }

    fun inAppProduct(productId: String): ProductDetails =
        productDetails(productId, BillingClient.ProductType.INAPP) { json ->
            json.put("oneTimePurchaseOfferDetails", JSONObject()
                .put("priceAmountMicros", 9_990_000L)
                .put("priceCurrencyCode", "USD")
                .put("formattedPrice", "$9.99"))
        }

    // ProductDetails has no public constructor; the JSON one is what the library itself uses
    private fun productDetails(
        productId: String,
        type: String,
        extra: (JSONObject) -> Unit
    ): ProductDetails {
        val json = JSONObject()
            .put("productId", productId)
            .put("type", type)
            .put("title", "$productId (App)")
            .put("name", productId)
            .put("description", "description")
            .put("skuDetailsToken", "skuDetailsToken")
            .also(extra)
        val constructor = ProductDetails::class.java.getDeclaredConstructor(String::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(json.toString())
    }
}
