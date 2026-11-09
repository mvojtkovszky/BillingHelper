package com.vojtkovszky.billinghelper

import com.vojtkovszky.billinghelper.TestData.Offer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionPurchaseParamsTest {

    private val details = TestData.subscription(
        "sub",
        listOf(
            Offer(basePlanId = "monthly", offerId = null, token = "monthly-base"),
            Offer(basePlanId = "monthly", offerId = "trial", token = "monthly-trial"),
            Offer(basePlanId = "yearly", offerId = null, token = "yearly-base"),
            Offer(basePlanId = "yearly", offerId = "intro", token = "yearly-intro"),
        )
    )

    @Test
    fun `no params picks the first offer`() {
        assertEquals("monthly-base", SubscriptionPurchaseParams().getOfferToken(details))
    }

    @Test
    fun `base plan picks its first offer`() {
        assertEquals("yearly-base", SubscriptionPurchaseParams(basePlanId = "yearly").getOfferToken(details))
    }

    @Test
    fun `base plan and offer pick the exact offer`() {
        assertEquals(
            "yearly-intro",
            SubscriptionPurchaseParams(basePlanId = "yearly", offerId = "intro").getOfferToken(details)
        )
    }

    @Test
    fun `offer alone picks the offer on any base plan`() {
        assertEquals("monthly-trial", SubscriptionPurchaseParams(offerId = "trial").getOfferToken(details))
    }

    @Test
    fun `unknown base plan or offer gives no token`() {
        assertNull(SubscriptionPurchaseParams(basePlanId = "weekly").getOfferToken(details))
        assertNull(SubscriptionPurchaseParams(basePlanId = "monthly", offerId = "intro").getOfferToken(details))
    }

    @Test
    fun `in-app product has no offer token`() {
        assertNull(SubscriptionPurchaseParams().getOfferToken(TestData.inAppProduct("lifetime")))
    }

    @Test
    fun `update params only when an old token or transaction is given`() {
        assertNull(SubscriptionPurchaseParams().getSubscriptionUpdateParams())
        assertNotNull(SubscriptionPurchaseParams(updateOldToken = "old").getSubscriptionUpdateParams())
        assertNotNull(SubscriptionPurchaseParams(updateExternalTransactionId = "ext").getSubscriptionUpdateParams())
    }

    @Test
    fun `replacement params only when an old product or mode is given`() {
        assertNull(SubscriptionPurchaseParams().getSubscriptionProductReplacementParams())
        assertNotNull(
            SubscriptionPurchaseParams(replacementOldProductId = "old", replacementMode = 1)
                .getSubscriptionProductReplacementParams()
        )
    }
}
