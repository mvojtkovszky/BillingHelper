# CHANGELOG

## 3.4.0 (TBD)
* add support for purchase history records. New methods and properties:
  - `BillingHelper.purchaseHistoryRecordsQueried`, `BillingHelper.initQueryPurchaseHistoryRecords` and `BillingHelper.getPurchaseHistoryRecords`
  - `BillingEvent.QUERY_PURCHASES_HISTORY_RECORDS_COMPLETE` and `BillingEvent.QUERY_PURCHASES_HISTORY_RECORDS_FAILED`
  - `queryPurchaseHistoryRecordsOnConnected` in `BillingHelper` constructor
* fix `ProductDetails.getFormattedPriceDivided` to cover cases where price is formatted using comma and dot.
* rename `querySkuDetailsOnConnected` to `queryProductDetailsOnConnected`.
* rename `getPurchaseWithProductName` to `getPurchasesForProductName` as multiple purchases can exist for a single product name.
  f.ex. when updating a subscription. Otherwise `.last()` is the one you're after.
* bump Kotlin to 2.0.21, Gradle plugin to 8.7.3

## 3.3.0 (2024-11-11)
* Added optional `subscriptionPurchaseParams` to `launchPurchaseFlow` method to unify subscription purchase definitions. 
  Added optional parameters `basePlanId: String?` and `offerId: String?`, which replaced old index parameters. 
  Similarly with `ProductDetails.getFormattedPrice`, which now take optional `subscriptionBasePlanId` and `subscriptionOfferId`.
* Removed `PriceUtil` object, use extension methods `ProductDetails.getFormattedPrice` and `ProductDetails.getFormattedPriceDivided`.

## 3.2.0 (2024-11-05)
* Support subscription updates or replacements: Added optional parameters `subscriptionUpdateOldToken`, 
  `subscriptionUpdateExternalTransactionId` and `subscriptionUpdateReplacementMode` to `launchPurchaseFlow()` 
* bump Gradle plugin to 8.7.2, core-ktx to 1.15.0

## 3.1.0 (2024-10-18)
* bump Google Billing to 7.1.1
* bump kotlin to 2.0.20, Gradle plugin to 8.7.1

## 3.0.0 (2024-06-07)
* add `´billingBuilderConfig` to `BillingHelper` constructor holding all parameters related to alternative billing and pending purchases support
* move `enableAlternativeBillingOnly` and `enableExternalOffer` to `BillingBuilderConfig`
* bump Google Billing to 7.0.0
* bump Gradle plugin to 8.3.2, core-ktx to 1.13.1
* `minSdkVersion` has been raised from 19 to 21

## 2.4.0 (2024-04-08)
* `BillingHelper.billingClient` is now public.
* Add `enableAlternativeBillingOnly` and `enableExternalOffer` properties to constructor.
* bump Google Billing to 6.2.0
* bump Gradle plugin to 8.3.1, Kotlin to 1.9.23

## 2.3.0 (2023-12-07)
* rename param from "skuNames" to "productNames" for `BillingHelper.isPurchasedAnyOf`
* fix issue where purchase will still be present even if not included in the result from additional call to `BillingHelper.initQueryOwnedPurchases`
* fix documentation and readme description
* bump Google Billing to 6.1.0 (note: support for alternative billing still not directly included in this release)
* bump kotlin to 1.9.10, core-ktx to 1.12.0, Gradle plugin to 8.1.4

## 2.2.1 (2023-08-30)
* update Google billing client to 6.0.1
* bump kotlin to 1.8.21, Gradle plugin to 8.1.1, core-ktx to 1.10.1
* bump buildToolsVersion 34.0.0, targetSdkVersion, compileSdkVersion to 34

## 2.1.0 (2022-11-21)
* update Google billing client to 5.1.0
* bump Kotlin to 1.7.20

## 2.0.0 (2022-09-12)
* update Google billing client to 5.0.0 and adjust to all changes required to support it
* renamed all references from "sku" to "product" to reflect Google's billing client naming conventions.
* add `PriceUtil` object to simplify price formatting.
* bump Kotlin to 1.7.10, Gradle plugin to 7.2.2, core-ktx to 1.9.0
* bump buildToolsVersion to 33.0.0, compileSdkVersion and targetSdkVersion to 33

