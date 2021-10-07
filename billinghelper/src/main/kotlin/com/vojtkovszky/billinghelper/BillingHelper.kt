package com.vojtkovszky.billinghelper

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.*

/**
 * Construct helper. By default, connection will be initialized immediately with sku details and
 * owned purchases queried.
 * This convenience flow can be omitted by tweaking constructor parameters.
 *
 * @param context required to build [BillingClient].
 * @param skuInAppPurchases list of sku names of in app purchases supported by the app.
 * @param skuSubscriptions list of sku names of subscriptions supported by the app.
 * @param startConnectionImmediately set whether [initClientConnection] should be called automatically
 * when [BillingHelper] is initialized.
 * @param key app's license key. If provided, it will be used to verify purchase signatures.
 * @param querySkuDetailsOnConnected set whether [initQuerySkuDetails] should be called automatically
 * right after client connects (when [initClientConnection] succeeds).
 * @param queryOwnedPurchasesOnConnected set whether [initQueryOwnedPurchases] should be called automatically
 * right after client connects (when [initClientConnection] succeeds).
 * @param autoAcknowledgePurchases All purchases require acknowledgement.
 * By default, this is handled automatically every time state of purchases changes.
 * If set to [Boolean.false], make sure [acknowledgePurchases] is used manually.
 * @param enableLogging toggle output of status logs
 * @param billingListener default listener that'll be added as [addBillingListener].
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class BillingHelper(
    context: Context,
    private val skuInAppPurchases: List<String>?,
    private val skuSubscriptions: List<String>?,
    private val startConnectionImmediately: Boolean = true,
    private var key: String? = null,
    var querySkuDetailsOnConnected: Boolean = true,
    var queryOwnedPurchasesOnConnected: Boolean = true,
    var autoAcknowledgePurchases: Boolean = true,
    var enableLogging: Boolean = false,
    billingListener: BillingListener? = null
) {
    companion object {
        private const val TAG = "BillingHelper"
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
        if (skuSubscriptions == null && skuInAppPurchases == null) {
            throw IllegalStateException(
                "Both skuSubscriptions and skuInAppPurchases missing. Define at least one of them."
            )
        }

        Security.enableLogging = this.enableLogging

        // add default listener, if present
        billingListener?.let { billingListeners.add(it) }

        // build client
        billingClient = BillingClient.newBuilder(context)
            .enablePendingPurchases()
            .setListener { billingResult, purchases -> // PurchasesUpdatedListener
                val billingEvent = when {
                    billingResult.isResponseOk() && purchases != null -> {
                        // update in current purchases
                        for (purchase in purchases) {
                            addOrUpdatePurchase(purchase)
                        }
                        // handle acknowledgement
                        if (autoAcknowledgePurchases) {
                            acknowledgePurchases(purchases)
                        }
                        // return complete
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
                responseCode = billingResult.responseCode
            )
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
        return try {
            purchases.find { it.skus.contains(skuName) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Will return a single [SkuDetails] object with a given [skuName] or null if no match found.
     * Note that you need to query for sku details first using [initQueryOwnedPurchases] in order
     * for this not to be null.
     */
    fun getSkuDetails(skuName: String): SkuDetails? {
        return try {
            skuDetailsList.find { it.sku == skuName }
        } catch (e: Exception) {
            null
        }
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
                message = if (!billingClient.isReady) "Billing not ready" else "SKU details not available"
            )
        }
    }

    /**
     * Will init and handle a call to [BillingClient.launchPriceChangeConfirmationFlow]
     */
    fun launchPriceChangeConfirmationFlow(activity: Activity, skuName: String) {
        val skuDetailsForChange = getSkuDetails(skuName)
        if (billingClient.isReady && skuDetailsForChange != null) {
            val priceChangeFlowParams = PriceChangeFlowParams.Builder()
                .setSkuDetails(skuDetailsForChange)
                .build()
            billingClient.launchPriceChangeConfirmationFlow(activity, priceChangeFlowParams) { billingResult ->
                invokeListener(
                    event = when {
                        billingResult.isResponseOk() -> BillingEvent.PRICE_CHANGE_CONFIRMATION_SUCCESS
                        billingResult.isResponseUserCancelled() -> BillingEvent.PRICE_CHANGE_CONFIRMATION_CANCELLED
                        else -> BillingEvent.PRICE_CHANGE_CONFIRMATION_FAILED
                    },
                    message = billingResult.debugMessage,
                    responseCode = billingResult.responseCode
                )
            }
        } else {
            // report error
            invokeListener(
                event = BillingEvent.PRICE_CHANGE_CONFIRMATION_FAILED,
                message = if (!billingClient.isReady) "Billing not ready" else "SKU details not available"
            )
        }
    }

    /**
     * Will initiate [BillingClient.startConnection]. If client is already connected, process
     * will be skipped and [BillingEvent.BILLING_CONNECTED] will be invoked.
     *
     * @param querySkuDetailsOnConnected set whether [initQuerySkuDetails] should be called
     * right after client connects. Defaults to [BillingHelper.querySkuDetailsOnConnected]
     * @param queryOwnedPurchasesOnConnected set whether [initQueryOwnedPurchases] should be called
     * right after client connects. Defaults to [BillingHelper.queryOwnedPurchasesOnConnected]
     */
    fun initClientConnection(querySkuDetailsOnConnected: Boolean = this.querySkuDetailsOnConnected,
                             queryOwnedPurchasesOnConnected: Boolean = this.queryOwnedPurchasesOnConnected) {
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
                    responseCode = billingResult.responseCode
                )
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
        initQueryOwnedPurchasesForType(getAvailableTypes(), 0, mutableListOf())
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
        initQuerySkuDetailsForType(getAvailableTypes(), 0, mutableListOf())
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
     * Will init and handle a call to [BillingClient.acknowledgePurchase]
     * By default, this is handled automatically every time state of purchases changes.
     * See [autoAcknowledgePurchases] if you want to change that behaviour.
     */
    fun acknowledgePurchases(purchases: List<Purchase>) {
        if (!billingClient.isReady) {
            invokeListener(
                event = BillingEvent.PURCHASE_ACKNOWLEDGE_FAILED,
                message = "Billing not ready"
            )
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
                        responseCode = billingResult.responseCode
                    )
                }
            }
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

    // Invoke a listener on UI thread
    private fun invokeListener(event: BillingEvent, message: String? = null, responseCode: Int? = null) {
        Handler(Looper.getMainLooper()).post {
            try {
                if (enableLogging) {
                    Log.d(TAG, "Listener invoked for " +
                            "event $event, message: $message, responseCode: $responseCode")
                }

                billingListeners.forEach {
                    it.onBillingEvent(event, message, responseCode)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    // endregion billing listener

    // region Private Methods
    // allows to call queryPurchasesAsync recursively on all types
    private fun initQueryOwnedPurchasesForType(types: List<String>,
                                               currentTypeIndex: Int,
                                               resultingList: MutableList<Purchase>) {
        // handled all types
        if (currentTypeIndex == types.size) {
            // mark as queried
            this.purchasesQueried = true
            // repopulate purchases
            for (purchase in resultingList) {
                addOrUpdatePurchase(purchase)
            }
            // handle acknowledgement
            if (autoAcknowledgePurchases) {
                acknowledgePurchases(purchases)
            }
            // invoke callback
            invokeListener(event = BillingEvent.QUERY_OWNED_PURCHASES_COMPLETE)
        }
        // query for type on current index
        else {
            billingClient.queryPurchasesAsync(types[currentTypeIndex]) { queryResult, purchases ->
                if (queryResult.isResponseOk()) {
                    resultingList.addAll(purchases)
                    initQueryOwnedPurchasesForType(types, currentTypeIndex+1, resultingList)
                } else {
                    invokeListener(
                        event = BillingEvent.QUERY_OWNED_PURCHASES_FAILED,
                        message = queryResult.debugMessage,
                        responseCode = queryResult.responseCode
                    )
                }
            }
        }
    }

    // allows to call querySkuDetailsAsync recursively on all types
    private fun initQuerySkuDetailsForType(types: List<String>,
                                           currentTypeIndex: Int,
                                           resultingList: MutableList<SkuDetails>) {
        // handled all types
        if (currentTypeIndex == types.size) {
            this.skuDetailsQueried = true
            this.skuDetailsList.clear()
            this.skuDetailsList.addAll(resultingList)
            invokeListener(BillingEvent.QUERY_SKU_DETAILS_COMPLETE)
        }
        // query for type on current index
        else {
            val currentType = types[currentTypeIndex]
            val skuDetailsParams = SkuDetailsParams.newBuilder()
                .setSkusList(getSkusForType(currentType))
                .setType(currentType)
                .build()
            billingClient.querySkuDetailsAsync(skuDetailsParams) { queryResult, skuDetailsList ->
                if (queryResult.isResponseOk()) {
                    skuDetailsList?.let { resultingList.addAll(it) }
                    initQuerySkuDetailsForType(types, currentTypeIndex+1, resultingList)
                } else {
                    invokeListener(
                        event = BillingEvent.QUERY_SKU_DETAILS_FAILED,
                        message = queryResult.debugMessage,
                        responseCode = queryResult.responseCode
                    )
                }
            }
        }
    }

    // purchases list repopulate and handle logic of acknowledge check and init
    @Synchronized
    private fun addOrUpdatePurchase(purchase: Purchase) {
        val newPurchases = this.purchases
            .filter { it.orderId != purchase.orderId }
            .toMutableList()
            .also {
                // only include it if signature is valid
                if (isSignatureValid(purchase)) {
                    it.add(purchase)
                    
                    if (enableLogging) {
                        Log.d(TAG, "Owned purchase added: ${purchase.skus}")
                    }
                }
            }

        this.purchases.clear()
        this.purchases.addAll(newPurchases)
    }

    // get available sku based on SkyType
    private fun getSkusForType(@BillingClient.SkuType type: String): List<String> {
        return when (type) {
            BillingClient.SkuType.INAPP -> skuInAppPurchases.orEmpty()
            BillingClient.SkuType.SUBS -> skuSubscriptions.orEmpty()
            else -> emptyList()
        }
    }

    // get available sku based on SkuTypes based on skuInAppPurchases and skuSubscriptions availability
    private fun getAvailableTypes(): List<String> {
        return mutableListOf<String>().apply {
            if (skuInAppPurchases.isNullOrEmpty().not()) {
                add(BillingClient.SkuType.INAPP)
            }
            if (skuSubscriptions.isNullOrEmpty().not()) {
                add(BillingClient.SkuType.SUBS)
            }
        }
    }

    // verify purchase if key is present
    private fun isSignatureValid(purchase: Purchase): Boolean {
        val key = this.key ?: return true
        return Security.verifyPurchase(key, purchase.originalJson, purchase.signature)
    }
    // endregion Private Methods

    // region private extension functions
    private fun BillingResult.isResponseOk(): Boolean =
        responseCode == BillingClient.BillingResponseCode.OK

    private fun BillingResult.isResponseUserCancelled(): Boolean =
        responseCode == BillingClient.BillingResponseCode.USER_CANCELED
    // endregion private extension functions
}