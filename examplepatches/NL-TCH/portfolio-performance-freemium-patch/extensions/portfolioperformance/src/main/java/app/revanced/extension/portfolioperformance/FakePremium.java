package app.revanced.extension.portfolioperformance;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Extension providing fake RevenueCat CustomerInfo and Offerings maps that
 * represent an active "premium" entitlement. Injected by FreemiumPatch into
 * PurchasesFlutterPlugin to unlock all premium features.
 */
@SuppressWarnings("unused")
public class FakePremium {

    /**
     * Processes a Flutter MethodChannel event payload.  If the event is one of
     * the known premium-related events, returns fake data instead of the real
     * payload; otherwise returns the original payload unchanged.
     *
     * Called from the patched static synthetic method {@code b()} which has
     * {@code .locals 0} — using this helper avoids the need for any extra
     * v-registers in the injected smali.
     *
     * @param eventName the channel event name (e.g. "Purchases-CustomerInfoUpdated")
     * @param payload   the original event payload
     * @return replacement payload (HashMap) or the original payload
     */
    public static Object processChannelPayload(String eventName, Object payload) {
        if ("Purchases-CustomerInfoUpdated".equals(eventName)) {
            return buildFakeCustomerInfo();
        } else if ("Purchases-OfferingsUpdated".equals(eventName)) {
            return buildFakeOfferings();
        }
        return payload;
    }

    /**
     * Builds a fake CustomerInfo HashMap that the RevenueCat Flutter SDK
     * accepts as a valid response. The "premium" entitlement is active with
     * an expiry date far in the future so all gating checks pass.
     *
     * @return Complete CustomerInfo-shaped HashMap ready to pass to the
     *         Flutter MethodChannel.
     */
    public static HashMap<String, Object> buildFakeCustomerInfo() {
        // --- EntitlementInfo for "premium" ---
        HashMap<String, Object> entitlementInfo = new HashMap<>();
        entitlementInfo.put("identifier",           "premium");
        entitlementInfo.put("isActive",             Boolean.TRUE);
        entitlementInfo.put("willRenew",            Boolean.TRUE);
        entitlementInfo.put("periodType",           "NORMAL");
        entitlementInfo.put("latestPurchaseDate",   "2099-01-01T00:00:00Z");
        entitlementInfo.put("originalPurchaseDate", "2023-01-01T00:00:00Z");
        entitlementInfo.put("expirationDate",       "2099-01-01T00:00:00Z");
        entitlementInfo.put("store",                "PLAY_STORE");
        entitlementInfo.put("productIdentifier",    "pp_premium_v1");
        entitlementInfo.put("productPlanIdentifier","monthly-autorenewing");
        entitlementInfo.put("isSandbox",            Boolean.FALSE);
        entitlementInfo.put("ownershipType",        "PURCHASED");
        entitlementInfo.put("verification",         "NOT_REQUESTED");

        // --- entitlements.all  { "premium" -> entitlementInfo } ---
        HashMap<String, Object> allEntitlements = new HashMap<>();
        allEntitlements.put("premium", entitlementInfo);

        // --- entitlements.active  { "premium" -> entitlementInfo } ---
        HashMap<String, Object> activeEntitlements = new HashMap<>();
        activeEntitlements.put("premium", entitlementInfo);

        // --- entitlements wrapper ---
        HashMap<String, Object> entitlements = new HashMap<>();
        entitlements.put("all",          allEntitlements);
        entitlements.put("active",       activeEntitlements);
        entitlements.put("verification", "NOT_REQUESTED");

        // --- top-level CustomerInfo ---
        HashMap<String, Object> customerInfo = new HashMap<>();
        customerInfo.put("entitlements", entitlements);

        // activeSubscriptions
        ArrayList<String> activeSubscriptions = new ArrayList<>();
        activeSubscriptions.add("pp_premium_v1");
        customerInfo.put("activeSubscriptions", activeSubscriptions);

        // allPurchasedProductIdentifiers
        ArrayList<String> allPurchased = new ArrayList<>();
        allPurchased.add("pp_premium_v1");
        customerInfo.put("allPurchasedProductIdentifiers", allPurchased);

        // Dates
        customerInfo.put("latestExpirationDate", "2099-01-01T00:00:00Z");
        customerInfo.put("requestDate",          "2099-01-01T00:00:00Z");
        customerInfo.put("firstSeen",            "2023-01-01T00:00:00Z");

        // allExpirationDates
        HashMap<String, Object> allExpirationDates = new HashMap<>();
        allExpirationDates.put("pp_premium_v1", "2099-01-01T00:00:00Z");
        customerInfo.put("allExpirationDates", allExpirationDates);

        // Empty maps required to avoid Dart's Null-is-not-Map cast crashes
        customerInfo.put("allExpirationDatesMillis",           new HashMap<>());
        customerInfo.put("allPurchaseDates",                   new HashMap<>());
        customerInfo.put("allPurchaseDatesMillis",             new HashMap<>());
        customerInfo.put("subscriptionsByProductIdentifier",   new HashMap<>());

        // nonSubscriptionTransactions (empty list)
        customerInfo.put("nonSubscriptionTransactions", new ArrayList<>());

        // Misc
        customerInfo.put("originalAppUserId",          "fake_user");
        customerInfo.put("originalApplicationVersion", "");

        return customerInfo;
    }

    /**
     * Builds a fake Offerings HashMap for the RevenueCat Flutter SDK.
     * Contains a single "premium" offering so the app sees valid offerings data.
     *
     * @return Offerings-shaped HashMap ready to pass to the Flutter MethodChannel.
     */
    public static HashMap<String, Object> buildFakeOfferings() {
        HashMap<String, Object> offering = new HashMap<>();
        offering.put("identifier",     "premium");
        offering.put("expirationDate", "2099-01-01T00:00:00Z");
        offering.put("willRenew",      Boolean.TRUE);

        HashMap<String, Object> offerings = new HashMap<>();
        offerings.put("offerings", offering);
        return offerings;
    }
}
