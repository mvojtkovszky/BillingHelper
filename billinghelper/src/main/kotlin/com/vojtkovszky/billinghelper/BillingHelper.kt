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
 * @param skuNames list of sku names supported by the app. Both in app purchases and subscriptions.
 * @param startConnectionImmediately set whether [initClientConnection] should be called automatically
 * right after client is constructed.
 * @param querySkuDetailsOnConnected set whether [initQuerySkuDetails] should be called automatically
 * right after client connects.
 * Note this parameter will be ignored if startConnectionImmediately is set to false as we cannot
 * query for sku details without billing client being connected.
 * @param queryOwnedPurchasesOnConnected set whether [initQueryOwnedPurchases] should be called automatically
 * right after client connects.
 * Note this parameter will be ignored if startConnectionImmediately is set to false as we cannot
 * query for owned purchases without billing client being connected.
 * @param autoAcknowledgePurchases All purchases require acknowledgement. By default, this is handled
 * automatically every time state of purchases changes. It set to [Boolean.false], make sure to
 * manually call [acknowledgePurchases].
 * @param billingListener default listener that'll be added as [addBillingListener].
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class BillingHelper(
    context: Context,
    private val skuNames: List<String>,
    startConnectionImmediately: Boolean = true,
    querySkuDetailsOnConnected: Boolean = true,
    queryOwnedPurchasesOnConnected: Boolean = true,
    var autoAcknowledgePurchases: Boolean = true,
    billingListener: BillingListener? = null
) {
    companion object {
        private val ALL_PURCHASE_TYPES = arrayOf(BillingClient.SkuType.INAPP, BillingClient.SkuType.SUBS)
    }

    // billing client
    private var billingClient: BillingClient
    // represents list of all currently owned purchases
    private val purchases = mutableListOf<Purchase>()
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
     * Retrieve [BillingClient.ConnectionState] from billingClient
     */
    val connectionState: Int
        get() = billingClient.connectionState

    /**
     * Determine if owned purchases have been successfully queried yet.
     * That happens with successful completion of [initQueryOwnedPurchases].
     */
    var purchasesQueried = false
        private set

    /**
     * Determine if sku details have been successfully queried yet.
     * That happens with successful completion of [initQuerySkuDetails].
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
                        updatePurchases(purchases)
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
                event = when {
                    billingResult.isResponseOk() -> BillingEvent.CONSUME_PURCHASE_SUCCESS
                    else -> BillingEvent.CONSUME_PURCHASE_FAILED
                },
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
     * Will return a single [Purchase] object that contains a given [skuName] or null
     * if no match found.
     * Note that you need to query for owned purchases using [initQuerySkuDetails] or complete a
     * purchase before, in order for this to be not null.
     */
    fun getPurchaseWithSkuName(skuName: String): Purchase? {
        return purchases.find { it.skus.contains(skuName) }
    }

    /**
     * Will return a single [SkuDetails] object with a given [skuName] or null if no match found.
     * Note that you need to query for sku details first using [initQueryOwnedPurchases] in order
     * for this not to be null.
     */
    fun getSkuDetails(skuName: String): SkuDetails? {
        return skuDetailsList.find { it.sku == skuName }
    }

    /**
     * Determine whether product with given name has state set as purchased
     */
    fun isPurchased(skuName: String): Boolean {
        return getPurchaseWithSkuName(skuName)?.isPurchased() == true
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
                event = when {
                    billingResult.isResponseOk() -> BillingEvent.PRICE_CHANGE_CONFIRMATION_SUCCESS
                    billingResult.isResponseUserCancelled() -> BillingEvent.PRICE_CHANGE_CONFIRMATION_CANCELLED
                    else -> BillingEvent.PRICE_CHANGE_CONFIRMATION_FAILED
                },
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
                    event = when {
                        billingResult.isResponseOk() -> BillingEvent.BILLING_CONNECTED
                        else -> BillingEvent.BILLING_CONNECTION_FAILED
                    },
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
        // temp list to be assembled through queries
        val queryOwnedPurchases = mutableListOf<Purchase>()
        // count successful queries
        var successfulTypeQueries = 0

        // repeat for in-app purchases and subscriptions
        for (purchaseType in ALL_PURCHASE_TYPES) {
            billingClient.queryPurchasesAsync(purchaseType) { queryResult, purchases ->
                if (queryResult.isResponseOk()) {
                    successfulTypeQueries++ // successful query count increase
                    queryOwnedPurchases.addAll(purchases)
                } else {
                    invokeListener(
                        event = BillingEvent.QUERY_OWNED_PURCHASES_FAILED,
                        message = queryResult.debugMessage,
                        responseCode = queryResult.responseCode
                    )
                }
                // all queries were completed successfully, safe to update the list and trigger listener
                if (successfulTypeQueries == ALL_PURCHASE_TYPES.size) {
                    updatePurchases(purchases)
                    this.purchasesQueried = true
                    invokeListener(BillingEvent.QUERY_OWNED_PURCHASES_COMPLETE)
                }
            }
        }
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
                    invokeListener(
                        event = BillingEvent.QUERY_SKU_DETAILS_FAILED,
                        message = queryResult.debugMessage,
                        responseCode = queryResult.responseCode
                    )
                }
                // all queries were completed successfully, safe to update the list and trigger listener
                if (successfulTypeQueries == ALL_PURCHASE_TYPES.size) {
                    this.skuDetailsList.clear()
                    this.skuDetailsList.addAll(querySkuDetailsList)
                    this.skuDetailsQueried = true
                    invokeListener(BillingEvent.QUERY_SKU_DETAILS_COMPLETE)
                }
            }
        }
    }

    /**
     * Conveniently determine if feature is supported by calling [BillingClient.isFeatureSupported]
     *
     * @param feature one of [BillingClient.FeatureType]
     * @return [Boolean.true] if response is [BillingClient.BillingResponseCode.OK], [Boolean.false]
     * if response was [BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED]
     */
    fun isFeatureSupported(feature: String): Boolean {
        return billingClient.isFeatureSupported(feature).isResponseOk()
    }

    /**
     * All purchases require acknowledgement. Failure to acknowledge a purchase will result in that
     * purchase being refunded.
     * By default, this is handled automatically every time state of purchases changes.
     * See [autoAcknowledgePurchases] if you want to change that behaviour.
     */
    fun acknowledgePurchases(purchases: List<Purchase>) {
        if (!billingClient.isReady) {
            invokeListener(
                event = BillingEvent.PURCHASE_ACKNOWLEDGE_FAILED,
                message = "Billing not ready")
            return
        }

        for (purchase in purchases) {
            if (!purchase.isAcknowledged && purchase.isPurchased()) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    invokeListener(
                        event = when {
                            billingResult.isResponseOk() -> BillingEvent.PURCHASE_ACKNOWLEDGE_SUCCESS
                            else -> BillingEvent.PURCHASE_ACKNOWLEDGE_FAILED
                        },
                        message = billingResult.debugMessage,
                        responseCode = billingResult.responseCode)
                }
            }
        }
    }

    // purchases list repopulate and handle logic of acknowledge check and init
    private fun updatePurchases(purchases: List<Purchase>) {
        // update our list
        this.purchases.clear()
        this.purchases.addAll(purchases)
        // handle acknowledgement
        if (autoAcknowledgePurchases) {
            acknowledgePurchases(purchases)
        }
    }

    // region billing listener
    /**
     * Add a listener to [billingListeners] if not already present
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
                billingListeners.forEach {
                    it.onBillingEvent(event, message, responseCode)
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