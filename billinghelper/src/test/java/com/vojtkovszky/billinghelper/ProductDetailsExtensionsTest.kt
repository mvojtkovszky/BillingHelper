package com.vojtkovszky.billinghelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductDetailsExtensionsTest {

    private val subscription = TestData.subscription("sub", listOf(TestData.Offer("monthly", null, "token")))
    private val inApp = TestData.inAppProduct("lifetime")

    @Test
    fun `type checks`() {
        assertTrue(subscription.isSubscription())
        assertFalse(subscription.isInAppPurchase())
        assertTrue(inApp.isInAppPurchase())
        assertFalse(inApp.isSubscription())
    }

    @Test
    fun `list filters split by type`() {
        val list = listOf(subscription, inApp)
        assertEquals(listOf("sub"), list.getSubscriptions().map { it.productId })
        assertEquals(listOf("lifetime"), list.getInAppPurchases().map { it.productId })
    }
}