## 1.10.0 (2022-04-29)
* update Google billing client to 4.1.0
* bump Kotlin to 1.6.21, Serialization to 1.3.2, Gradle plugin to 7.1.3
* bump buildToolsVersion to 32.0.0, compileSdkVersion and targetSdkVersion to 32

## 1.9.0 (2022-02-01)
* add `isConnectionFailure` and `purchasesQueriedOrConnectionFailure`.
* fix `purchasesQueried` and `skuDetailsQueried` now change after lists get updated instead of before
* bump kotlin to 1.6.10, gradle to 7.1.0, build tools and target sdk to 31, core-ktx to 1.7.0

## 1.8.0 (2021-10-07)
* add purchase verification based on app's license key
* add option to enable logging

## 1.7.0 (2021-08-05)
* bump Gradle plugin to 7.0.0
* update publish scripts

## 1.6.0 (2021-07-08)
* `querySkuDetailsOnConnected`, `queryOwnedPurchasesOnConnected` and `autoAcknowledgePurchases`
  are now var
* fix issue where `PurchasesUpdatedListener` would replace with new purchases instead of add or update
* price change confirmation flow now uses `skuName` instead of `skuDetails`

## 1.5.0 (2021-07-08)
* use `skuInAppPurchases` and `skuSubscriptions` instead of unified `skuNames
* fix issue with concurrent queries

## 1.4.1 (2021-07-08)
* `autoAcknowledgePurchases` is now part of init config. It's true by default, so no changes in behaviour from previous versions
* `acknowledgePurchases()` is now public
* fix potential crash caused by potential fake data injection
* bump Kotlin to 1.5.20, Gradle plugin to 4.2.2

## 1.4.0 (2021-06-04)
* rename `BillingEvent.PURCHASE_ACKNOWLEDGED` to `BillingEvent.PURCHASE_ACKNOWLEDGE_SUCCESS`
* add `BillingEvent.PRICE_CHANGE_CONFIRMATION_FAILED`
* add `BillingEvent` helper methods
* add `connectionState` getter
* add `isFeatureSupported()`
* fix issue where `SkuDetails.isSubscription()` would return for in app purchase
* remove jCenter() and add mavenCentral
* bump Google Billing to 4.0.0
* bump Kotlin to 1.5.10, Gradle plugin to 4.2.1

## 1.3.3 (2021-03-21)
* make `ALL_PURCHASE_TYPES` private
* bump Google Billing API to 3.0.3
* bump Kotlin to 1.4.31, Gradle plugin tp 4.1.3, build tools to 30.0.3

## 1.3.2 (2020-12-05)
* bump Google Billing API to 3.0.2
* bump Kotlin to 1.4.20 and Gradle plugin to 4.1.1

## 1.3.1 (2020-11-04)
* disable minify for release build types so javadoc can be created

## 1.3.0 (2020-11-03)
* rename `endBillingClientConnection()` to `endClientConnection()` to stay persistent with  initClientConnection()
* add `BillingEvent.isOwnedPurchasesChange`
* add `purchasesQueried` and `skuDetailsQueried` to determine if queries have been completed yet
* add `isPurchasedAnyOf()`
* add Javadoc support
* rename `isBillingReady()`, which is now `billingReady`

## 1.2.0 (2020-10-14)
* add response code parameter to the billing listener
* add more public extensions to `Purchase` and `SkuDetails`
* add `startConnectionImmediately` to constructor
* rename `queryForSkuDetailsOnInit` to `querySkuDetailsOnConnected` and 
  `queryForOwnedPurchasesOnInit` to `queryOwnedPurchasesOnConnected`
* add option to call `initClientConnection()` manually, so it's now added as public method
* better documentation
* bump google billing to 3.0.1
* bump kotlin to 1.3.2, gradle plugin to 4.1.0

## 1.1.0 (2020-09-24)
* fix bug where cancelled flow gets reported incorrectly
* fix invoke listeners in try/catch block
* add support for `obfuscatedAccountId`, `obfuscatedProfileId` and `setVrPurchaseFlow
* bump Kotlin to 1.4.10
