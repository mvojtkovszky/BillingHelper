package com.vojtkovszky.billinghelper

import com.android.billingclient.api.Purchase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseListTest {

    @Test
    fun `purchase is parsed from store json`() {
        val purchase = TestData.purchase("t1", products = listOf("pro"))
        assertEquals("t1", purchase.purchaseToken)
        assertEquals(listOf("pro"), purchase.products)
        assertEquals(Purchase.PurchaseState.PURCHASED, purchase.purchaseState)
    }

    @Test
    fun `isPurchased is true only for purchased state`() {
        assertTrue(TestData.purchase("t1").isPurchased())
        assertFalse(TestData.purchase("t2", pending = true).isPurchased())
    }

    @Test
    fun `withPurchase adds a new purchase at the end`() {
        val list = listOf(TestData.purchase("t1")).withPurchase(TestData.purchase("t2"))
        assertEquals(listOf("t1", "t2"), list.map { it.purchaseToken })
    }

    @Test
    fun `withPurchase replaces a purchase with the same token`() {
        val old = TestData.purchase("t1", acknowledged = false)
        val updated = TestData.purchase("t1", acknowledged = true)
        val list = listOf(old, TestData.purchase("t2")).withPurchase(updated)
        assertEquals(listOf("t2", "t1"), list.map { it.purchaseToken })
        assertTrue(list.last().isAcknowledged)
    }

    @Test
    fun `two pending purchases without order id are both kept`() {
        val first = TestData.purchase("t1", products = listOf("a"), orderId = null, pending = true)
        val second = TestData.purchase("t2", products = listOf("b"), orderId = null, pending = true)
        val list = listOf(first).withPurchase(second)
        assertEquals(listOf("t1", "t2"), list.map { it.purchaseToken })
    }

    @Test
    fun `pending purchase that completes replaces the pending copy`() {
        val pending = TestData.purchase("t1", orderId = null, pending = true)
        val completed = TestData.purchase("t1", orderId = "GPA.1")
        val list = listOf(pending).withPurchase(completed)
        assertEquals(1, list.size)
        assertTrue(list.single().isPurchased())
    }

    @Test
    fun `withoutPurchase removes only the matching token`() {
        val list = listOf(TestData.purchase("t1"), TestData.purchase("t2")).withoutPurchase("t1")
        assertEquals(listOf("t2"), list.map { it.purchaseToken })
    }

    @Test
    fun `withoutPurchase with unknown token keeps the list`() {
        val list = listOf(TestData.purchase("t1"))
        assertEquals(list, list.withoutPurchase("unknown"))
    }

    @Test
    fun `withProduct finds purchases containing the product`() {
        val list = listOf(
            TestData.purchase("t1", products = listOf("a")),
            TestData.purchase("t2", products = listOf("a", "b")),
            TestData.purchase("t3", products = listOf("c")),
        )
        assertEquals(listOf("t1", "t2"), list.withProduct("a").map { it.purchaseToken })
        assertEquals(listOf("t2"), list.withProduct("b").map { it.purchaseToken })
        assertTrue(list.withProduct("missing").isEmpty())
    }
}
