package com.vojtkovszky.billinghelper

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.*

/**
 * Construct helper. By default, connection will be initialized immediately with sku details and
 * owned purchases queried.
 * You can omit this convenience flow by tweaking constructor parameters and handle everything
 * manually.
 *
 * @param context required to build [BillingClient].
 * @param skuNames list of sku names supported by the app.
 * @param startConnectionImmediately set whether [initClientConnection] should be called right after
 * client is constructed.
 * @param querySkuDetailsOnConnected set whether [initQuerySkuDetails] should be called right after
 * client connects. Note this parameter will be ignored if startConnectionImmediately is set to false.
 * @param queryOwnedPurchasesOnConnected set whether [initQueryOwnedPurchases] should be called right
 * after client connects. Note this parameter will be ignored if startConnectionImmediately is set to false.
 * @param billingListener default listener that'll be added by calling [addBillingListener].
 */
@Suppress("unused")
class BillingHelper(
        context: Context,
        private val skuNames: List<String>,
        startConnectionImmediately: Boolean = true,
        querySkuDetailsOnConnected: Boolean = true,
        queryOwnedPurchasesOnConnected: Boolean = true,
        billingListener: BillingListener? = null
) {
    companion object {
        val ALL_PURCHASE_TYPES = arrayOf(BillingClient.SkuType.INAPP, BillingClient.SkuType.SUBS)
    }

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

    // keep track if we've actually queried purchases and sku details
    /**
     * Determine if billingClient is ready. Based on [BillingClient.isReady]
     */
    val billingReady: Boolean
        get() = billingClient.isReady

    /**
     * Determine if owned purchases have been successfully queried yet.
     */
    var purchasesQueried = false
        private set

    /**
     * Determine if sku details have been successfully queried yet.
     */
    var skuDetailsQueried = false
        private set

    init {
        // add default listener, if present
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

        // immediately connect client, if allowed so, and pass our preferences for pending queries
        // once client connects
        if (startConnectionImmediately) {
            initClientConnection(querySkuDetailsOnConnected, queryOwnedPurchasesOnConnected)
        }
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
    fun endClientConnection() {
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
     * Determine whether product with given name has state set as purchased
     */
    @SuppressWarnings("WeakerAccess")
    fun isPurchased(skuName: String): Boolean {
        return (getPurchaseForSkuName(skuName)?.isPurchased() == true)
    }

    /**
     * Determine if at least one product among given names has state set as purchased
     */
    fun isPurchasedAnyOf(vararg skuNames: String): Boolean {
        for (skuName in skuNames) {
            if (isPurchased(skuName)) return true
        }
        return false
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
     * Will initiate [BillingClient.startConnection]. If client is already connected, process
     * will be skipped and [BillingEvent.BILLING_CONNECTED] will be invoked via callback.
     *
     * @param querySkuDetailsOnConnected set whether [initQuerySkuDetails] should be called
     * right after client connects.
     * @param queryOwnedPurchasesOnConnected set whether [initQueryOwnedPurchases] should be
     * called right after client connects.
     */
    @SuppressWarnings("WeakerAccess")
    fun initClientConnection(querySkuDetailsOnConnected: Boolean = false,
                             queryOwnedPurchasesOnConnected: Boolean = false) {
        if (billingClient.isReady) {
            invokeListener(BillingEvent.BILLING_CONNECTED, "BillingClient already connected, skipping.")
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                // report billing ready
                invokeListener(
                    event = if (billingResult.isResponseOk())
                        BillingEvent.BILLING_CONNECTED else BillingEvent.BILLING_CONNECTION_FAILED,
                    message = billingResult.debugMessage,
                    responseCode = billingResult.responseCode)
                // initialize queries on start, if allowed to do so
                if (billingResult.isResponseOk()) {
                    // query for sku details
                    if (querySkuDetailsOnConnected) {
                        initQuerySkuDetails()
                    }
                    // query for owned purchases
                    if (queryOwnedPurchasesOnConnected) {
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
     * Initialize query for all currently owned items bought within your app.
     * Will query for both in-app purchases and subscriptions.
     * Result will be returned using [billingListeners] after [purchases] is updated.
     *
     * Note if queryForOwnedPurchasesOnInit=true in the helper constructor, method gets called
     * automatically when client connects (See [BillingHelper] constructor for more info).
     */
    fun initQueryOwnedPurchases() {
        val ownedPurchases = mutableListOf<Purchase>()
        for (purchaseType in ALL_PURCHASE_TYPES) {
            billingClient.queryPurchases(purchaseType).let {
                if (it.billingResult.isResponseOk()) {
                    purchasesQueried = true
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
     *
     * Note if queryForSkuDetailsOnInit=true in the helper constructor, method gets called
     * automatically when client connects (See [BillingHelper] constructor for more info).
     */
    fun initQuerySkuDetails() {
        // temp list to be assembled through queries
        val querySkuDetailsList = mutableListOf<SkuDetails>()
        // count successful queries
        var successfulTypeQueries = 0

        // repeat for in-app purchases and subscriptions
        for (purchaseType in ALL_PURCHASE_TYPES) {
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
                if (successfulTypeQueries == ALL_PURCHASE_TYPES.size) {
                    this.skuDetailsList.clear()
                    this.skuDetailsList.addAll(querySkuDetailsList)
                    this.skuDetailsQueried = true
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
    @SuppressWarnings("WeakerAccess")
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