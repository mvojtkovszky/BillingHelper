package com.vojtkovszky.billinghelper

import com.android.billingclient.api.BillingClient

/**
 * Representing an event from [BillingHelper].
 * Whenever a supported change of any kind happens and someone is listening for it using
 * [BillingListener], an event will always be passed.
 */
@Suppress("unused")
enum class BillingEvent {
    // region event types
    /**
     * Success when calling [BillingClient.startConnection]
     */
    BILLING_CONNECTED,

    /**
     * Failure when calling [BillingClient.startConnection]
     */
    BILLING_CONNECTION_FAILED,

    /**
     * Called when [BillingClient] disconnected
     */
    BILLING_DISCONNECTED,

    /**
     * Success from [BillingClient.querySkuDetailsAsync]
     */
    QUERY_SKU_DETAILS_COMPLETE,

    /**
     * Failure from [BillingClient.querySkuDetailsAsync]
     */
    QUERY_SKU_DETAILS_FAILED,

    /**
     * Success from [BillingClient.queryPurchasesAsync]
     */
    QUERY_OWNED_PURCHASES_COMPLETE,

    /**
     * Failure from [BillingClient.queryPurchasesAsync]
     */
    QUERY_OWNED_PURCHASES_FAILED,

    /**
     * Success from [BillingClient.launchBillingFlow]
     */
    PURCHASE_COMPLETE,

    /**
     * Failure from [BillingClient.launchBillingFlow]
     */
    PURCHASE_FAILED,

    /**
     * User cancelled [BillingClient.launchBillingFlow]
     */
    PURCHASE_CANCELLED,

    /**
     * Success from [BillingClient.acknowledgePurchase]
     */
    PURCHASE_ACKNOWLEDGE_SUCCESS,

    /**
     * Failure from [BillingClient.acknowledgePurchase]
     */
    PURCHASE_ACKNOWLEDGE_FAILED,

    /**
     * Success from [BillingClient.consumeAsync]
     */
    CONSUME_PURCHASE_SUCCESS,

    /**
     * Failure from [BillingClient.consumeAsync]
     */
    CONSUME_PURCHASE_FAILED,

    /**
     * Success from [BillingClient.launchPriceChangeConfirmationFlow]
     */
    PRICE_CHANGE_CONFIRMATION_SUCCESS,

    /**
     * User cancelled [BillingClient.launchPriceChangeConfirmationFlow]
     */
    PRICE_CHANGE_CONFIRMATION_CANCELLED,

    /**
     * Failure from [BillingClient.launchPriceChangeConfirmationFlow]
     */
    PRICE_CHANGE_CONFIRMATION_FAILED;
    // endregion event types

    // region helpers
    /**
     * Is event a failure by nature.
     */
    val isFailure: Boolean
        get() = listOf(
            BILLING_CONNECTION_FAILED, QUERY_SKU_DETAILS_FAILED, QUERY_OWNED_PURCHASES_FAILED,
            PURCHASE_FAILED, CONSUME_PURCHASE_FAILED, PRICE_CHANGE_CONFIRMATION_FAILED
        ).contains(this)

    /**
     * Is event a failure due to an actively initialized flow. One of:
     * [QUERY_SKU_DETAILS_FAILED], [QUERY_SKU_DETAILS_FAILED], [CONSUME_PURCHASE_FAILED],
     * [PRICE_CHANGE_CONFIRMATION_FAILED]
     */
    val isActiveActionFailure: Boolean
        get() = listOf(
            QUERY_SKU_DETAILS_FAILED, PURCHASE_FAILED, CONSUME_PURCHASE_FAILED,
            PRICE_CHANGE_CONFIRMATION_FAILED
        ).contains(this)

    /**
     * Is event a success by nature.
     */
    val isSuccess: Boolean
        get() = listOf(
            BILLING_CONNECTED, QUERY_SKU_DETAILS_COMPLETE, QUERY_OWNED_PURCHASES_COMPLETE,
            PURCHASE_COMPLETE, PURCHASE_ACKNOWLEDGE_SUCCESS, CONSUME_PURCHASE_SUCCESS,
            PRICE_CHANGE_CONFIRMATION_SUCCESS
        ).contains(this)

    /**
     * Is event a success due to an actively initialized flow. One of:
     * [PURCHASE_COMPLETE], [PRICE_CHANGE_CONFIRMATION_SUCCESS]
     */
    val isActiveActionSuccess: Boolean
        get() = listOf(
            PURCHASE_COMPLETE, PRICE_CHANGE_CONFIRMATION_SUCCESS
        ).contains(this)

    /**
     * Indicating owned purchases information changed.
     * Happens by either [PURCHASE_COMPLETE] or [QUERY_OWNED_PURCHASES_COMPLETE]
     */
    val isOwnedPurchasesChange: Boolean
        get() = listOf(
            PURCHASE_COMPLETE, QUERY_OWNED_PURCHASES_COMPLETE
        ).contains(this)
    // endregion helpers
}