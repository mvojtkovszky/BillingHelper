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
 * @param productInAppPurchases list of sku names of in app purchases supported by the app.
 * @param productSubscriptions list of sku names of subscriptions supported by the app.
 * @param startConnectionImmediately set whether [initClientConnection] should be called automatically
 * when [BillingHelper] is initialized.
 * @param key app's license key. If provided, it will be used to verify purchase signatures.
 * @param querySkuDetailsOnConnected set whether [initQueryProductDetails] should be called automatically
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
    private val productInAppPurchases: List<String>?,
    private val productSubscriptions: List<String>?,
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
    private val productDetailsList = mutableListOf<ProductDetails>()
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
    var purchasesQueried: Boolean = false
        private set

    /**
     * Determine if sku details have been successfully queried yet.
     * That happens with successful completion of [initQueryProductDetails].
     *
     * Until sku details are queries any call to [launchPurchaseFlow] will fail.
     */
    var productDetailsQueried: Boolean = false
        private set

    /**
     * Ended up in a state of connection failure when trying to init client connection.
     * If true, we know that connection was initialized but failed instead of just current
     * connection state.
     * This can happen if device is disconnected from Play services or user not logged in to the
     * Play Store app.
     */
    var isConnectionFailure: Boolean = false
        private set

    /**
     * Tells us if the state of purchases can be presented to the app.
     * We determine this if either [purchasesQueried] is true or [isConnectionFailure] is true.
     *
     * -> If purchases have been queried, it means we are connected to billing and got result from,
     *    querying purchases, so all is well.
     * -> If connection failed, it means there are no purchases and neither will be.
     *    This can happen if device is disconnected from Play services or user not logged in to the
     *    Play Store app.
     *
     * In either case [Boolean.true] indicates that we did whatever we can to determine if purchases
     * are available and can consider this state final and presentable to the app.
     */
    val purchasesPresentable: Boolean
        get() = purchasesQueried || isConnectionFailure

    init {
        if (productSubscriptions == null && productInAppPurchases == null) {
            throw IllegalStateException(
                "Both productSubscriptions and productInAppPurchases missing. Define at least one of them."
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
     * Will return a single [Purchase] object that contains a given [productName] or null
     * if no match found.
     * Note that you need to query for owned purchases using [initQueryProductDetails] or complete a
     * purchase before, in order for this to be not null.
     */
    fun getPurchaseWithProductName(productName: String): Purchase? {
        return try {
            purchases.find { it.products.contains(productName) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Will return a single [ProductDetails] object with a given [productName] or null if no match found.
     * Note that you need to query for sku details first using [initQueryOwnedPurchases] in order
     * for this not to be null.
     */
    fun getProductDetails(productName: String): ProductDetails? {
        return try {
            productDetailsList.find { it.productId == productName }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Determine whether product with given name has state set as purchased
     */
    fun isPurchased(productName: String): Boolean {
        return getPurchaseWithProductName(productName)?.isPurchased() == true
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
     * Will start a purchase flow for given product name.
     * Result will get back to [PurchasesUpdatedListener]
     *
     * @param selectedOfferIndex see [ProductDetails.SubscriptionOfferDetails]
     */
    fun launchPurchaseFlow(activity: Activity,
                           productName: String,
                           obfuscatedAccountId: String? = null,
                           obfuscatedProfileId: String? = null,
                           isOfferPersonalized: Boolean? = null,
                           selectedOfferIndex: Int = 0
    ) {
        val productDetailsForPurchase = getProductDetails(productName)

        if (billingClient.isReady && productDetailsForPurchase != null) {
            val offerToken = selectedOfferIndex.let {
                productDetailsForPurchase.subscriptionOfferDetails?.get(it)?.offerToken
            }

            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder().apply {
                setProductDetails(productDetailsForPurchase)
                // offer token required for subscriptions
                if (productDetailsForPurchase.isSubscription()) {
                    offerToken?.let { setOfferToken(offerToken) }
                }
            }.build()

            val billingFlowParams = BillingFlowParams.newBuilder().apply {
                setProductDetailsParamsList(listOf(productDetailsParams))
                obfuscatedAccountId?.let { setObfuscatedAccountId(it) }
                obfuscatedProfileId?.let { setObfuscatedProfileId(it) }
                isOfferPersonalized?.let { setIsOfferPersonalized(it) }
            } .build()

            // Launch the billing flow
            val result = billingClient.launchBillingFlow(activity, billingFlowParams)

            // report failure
            if (!result.isResponseOk()) {
                invokeListener(
                    event = BillingEvent.PURCHASE_FAILED,
                    message = getPurchaseFlowErrorMessage(productName)
                )
            }
        } else {
            // report purchase flow error
            invokeListener(
                event = BillingEvent.PURCHASE_FAILED,
                message = getPurchaseFlowErrorMessage(productName)
            )
        }
    }

    /**
     * Will initiate [BillingClient.startConnection]. If client is already connected, process
     * will be skipped and [BillingEvent.BILLING_CONNECTED] will be invoked.
     *
     * @param querySkuDetailsOnConnected set whether [initQueryProductDetails] should be called
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
                isConnectionFailure = !billingResult.isResponseOk()
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
                        initQueryProductDetails()
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
     * Initialize query for [ProductDetails] listed for this app.
     * Will query for both in-app purchases and subscriptions.
     * Result will be returned using [billingListeners]
     *
     * Note if queryForSkuDetailsOnInit=true in the helper constructor, method gets called
     * automatically when client connects (See [BillingHelper] constructor for more info).
     */
    fun initQueryProductDetails() {
        initQueryProductDetailsByType(getAvailableTypes(), 0, mutableListOf())
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
            // repopulate purchases
            for (purchase in resultingList) {
                addOrUpdatePurchase(purchase)
            }
            // handle acknowledgement
            if (autoAcknowledgePurchases) {
                acknowledgePurchases(purchases)
            }
            // mark as queried
            this.purchasesQueried = true
            // invoke callback
            invokeListener(event = BillingEvent.QUERY_OWNED_PURCHASES_COMPLETE)
        }
        // query for type on current index
        else {
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(types[currentTypeIndex]).build()
            ) { queryResult, purchases ->
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

    // allows to call queryProductDetailsAsync recursively on all types
    private fun initQueryProductDetailsByType(
        types: List<String>,
        currentTypeIndex: Int,
        resultingList: MutableList<ProductDetails>) {

        // handled all types
        if (currentTypeIndex == types.size) {
            // repopulate product details list
            this.productDetailsList.clear()
            this.productDetailsList.addAll(resultingList)
            // mark as queried
            this.productDetailsQueried = true
            // invoke callback
            invokeListener(event = BillingEvent.QUERY_PRODUCT_DETAILS_COMPLETE)
        }
        // query for type on current index
        else {
            val products = mutableListOf<QueryProductDetailsParams.Product>()
            if (types[currentTypeIndex] == BillingClient.ProductType.SUBS) {
                for (sub in productSubscriptions.orEmpty()) {
                    products.add(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(sub)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                }
            }
            else if (types[currentTypeIndex] == BillingClient.ProductType.INAPP) {
                for (iap in productInAppPurchases.orEmpty()) {
                    products.add(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(iap)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                }
            }

            val params = QueryProductDetailsParams.newBuilder().setProductList(products)
            billingClient.queryProductDetailsAsync(params.build()) { queryResult, productDetailsList ->
                if (queryResult.isResponseOk()) {
                    resultingList.addAll(productDetailsList)
                    initQueryProductDetailsByType(types, currentTypeIndex+1, resultingList)
                } else {
                    invokeListener(
                        event = BillingEvent.QUERY_PRODUCT_DETAILS_FAILED,
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
                        Log.d(TAG, "Owned purchase added: ${purchase.products}")
                    }
                }
            }

        this.purchases.clear()
        this.purchases.addAll(newPurchases)
    }

    // get available sku based on SkuTypes based on skuInAppPurchases and skuSubscriptions availability
    private fun getAvailableTypes(): List<String> {
        return mutableListOf<String>().apply {
            if (productInAppPurchases.isNullOrEmpty().not()) {
                add(BillingClient.ProductType.INAPP)
            }
            if (productSubscriptions.isNullOrEmpty().not()) {
                add(BillingClient.ProductType.SUBS)
            }
        }
    }

    // verify purchase if key is present
    private fun isSignatureValid(purchase: Purchase): Boolean {
        val key = this.key ?: return true
        return Security.verifyPurchase(key, purchase.originalJson, purchase.signature)
    }

    // figure out what's wrong when trying to initialize purchase, because it fails due to
    // predictable reasons
    private fun getPurchaseFlowErrorMessage(skuName: String): String {
        return when {
            !billingClient.isReady -> "Billing not ready."
            !productDetailsQueried -> "SKU details have not been queried yet."
            else -> "skuName $skuName not recognized among sku details."
        }
    }
    // endregion Private Methods

    // region private extension functions
    private fun BillingResult.isResponseOk(): Boolean =
        responseCode == BillingClient.BillingResponseCode.OK

    private fun BillingResult.isResponseUserCancelled(): Boolean =
        responseCode == BillingClient.BillingResponseCode.USER_CANCELED
    // endregion private extension functions
}