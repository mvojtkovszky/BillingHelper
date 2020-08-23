package com.vojtkovszky.billinghelper

interface BillingListener {
    fun onBillingEvent(event: BillingEvent, message: String?)
}