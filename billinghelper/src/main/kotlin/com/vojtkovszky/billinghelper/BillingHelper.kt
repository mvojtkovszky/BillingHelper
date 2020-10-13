package com.vojtkovszky.billinghelper

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.*

@Suppress("unused")
class BillingHelper(
        context: Context,
        private val skuNames: List<String>,
        queryForSkuDetailsOnInit: Boolean = true,
        queryForOwnedPurchasesOnInit: Boolean = true,
        billingListener: BillingListener? = null
) {

    // billing client
    private var billingClient: BillingClient
    // represents list of all currently owned purchases
    private var purchases = emptyList<Purchase>()
        set(value) {
            acknowledgePurchases(value) // important to check if all owned purchases have been acknowledged
            field = value
        }
    // represents details of all available sku details
    private val skuDetailsList = mutableListOf<SkuDetails>()
    // callback listeners
    private val billingListeners = mutableListOf<BillingListener>()

    init {
        billingListener?.let { billingListeners.add(it) }

        // build client
        billingClient = BillingClient.newBuilder(context)
            .enablePendingPurchases()
            .setListener { billingResult, purchases -> // PurchasesUpdatedListener
                val billingEvent = when {
                    billingResult.isResponseOk() && purchases != null -> {
                        // update our list and handle acknowledgement first
                        this.purchases = purchases
                        BillingEvent.PURCHASE_COMPLETE
                    }
                    billingResult.isResponseUserCancelled() -> BillingEvent.PURCHASE_CANCELLED
                    else -> BillingEvent.PURCHASE_FAILED
                }
                // send callback complete
                invokeListener(billingEvent, billingResult.debugMessage, billingResult.responseCode)
            }.build()

        // immediately start connection and query for sku details purchases we own
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                // report billing ready
                invokeListener(
                    event = if (billingResult.isResponseOk())
                        BillingEvent.BILLING_CONNECTED else BillingEvent.BILLING_CONNECTION_FAILED,
                    message = billingResult.debugMessage,
                    responseCode = billingResult.responseCode)
                // initialize queries on start
                if (billingResult.isResponseOk()) {
                    // query for sku details
                    if (queryForSkuDetailsOnInit) {
                        initQuerySkuDetails()
                    }
                    // query for owned purchases
                    if (queryForOwnedPurchasesOnInit) {
                        initQueryOwnedPurchases()
                    }
                }
            }
            override fun onBillingServiceDisconnected() {
                // report disconnected
                invokeListener(BillingEvent.BILLING_DISCONNECTED)
            }
        })
    }

    /**
     * Consume a purchase.
     * Will init and handle a call to [BillingClient.consumeAsync]
     */
    fun consumePurchase(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            invokeListener(
                event = if (billingResult.isResponseOk())
                    BillingEvent.CONSUME_PURCHASE_SUCCESS else BillingEvent.CONSUME_PURCHASE_FAILED,
                message = billingResult.debugMessage,
                responseCode = billingResult.responseCode)
        }
    }

    /**
     * Important to call this when done with billing.
     * Will clear all the [billingListeners] call [BillingClient.endConnection]
     */
    fun endBillingClientConnection() {
        billingListeners.clear()
        billingClient.endConnection()
    }

    /**
     * will return a single [SkuDetails] object.
     * Note that you need to query for owned purchases first using [initQuerySkuDetails] or in
     * some cases complete a purchase in order for this to be not null
     */
    @SuppressWarnings("WeakerAccess")
    fun getPurchaseForSkuName(skuName: String): Purchase? {
        return purchases.find { purchase -> purchase.sku == skuName }
    }

    /**
     * will return a single [SkuDetails] object.
     * Note that you need to query for details first using [initQueryOwnedPurchases] in order to
     * get a result.
     */
    @SuppressWarnings("WeakerAccess")
    fun getSkuDetails(skuName: String): SkuDetails? {
        return skuDetailsList.find { skuDetail -> skuDetail.sku == skuName }
    }

    /**
     * Determine if billingClient is ready.
     * Based on [BillingClient.isReady]
     */
    fun isBillingReady(): Boolean {
        return billingClient.isReady
    }

    /**
     * Determine whether product with given name has state set as purchased
     */
    fun isPurchased(skuName: String): Boolean {
        return (getPurchaseForSkuName(skuName)?.isPurchased() == true)
    }

    /**
     * Will start a purchase flow for given sku name.
     * Result will get back to [PurchasesUpdatedListener]
     */
    fun launchPurchaseFlow(activity: Activity,
                           skuName: String,
                           obfuscatedAccountId: String? = null,
                           obfuscatedProfileId: String? = null,
                           setVrPurchaseFlow: Boolean = false
    ) {
        val skuDetailsToPurchase = getSkuDetails(skuName)
        if (billingClient.isReady && skuDetailsToPurchase != null) {
            val flowParams = BillingFlowParams.newBuilder().apply {
                setSkuDetails(skuDetailsToPurchase)
                obfuscatedAccountId?.let { setObfuscatedAccountId(it) }
                obfuscatedProfileId?.let { setObfuscatedProfileId(it) }
                setVrPurchaseFlow(setVrPurchaseFlow)
            }.build()
            // launch flow. Result will be passed to PurchasesUpdatedListener
            billingClient.launchBillingFlow(activity, flowParams)
        } else {
            // report purchase flow error
            invokeListener(
                event = BillingEvent.PURCHASE_FAILED,
                message = if (!billingClient.isReady) "Billing not ready" else "SKU details not available")
        }
    }

    /**
     * Will initiate a price change confirmation flow.
     */
    fun launchPriceChangeConfirmationFlow(activity: Activity, skuDetails: SkuDetails) {
        val priceChangeFlowParams = PriceChangeFlowParams.Builder()
                .setSkuDetails(skuDetails)
                .build()
        billingClient.launchPriceChangeConfirmationFlow(activity, priceChangeFlowParams) { billingResult ->
            invokeListener(
                event = if (billingResult.isResponseOk())
                    BillingEvent.PRICE_CHANGE_CONFIRMATION_SUCCESS else BillingEvent.PRICE_CHANGE_CONFIRMATION_CANCELLED,
                message = billingResult.debugMessage,
                responseCode = billingResult.responseCode)
        }
    }

    /**
     * Initialize query for all currently owned items bought within your app.
     * Will query for both in-app purchases and subscriptions.
     * Result will be returned using [billingListeners] after [purchases] is updated.
     */
    fun initQueryOwnedPurchases() {
        val ownedPurchases = mutableListOf<Purchase>()
        for (purchaseType in arrayOf(BillingClient.SkuType.INAPP, BillingClient.SkuType.SUBS)) {
                billingClient.queryPurchases(purchaseType).let {
                if (it.billingResult.isResponseOk()) {
                    it.purchasesList?.let { purchasesList -> ownedPurchases.addAll(purchasesList) }
                } else {
                    invokeListener(
                        event = BillingEvent.QUERY_OWNED_PURCHASES_FAILED,
                        message = it.billingResult.debugMessage,
                        responseCode = it.billingResult.responseCode)
                    return
                }
            }
        }
        // update list
        purchases = ownedPurchases
        invokeListener(BillingEvent.QUERY_OWNED_PURCHASES_COMPLETE)
    }

    /**
     * Initialize query for [SkuDetails] listed for this app.
     * Will query for both in-app purchases and subscriptions.
     * Result will be returned using [billingListeners]
     */
    fun initQuerySkuDetails() {
        // temp list to be assembled through queries
        val querySkuDetailsList = mutableListOf<SkuDetails>()
        // type of purchases to query
        val purchaseTypesToQuery = arrayOf(BillingClient.SkuType.INAPP, BillingClient.SkuType.SUBS)
        // count successful queries
        var successfulTypeQueries = 0

        // repeat for in-app purchases and subscriptions
        for (purchaseType in purchaseTypesToQuery) {
            val skuDetailsParams = SkuDetailsParams.newBuilder()
                    .setSkusList(skuNames)
                    .setType(purchaseType)
                    .build()
            billingClient.querySkuDetailsAsync(skuDetailsParams) { queryResult, skuDetailsList ->
                if (queryResult.isResponseOk()) {
                    successfulTypeQueries++ // successful query count increase
                    skuDetailsList?.let { querySkuDetailsList.addAll(it) }
                } else {
                    invokeListener(BillingEvent.QUERY_SKU_DETAILS_FAILED, queryResult.debugMessage, queryResult.responseCode)
                }
                // all queries were completed successfully, safe to update the list and trigger listener
                if (successfulTypeQueries == purchaseTypesToQuery.size) {
                    this.skuDetailsList.clear()
                    this.skuDetailsList.addAll(querySkuDetailsList)
                    invokeListener(BillingEvent.QUERY_SKU_DETAILS_COMPLETE, queryResult.debugMessage, queryResult.responseCode)
                }
            }
        }
    }

    /**
     * All purchases require acknowledgement. Failure to acknowledge a purchase will result in that
     * purchase being refunded.
     * That's why we're making sure to call this every time we change [BillingHelper.purchases]
     */
    private fun acknowledgePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (billingClient.isReady && !purchase.isAcknowledged && purchase.isPurchased()) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    invokeListener(event =
                            if (billingResult.isResponseOk()) BillingEvent.PURCHASE_ACKNOWLEDGED
                            else BillingEvent.PURCHASE_ACKNOWLEDGE_FAILED,
                        message = billingResult.debugMessage,
                        responseCode = billingResult.responseCode)
                }
            }
        }
    }

    // region billing listener
    /**
     * Add a listener to [billingListeners]
     */
    fun addBillingListener(listener: BillingListener) {
        if (!billingListeners.contains(listener)) billingListeners.add(listener)
    }

    /**
     * Remove a listener from [billingListeners]
     */
    fun removeBillingListener(listener: BillingListener) {
        billingListeners.remove(listener)
    }

    /**
     * Invoke a listener on UI thread
     */
    private fun invokeListener(event: BillingEvent, message: String? = null, responseCode: Int? = null) {
        Handler(Looper.getMainLooper()).post {
            try {
                for (billingListener in billingListeners) {
                    billingListener.onBillingEvent(event, message, responseCode)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    // endregion billing listener

    // region private extension functions
    private fun BillingResult.isResponseOk(): Boolean =
        responseCode == BillingClient.BillingResponseCode.OK

    private fun BillingResult.isResponseUserCancelled(): Boolean =
        responseCode == BillingClient.BillingResponseCode.USER_CANCELED
    // endregion private extension functions
}