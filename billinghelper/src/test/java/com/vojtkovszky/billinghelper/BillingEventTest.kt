package com.vojtkovszky.billinghelper

import com.vojtkovszky.billinghelper.BillingEvent.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingEventTest {

    private fun BillingEvent.flows() = listOf(
        isBillingConnectionFlow, isQueryProductDetailsFlow, isQueryOwnedPurchasesFlow,
        isConsumePurchaseFlow, isPurchaseFlow, isPurchaseAcknowledgeFlow
    )

    @Test
    fun `every event belongs to exactly one flow`() {
        BillingEvent.entries.forEach { event ->
            assertEquals("$event flows", 1, event.flows().count { it })
        }
    }

    @Test
    fun `no event is both success and failure`() {
        BillingEvent.entries.forEach { event ->
            assertFalse("$event", event.isSuccess && event.isFailure)
        }
    }

    @Test
    fun `failed events are failures`() {
        listOf(BILLING_CONNECTION_FAILED, QUERY_PRODUCT_DETAILS_FAILED, QUERY_OWNED_PURCHASES_FAILED,
            PURCHASE_FAILED, CONSUME_PURCHASE_FAILED).forEach { assertTrue("$it", it.isFailure) }
    }

    @Test
    fun `completed events are successes`() {
        listOf(BILLING_CONNECTED, QUERY_PRODUCT_DETAILS_COMPLETE, QUERY_OWNED_PURCHASES_COMPLETE,
            PURCHASE_COMPLETE, PURCHASE_ACKNOWLEDGE_SUCCESS, CONSUME_PURCHASE_SUCCESS)
            .forEach { assertTrue("$it", it.isSuccess) }
    }

    @Test
    fun `cancel and disconnect are neither success nor failure`() {
        listOf(PURCHASE_CANCELLED, BILLING_DISCONNECTED).forEach {
            assertFalse("$it", it.isSuccess)
            assertFalse("$it", it.isFailure)
        }
    }

    @Test
    fun `active actions and owned purchase changes`() {
        assertEquals(listOf(PURCHASE_FAILED), BillingEvent.entries.filter { it.isActiveActionFailure })
        assertEquals(listOf(PURCHASE_COMPLETE), BillingEvent.entries.filter { it.isActiveActionSuccess })
        assertEquals(
            listOf(QUERY_OWNED_PURCHASES_COMPLETE, PURCHASE_COMPLETE).toSet(),
            BillingEvent.entries.filter { it.isOwnedPurchasesChange }.toSet()
        )
    }
}
