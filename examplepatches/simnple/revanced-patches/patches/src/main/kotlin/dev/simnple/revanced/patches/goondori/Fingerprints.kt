package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.fingerprint

private const val FIREBASE_ANALYTICS = "Lcom/google/firebase/analytics/FirebaseAnalytics;"
private const val ADROP_METRICS_MODULE = "Lio/adrop/AdropMetricsModule;"
private const val PAIRIP_LICENSE_CLIENT = "Lcom/pairip/licensecheck/LicenseClient;"

internal fun exactVoidMethodFingerprint(
    classDescriptor: String,
    methodName: String,
    parameterTypes: List<String>,
) = exactMethodFingerprint(classDescriptor, methodName, parameterTypes, "V")

internal fun exactMethodFingerprint(
    classDescriptor: String,
    methodName: String,
    parameterTypes: List<String>,
    returnType: String,
) = fingerprint {
    custom { method, classDef ->
        classDef.type == classDescriptor &&
            method.name == methodName &&
            method.returnType == returnType &&
            method.parameterTypes.map(CharSequence::toString) == parameterTypes
    }
}

internal val firebaseLogEventFingerprint = exactVoidMethodFingerprint(
    FIREBASE_ANALYTICS,
    "logEvent",
    listOf("Ljava/lang/String;", "Landroid/os/Bundle;"),
)

internal val firebaseSetUserIdFingerprint = exactVoidMethodFingerprint(
    FIREBASE_ANALYTICS,
    "setUserId",
    listOf("Ljava/lang/String;"),
)

internal val firebaseSetUserPropertyFingerprint = exactVoidMethodFingerprint(
    FIREBASE_ANALYTICS,
    "setUserProperty",
    listOf("Ljava/lang/String;", "Ljava/lang/String;"),
)

internal val firebaseSetDefaultEventParametersFingerprint = exactVoidMethodFingerprint(
    FIREBASE_ANALYTICS,
    "setDefaultEventParameters",
    listOf("Landroid/os/Bundle;"),
)

internal val adropSetPropertyFingerprint = exactVoidMethodFingerprint(
    ADROP_METRICS_MODULE,
    "setProperty",
    listOf("Ljava/lang/String;", "Lcom/facebook/react/bridge/ReadableArray;"),
)

internal val adropSetUidFingerprint = exactVoidMethodFingerprint(
    "Lio/adrop/AdropAdsModule;",
    "setUID",
    listOf("Ljava/lang/String;"),
)

internal val adropInitializeFingerprint = exactVoidMethodFingerprint(
    "Lio/adrop/AdropAdsModule;",
    "initialize",
    listOf("Z", "Lcom/facebook/react/bridge/ReadableArray;", "Z"),
)

internal val adropLogEventFingerprint = exactVoidMethodFingerprint(
    ADROP_METRICS_MODULE,
    "logEvent",
    listOf("Ljava/lang/String;", "Lcom/facebook/react/bridge/ReadableMap;"),
)

internal val adropSendEventFingerprint = exactVoidMethodFingerprint(
    ADROP_METRICS_MODULE,
    "sendEvent",
    listOf("Ljava/lang/String;", "Lcom/facebook/react/bridge/ReadableMap;"),
)

internal val pairipLocalInstallerCheckFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == PAIRIP_LICENSE_CLIENT &&
            method.name == "performLocalInstallerCheck" &&
            method.returnType == "Z" &&
            method.parameterTypes.isEmpty()
    }
}

internal val firebaseLoggingSendFingerprint = exactMethodFingerprint(
    "Lcom/google/android/datatransport/cct/CctTransportBackend;",
    "send",
    listOf("Lcom/google/android/datatransport/runtime/backends/BackendRequest;"),
    "Lcom/google/android/datatransport/runtime/backends/BackendResponse;",
)

internal val firebaseRemoteConfigUrlFingerprint = exactMethodFingerprint(
    "Lcom/google/firebase/remoteconfig/internal/ConfigFetchHttpClient;",
    "getFetchUrl",
    listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    "Ljava/lang/String;",
)

internal val fundingChoicesUrlFingerprint = exactMethodFingerprint(
    "Lcom/google/android/gms/internal/consent_sdk/zzw;",
    "zzd",
    listOf("Lcom/google/android/gms/internal/consent_sdk/zzcl;"),
    "Lcom/google/android/gms/internal/consent_sdk/zzcn;",
)

internal val revenueCatPurchasesFactoryUrlFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/revenuecat/purchases/PurchasesFactory;" &&
            method.name == "createPurchases\$default" &&
            method.returnType == "Lcom/revenuecat/purchases/Purchases;"
    }
}

internal val revenueCatAppConfigUrlFingerprint = exactMethodFingerprint(
    "Lcom/revenuecat/purchases/common/AppConfig;",
    "<init>",
    listOf(
        "Landroid/content/Context;",
        "Lcom/revenuecat/purchases/PurchasesAreCompletedBy;",
        "Z",
        "Lcom/revenuecat/purchases/common/PlatformInfo;",
        "Ljava/net/URL;",
        "Lcom/revenuecat/purchases/Store;",
        "Z",
        "Lcom/revenuecat/purchases/APIKeyValidator\$ValidationResult;",
        "Lcom/revenuecat/purchases/DangerousSettings;",
        "Z",
        "Z",
        "Ljava/lang/String;",
        "I",
        "Lkotlin/jvm/internal/DefaultConstructorMarker;",
    ),
    "V",
)

internal val revenueCatRemoteConfigUrlFingerprint = exactVoidMethodFingerprint(
    "Lcom/revenuecat/purchases/common/remoteconfig/DefaultRemoteConfigSourceProvider;",
    "<clinit>",
    emptyList(),
)
