package app.revanced.patches.portfolioperformance.freemium

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val EXTENSION_CLASS = "Lapp/revanced/extension/portfolioperformance/FakePremium;"
private const val PLUGIN_CLASS = "Lcom/revenuecat/purchases_flutter/PurchasesFlutterPlugin;"
private const val CALLBACK_TYPE = "Lb4/j\$d;"
private const val CALLBACK_METHOD = "$CALLBACK_TYPE->a(Ljava/lang/Object;)V"

// ----- Fingerprints -----

internal val getCustomerInfoFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == PLUGIN_CLASS && method.name == "getCustomerInfo"
    }
}

internal val getOfferingsFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == PLUGIN_CLASS && method.name == "getOfferings"
    }
}

// public static synthetic b(PurchasesFlutterPlugin, String, Object)V
// — the Flutter MethodChannel event sender; intercept payload replacement
internal val channelSendFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == PLUGIN_CLASS &&
        method.name == "b" &&
        AccessFlags.STATIC.isSet(method.accessFlags) &&
        AccessFlags.SYNTHETIC.isSet(method.accessFlags) &&
        method.parameterTypes.size == 3
    }
}


// ----- Patch -----

@Suppress("unused")
val freemiumPatch = bytecodePatch(
    name = "Unlock premium",
    description = "Unlocks all premium features of Portfolio Performance by spoofing the RevenueCat CustomerInfo response.",
) {
    compatibleWith("software.msm.portfolio_performance")
    extendWith("extensions/extension.rve")

    execute {
        getCustomerInfoFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS->buildFakeCustomerInfo()Ljava/util/HashMap;
                move-result-object p0
                invoke-interface {p1, p0}, $CALLBACK_METHOD
                return-void
            """
        )

        getOfferingsFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS->buildFakeOfferings()Ljava/util/HashMap;
                move-result-object p0
                invoke-interface {p1, p0}, $CALLBACK_METHOD
                return-void
            """
        )

        channelSendFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $EXTENSION_CLASS->processChannelPayload(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object p2
            """
        )

    }
}
