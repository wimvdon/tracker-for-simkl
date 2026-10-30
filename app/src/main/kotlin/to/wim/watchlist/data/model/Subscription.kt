package to.wim.watchlist.data.model

import java.time.Period
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

/** What Google Play last said about the subscription. */
enum class PlayStatus {
    /** Not asked yet (the app just started). */
    Unknown,

    /** A purchased (or in its free trial) subscription. */
    Active,

    /** Bought, but Google Play hasn't confirmed the payment yet (some payment methods take days). */
    Pending,

    /** No subscription. */
    Inactive,

    /** Google Play couldn't be asked: not installed, signed out, blocked, offline. */
    Unavailable,
}

/** Whether the app opens, or shows the subscription screen. */
enum class Access { Checking, Allowed, Locked }

/** The last answer Google Play gave, kept on the phone so the app opens offline. */
data class StoredAccess(
    val active: Boolean,
    /** Epoch ms. */
    val checkedAt: Long,
)

/** A purchase as far as the app cares: which products, and whether it is paid. */
data class PurchaseInfo(
    val products: List<String>,
    val purchased: Boolean,
    val pending: Boolean,
)

/** One price phase of a subscription offer, as Google Play describes it. */
data class PricePhase(
    val priceMicros: Long,
    /** Already in the user's currency and format ("€3.99", "3,99 €"). */
    val formattedPrice: String,
    /** ISO 8601 period: "P1Y", "P2W", "P14D". */
    val billingPeriod: String,
)

/** One way to buy the subscription (the plain yearly plan, or the free-trial offer). */
data class SubscriptionOffer(
    val token: String,
    val phases: List<PricePhase>,
)

/** What the subscription screen offers: a free trial of [trialDays] (null: none), then [yearlyPrice] a year. */
data class PaywallOffer(
    val token: String,
    val trialDays: Int?,
    val yearlyPrice: String,
)

/**
 * The rules of the subscription: a free trial and then a yearly plan, both sold by Google Play
 * (Play decides who still gets the trial). Without an active subscription the app is locked.
 * Plain Kotlin so it is unit tested; `SubscriptionRepository` talks to Google Play.
 */
object SubscriptionRules {
    /** The subscription's product ID in the Play Console (see docs/publishing.md). */
    const val PRODUCT_ID = "watchlist_yearly"

    /** How long a remembered "active" opens the app while Google Play can't be asked. */
    val OFFLINE_GRACE = TimeUnit.DAYS.toMillis(7)

    fun status(purchases: List<PurchaseInfo>): PlayStatus {
        val mine = purchases.filter { PRODUCT_ID in it.products }
        return when {
            mine.any { it.purchased } -> PlayStatus.Active
            mine.any { it.pending } -> PlayStatus.Pending
            else -> PlayStatus.Inactive
        }
    }

    /**
     * [required] is false in debug builds, which are installed next to the Play app and can't buy
     * anything. A remembered "active" younger than [OFFLINE_GRACE] stands in while Google Play
     * can't be asked, so a subscriber isn't locked out on a plane.
     */
    fun access(
        required: Boolean,
        status: PlayStatus,
        stored: StoredAccess?,
        now: Long,
    ): Access {
        if (!required) return Access.Allowed
        val recentlyActive = stored != null && stored.active && now - stored.checkedAt in 0 until OFFLINE_GRACE
        return when (status) {
            PlayStatus.Active -> Access.Allowed
            PlayStatus.Pending, PlayStatus.Inactive -> Access.Locked
            PlayStatus.Unknown -> if (recentlyActive) Access.Allowed else Access.Checking
            PlayStatus.Unavailable -> if (recentlyActive) Access.Allowed else Access.Locked
        }
    }

    /**
     * The offer the subscription screen shows: one that starts with a free phase (Google Play only
     * returns the trial offer to people who haven't had it), else the plain yearly plan.
     */
    fun paywallOffer(offers: List<SubscriptionOffer>): PaywallOffer? {
        fun SubscriptionOffer.yearly() = phases.lastOrNull { it.priceMicros > 0 }
        val trial = offers.firstOrNull { o -> o.phases.firstOrNull()?.priceMicros == 0L && o.yearly() != null }
        if (trial != null) return PaywallOffer(trial.token, days(trial.phases.first().billingPeriod), trial.yearly()!!.formattedPrice)
        val plain = offers.firstOrNull { it.yearly() != null } ?: return null
        return PaywallOffer(plain.token, null, plain.yearly()!!.formattedPrice)
    }

    /** "P14D" → 14, "P2W" → 14, "P1M" → 30; null when it can't be read. */
    fun days(period: String): Int? =
        try {
            val p = Period.parse(period)
            (p.years * 365 + p.months * 30 + p.days).takeIf { it > 0 }
        } catch (e: DateTimeParseException) {
            null
        }
}
