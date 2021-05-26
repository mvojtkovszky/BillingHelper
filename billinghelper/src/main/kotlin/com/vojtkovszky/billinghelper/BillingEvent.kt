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
     * Is event a success by nature.
     */
    val isSuccess: Boolean
        get() = listOf(
            BILLING_CONNECTED, QUERY_SKU_DETAILS_COMPLETE, QUERY_OWNED_PURCHASES_COMPLETE,
            PURCHASE_COMPLETE, PURCHASE_ACKNOWLEDGE_SUCCESS, CONSUME_PURCHASE_SUCCESS,
            PRICE_CHANGE_CONFIRMATION_SUCCESS
        ).contains(this)

    /**
     * Is event a failure in an actively initialized flow, usually accompanied by a dialog. One of:
     * [PURCHASE_FAILED], [PRICE_CHANGE_CONFIRMATION_FAILED]
     */
    val isActiveActionFailure: Boolean
        get() = listOf(
            PURCHASE_FAILED, PRICE_CHANGE_CONFIRMATION_FAILED
        ).contains(this)

    /**
     * Is event a success in an actively initialized flow, usually accompanied by a dialog. One of:
     * [PURCHASE_COMPLETE], [PRICE_CHANGE_CONFIRMATION_SUCCESS]
     */
    val isActiveActionSuccess: Boolean
        get() = listOf(
            PURCHASE_COMPLETE, PRICE_CHANGE_CONFIRMATION_SUCCESS
        ).contains(this)

    /**
     * Indicating information about owned purchases changed.
     * Happens by either [PURCHASE_COMPLETE] or [QUERY_OWNED_PURCHASES_COMPLETE]
     */
    val isOwnedPurchasesChange: Boolean
        get() = listOf(
            PURCHASE_COMPLETE, QUERY_OWNED_PURCHASES_COMPLETE
        ).contains(this)
    // endregion helpers

    // region flow helpers
    /**
     * Determine if event is part of Billing connection changes events.
     */
    val isBillingConnectionFlow: Boolean
        get() = listOf(
            BILLING_CONNECTED, BILLING_CONNECTION_FAILED, BILLING_DISCONNECTED
        ).contains(this)

    /**
     * Determine if event belongs to query sku details flow.
     */
    val isQuerySkuDetailsFlow: Boolean
        get() = listOf(
            QUERY_SKU_DETAILS_COMPLETE, QUERY_SKU_DETAILS_FAILED
        ).contains(this)

    /**
     * Determine if event belongs to query owned purchases flow.
     */
    val isQueryOwnedPurchasesFlow: Boolean
        get() = listOf(
            QUERY_OWNED_PURCHASES_COMPLETE, QUERY_OWNED_PURCHASES_FAILED
        ).contains(this)

    /**
     * Determine if event belongs to consume purchase flow.
     */
    val isConsumePurchaseFlow: Boolean
        get() = listOf(
            CONSUME_PURCHASE_SUCCESS, CONSUME_PURCHASE_FAILED
        ).contains(this)

    /**
     * Determine if event belongs to a purchase flow.
     */
    val isPurchaseFlow: Boolean
        get() = listOf(
            PURCHASE_CANCELLED, PURCHASE_COMPLETE, PURCHASE_FAILED
        ).contains(this)

    /**
     * Determine if event belongs to purchase acknowledgement flow.
     */
    val isPurchaseAcknowledgeFlow: Boolean
        get() = listOf(
            PURCHASE_ACKNOWLEDGE_SUCCESS, PURCHASE_ACKNOWLEDGE_FAILED
        ).contains(this)

    /**
     * Determine if event belongs to price change confirmation flow.
     */
    val isPriceChangeConfirmationFlow: Boolean
        get() = listOf(
            PRICE_CHANGE_CONFIRMATION_FAILED, PRICE_CHANGE_CONFIRMATION_CANCELLED,
            PRICE_CHANGE_CONFIRMATION_SUCCESS
        ).contains(this)
    // endregion flow helpers
}