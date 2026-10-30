package to.wim.watchlist.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import to.wim.watchlist.data.model.Access
import to.wim.watchlist.data.model.PaywallOffer
import to.wim.watchlist.data.model.PlayStatus
import to.wim.watchlist.data.model.PricePhase
import to.wim.watchlist.data.model.PurchaseInfo
import to.wim.watchlist.data.model.StoredAccess
import to.wim.watchlist.data.model.SubscriptionOffer
import to.wim.watchlist.data.model.SubscriptionRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class SubscriptionState(
    val status: PlayStatus = PlayStatus.Unknown,
    /** What the subscription screen sells; null until Google Play described the product. */
    val offer: PaywallOffer? = null,
    val access: Access = Access.Checking,
    /** A purchase screen of Google Play is open. */
    val purchasing: Boolean = false,
    /** Google Play's reason when a purchase failed (not when the user cancelled). */
    val purchaseFailed: Boolean = false,
)

/**
 * The yearly subscription, sold through Google Play Billing (product
 * [SubscriptionRules.PRODUCT_ID]; the 14-day free trial is an offer on it in the Play Console).
 * Google Play keeps the purchases on the phone, so asking works offline too; the last answer is
 * also stored, so the app opens while Google Play can't be reached ([SubscriptionRules.access]).
 */
class SubscriptionRepository(
    context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    /** False in debug builds: they are never locked (see [SubscriptionRules.access]). */
    private val required: Boolean,
) {
    private val _state = MutableStateFlow(SubscriptionState(access = if (required) Access.Checking else Access.Allowed))
    val state: StateFlow<SubscriptionState> = _state.asStateFlow()

    private var product: ProductDetails? = null
    private val refreshLock = Mutex()

    private val client: BillingClient =
        BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> scope.launch { onPurchasesUpdated(result, purchases) } }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()

    /** Connects to Google Play when needed; false when it isn't available. */
    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { continuation ->
                client.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (continuation.isActive) continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }

                        override fun onBillingServiceDisconnected() {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    },
                )
            }
        } ?: false
    }

    /** Asks Google Play again (app start, back to the foreground, "Check again"). */
    suspend fun refresh() {
        if (!required) return
        refreshLock.withLock {
            if (!connect()) return@withLock publish(PlayStatus.Unavailable)
            val purchases = queryPurchases() ?: return@withLock publish(PlayStatus.Unavailable)
            purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }.forEach { acknowledge(it) }
            if (product == null) product = queryProduct()
            publish(SubscriptionRules.status(purchases.map { it.info() }))
        }
    }

    /** For the background check: Google Play's answer if it comes quickly, else the stored one. */
    suspend fun currentAccess(): Access {
        if (!required) return Access.Allowed
        withTimeoutOrNull(15_000) { refresh() }
        return SubscriptionRules.access(required, _state.value.status, settings.storedAccess(), System.currentTimeMillis())
    }

    /** Opens Google Play's purchase sheet for [offer]. */
    fun purchase(
        activity: Activity,
        offer: PaywallOffer,
    ) {
        val details = product ?: return
        val params =
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.token).build()),
                ).build()
        _state.update { it.copy(purchasing = true, purchaseFailed = false) }
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) _state.update { it.copy(purchasing = false, purchaseFailed = true) }
    }

    private suspend fun onPurchasesUpdated(
        result: BillingResult,
        purchases: List<Purchase>?,
    ) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }.forEach { acknowledge(it) }
                _state.update { it.copy(purchasing = false) }
                publish(SubscriptionRules.status(purchases.orEmpty().map { it.info() }).takeIf { it != PlayStatus.Inactive } ?: _state.value.status)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _state.update { it.copy(purchasing = false) }
            // Bought on another phone, or a renewal: ask again rather than trust this answer.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _state.update { it.copy(purchasing = false) }
                refresh()
            }
            else -> _state.update { it.copy(purchasing = false, purchaseFailed = true) }
        }
    }

    private suspend fun publish(status: PlayStatus) {
        val now = System.currentTimeMillis()
        when (status) {
            PlayStatus.Active -> settings.setStoredAccess(StoredAccess(true, now))
            PlayStatus.Inactive, PlayStatus.Pending -> settings.setStoredAccess(StoredAccess(false, now))
            PlayStatus.Unknown, PlayStatus.Unavailable -> Unit
        }
        val offer = product?.let { SubscriptionRules.paywallOffer(it.offers()) }
        _state.update { it.copy(status = status, offer = offer ?: it.offer, access = SubscriptionRules.access(required, status, settings.storedAccess(), now)) }
    }

    private suspend fun queryPurchases(): List<Purchase>? =
        suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, list ->
                if (continuation.isActive) continuation.resume(list.takeIf { result.responseCode == BillingClient.BillingResponseCode.OK })
            }
        }

    private suspend fun queryProduct(): ProductDetails? =
        suspendCancellableCoroutine { continuation ->
            val product =
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SubscriptionRules.PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, details ->
                if (continuation.isActive) {
                    continuation.resume(details.productDetailsList.firstOrNull().takeIf { result.responseCode == BillingClient.BillingResponseCode.OK })
                }
            }
        }

    /** Google Play refunds a purchase that isn't acknowledged within three days. */
    private suspend fun acknowledge(purchase: Purchase) {
        suspendCancellableCoroutine { continuation ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private fun Purchase.info() =
        PurchaseInfo(products, purchased = purchaseState == Purchase.PurchaseState.PURCHASED, pending = purchaseState == Purchase.PurchaseState.PENDING)

    private fun ProductDetails.offers(): List<SubscriptionOffer> =
        subscriptionOfferDetails.orEmpty().map { o ->
            SubscriptionOffer(o.offerToken, o.pricingPhases.pricingPhaseList.map { PricePhase(it.priceAmountMicros, it.formattedPrice, it.billingPeriod) })
        }

    companion object {
        /** Google Play's page where the user changes or cancels the subscription. */
        fun manageUrl(packageName: String) = "https://play.google.com/store/account/subscriptions?sku=${SubscriptionRules.PRODUCT_ID}&package=$packageName"
    }
}
