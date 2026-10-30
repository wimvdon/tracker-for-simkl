package to.wim.watchlist.data

import to.wim.watchlist.data.model.Access
import to.wim.watchlist.data.model.PaywallOffer
import to.wim.watchlist.data.model.PlayStatus
import to.wim.watchlist.data.model.PricePhase
import to.wim.watchlist.data.model.PurchaseInfo
import to.wim.watchlist.data.model.StoredAccess
import to.wim.watchlist.data.model.SubscriptionOffer
import to.wim.watchlist.data.model.SubscriptionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class SubscriptionTest {
    private val id = SubscriptionRules.PRODUCT_ID
    private val now = 1_800_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)

    @Test
    fun purchasesOfThisSubscriptionDecideTheStatus() {
        assertEquals(PlayStatus.Inactive, SubscriptionRules.status(emptyList()))
        assertEquals(PlayStatus.Active, SubscriptionRules.status(listOf(PurchaseInfo(listOf(id), purchased = true, pending = false))))
        assertEquals(PlayStatus.Pending, SubscriptionRules.status(listOf(PurchaseInfo(listOf(id), purchased = false, pending = true))))
        // Another product doesn't unlock the app.
        assertEquals(PlayStatus.Inactive, SubscriptionRules.status(listOf(PurchaseInfo(listOf("other"), purchased = true, pending = false))))
    }

    @Test
    fun withoutAnActiveSubscriptionTheAppIsLocked() {
        assertEquals(Access.Allowed, SubscriptionRules.access(true, PlayStatus.Active, null, now))
        assertEquals(Access.Locked, SubscriptionRules.access(true, PlayStatus.Inactive, null, now))
        assertEquals(Access.Locked, SubscriptionRules.access(true, PlayStatus.Pending, null, now))
        // A remembered subscription doesn't outweigh Google Play saying it ended.
        assertEquals(Access.Locked, SubscriptionRules.access(true, PlayStatus.Inactive, StoredAccess(true, now), now))
    }

    @Test
    fun aRecentlyActiveSubscriptionOpensTheAppWhilePlayCantBeAsked() {
        val recent = StoredAccess(active = true, checkedAt = now - 2 * day)
        val old = StoredAccess(active = true, checkedAt = now - 8 * day)
        assertEquals(Access.Allowed, SubscriptionRules.access(true, PlayStatus.Unknown, recent, now))
        assertEquals(Access.Allowed, SubscriptionRules.access(true, PlayStatus.Unavailable, recent, now))
        assertEquals(Access.Locked, SubscriptionRules.access(true, PlayStatus.Unavailable, old, now))
        assertEquals(Access.Checking, SubscriptionRules.access(true, PlayStatus.Unknown, null, now))
        assertEquals(Access.Checking, SubscriptionRules.access(true, PlayStatus.Unknown, StoredAccess(false, now), now))
        // A clock set back doesn't extend it.
        assertEquals(Access.Locked, SubscriptionRules.access(true, PlayStatus.Unavailable, StoredAccess(true, now + day), now))
    }

    @Test
    fun debugBuildsAreNeverLocked() {
        assertEquals(Access.Allowed, SubscriptionRules.access(false, PlayStatus.Inactive, null, now))
    }

    private val yearly = PricePhase(3_990_000, "€3.99", "P1Y")

    @Test
    fun theFreeTrialIsOfferedWhenPlayHasIt() {
        val offers =
            listOf(
                SubscriptionOffer("plain", listOf(yearly)),
                SubscriptionOffer("trial", listOf(PricePhase(0, "Free", "P2W"), yearly)),
            )
        assertEquals(PaywallOffer("trial", 14, "€3.99"), SubscriptionRules.paywallOffer(offers))
    }

    @Test
    fun withoutTheTrialThePlainYearlyPlanIsOffered() {
        // Someone who already had the trial only gets the base plan from Google Play.
        assertEquals(PaywallOffer("plain", null, "€3.99"), SubscriptionRules.paywallOffer(listOf(SubscriptionOffer("plain", listOf(yearly)))))
        assertNull(SubscriptionRules.paywallOffer(emptyList()))
    }

    @Test
    fun trialPeriodsAreReadInDays() {
        assertEquals(14, SubscriptionRules.days("P14D"))
        assertEquals(14, SubscriptionRules.days("P2W"))
        assertEquals(30, SubscriptionRules.days("P1M"))
        assertNull(SubscriptionRules.days("two weeks"))
    }
}
