package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.patch.ResourcePatchContext
import app.revanced.patcher.patch.resourcePatch
import java.security.MessageDigest

private const val HERMES_ASSET_PATH = "assets/index.android.bundle"
private const val GOONDORI_HERMES_SIZE = 17_432_292

private val goondoriHermesHeader = bytes(
    0xc6, 0x1f, 0xbc, 0x03, 0xc1, 0x03, 0x19, 0x1f,
    0x62, 0x00, 0x00, 0x00,
    0xd0, 0x3a, 0xd1, 0x28, 0xd5, 0xcd, 0x88, 0xff,
    0x27, 0xfc, 0x83, 0xf0, 0xca, 0x18, 0x68, 0xa0,
    0x4a, 0xaf, 0xdb, 0xb7,
)

private data class HermesBytePatch(
    val name: String,
    val offset: Int,
    val expected: ByteArray,
    val replacement: ByteArray,
)

private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

internal fun ByteArray.updateHermesSha1() {
    val payloadSize = size - 20
    check(payloadSize > 0) { "Invalid Hermes bundle size: $size" }

    val digest = MessageDigest.getInstance("SHA-1").apply {
        update(this@updateHermesSha1, 0, payloadSize)
    }.digest()

    digest.copyInto(this, payloadSize)
}

private fun ByteArray.validateHermesBundle() {
    check(size == GOONDORI_HERMES_SIZE) {
        "Unsupported Goondori Hermes bundle size: $size"
    }
    check(copyOfRange(0, goondoriHermesHeader.size).contentEquals(goondoriHermesHeader)) {
        "Unsupported Goondori Hermes bundle header or source hash"
    }
}

private fun ByteArray.applyHermesBytePatches(vararg patches: HermesBytePatch) {
    patches.forEach { patch ->
        check(patch.expected.size == patch.replacement.size) {
            "${patch.name} must preserve the Hermes bytecode size"
        }

        val actual = copyOfRange(patch.offset, patch.offset + patch.expected.size)
        check(actual.contentEquals(patch.expected)) {
            "${patch.name} fingerprint mismatch at 0x${patch.offset.toString(16)}"
        }
    }

    patches.forEach { patch ->
        patch.replacement.copyInto(this, patch.offset)
    }
}

private fun ByteArray.hasHermesReplacement(patch: HermesBytePatch) =
    copyOfRange(patch.offset, patch.offset + patch.replacement.size).contentEquals(patch.replacement)

private fun ResourcePatchContext.updateHermesBundle(update: ByteArray.() -> Unit) {
    val bundleFile = get(HERMES_ASSET_PATH)
    val bundle = bundleFile.readBytes()

    bundle.validateHermesBundle()
    bundle.update()
    bundle.updateHermesSha1()
    bundleFile.writeBytes(bundle)
}

private fun ResourcePatchContext.applyHermesPatches(vararg patches: HermesBytePatch) =
    updateHermesBundle {
        applyHermesBytePatches(*patches)
    }

private val removeSensitiveLogsBytePatch = HermesBytePatch(
    name = "Remove Sensitive Logs",
    offset = 12_615_204,
    expected = bytes(111, 1, 2, 3, 1, 6),
    replacement = bytes(147, 1, 147, 1, 147, 1),
)

private val disableHotUpdatesBytePatches = arrayOf(
    HermesBytePatch(
        name = "Complete HotUpdater Startup",
        offset = 14_634_030,
        expected = bytes(145, 8, 154, 35, 1, 0),
        replacement = bytes(145, 8, 155, 35, 1, 0),
    ),
    HermesBytePatch(
        name = "Disable HotUpdater Effect",
        offset = 14_634_192,
        expected = bytes(133, 5, 5, 7, 10, 1, 0),
        replacement = bytes(133, 5, 5, 3, 10, 1, 0),
    ),
)

private val disableInstallReferrerBytePatch = HermesBytePatch(
    name = "Disable Install Referrer",
    offset = 10_234_022,
    expected = bytes(147, 0, 147, 1),
    replacement = bytes(147, 0, 118, 0),
)

private val disablePushRegistrationBytePatch = HermesBytePatch(
    name = "Disable Push Registration",
    offset = 10_904_785,
    expected = bytes(147, 0, 147, 1),
    replacement = bytes(147, 0, 118, 0),
)

private val hidePremiumPromotionsBytePatch = HermesBytePatch(
    name = "Hide Premium Promotions",
    offset = 10_764_267,
    expected = bytes(52, 11, 0, 59, 2, 11, 0),
    replacement = bytes(148, 0, 118, 0, 16, 0, 0),
)

private val hideHomeBenefitsBytePatch = HermesBytePatch(
    name = "Hide Home Benefits",
    offset = 10_170_993,
    expected = bytes(52, 11, 0, 137, 3, 1),
    replacement = bytes(148, 0, 118, 0, 147, 0),
)

private val hideHomeCommunityBytePatch = HermesBytePatch(
    name = "Hide Home Community",
    offset = 10_197_564,
    expected = bytes(52, 13, 0, 59, 4, 13, 0),
    replacement = bytes(148, 0, 118, 0, 16, 0, 0),
)

private val hideHomeDeliveryBytePatch = HermesBytePatch(
    name = "Hide Home Delivery",
    offset = 10_204_245,
    expected = bytes(52, 13, 0, 59, 4, 13, 0),
    replacement = bytes(148, 0, 118, 0, 16, 0, 0),
)

private val hideHomeFoodBytePatch = HermesBytePatch(
    name = "Hide Home Food Menu",
    offset = 10_213_225,
    expected = bytes(52, 8, 0, 59, 2, 8, 0),
    replacement = bytes(148, 0, 118, 0, 16, 0, 0),
)

private val hideHomeMailboxBytePatch = HermesBytePatch(
    name = "Hide Home Mailbox",
    offset = 10_220_489,
    expected = bytes(52, 9, 0, 59, 3, 9, 0),
    replacement = bytes(148, 0, 118, 0, 16, 0, 0),
)

private val hideHomeVacationBytePatch = HermesBytePatch(
    name = "Hide Home Vacation",
    offset = 10_268_166,
    expected = bytes(52, 3, 0, 59, 2, 3, 0),
    replacement = bytes(148, 0, 118, 0, 16, 0, 0),
)

private val hideHomeFeedbackBytePatch = HermesBytePatch(
    name = "Hide Home Feedback",
    offset = 10_224_982,
    expected = bytes(52, 3, 0, 59, 2, 3, 2),
    replacement = bytes(148, 0, 118, 0, 16, 0, 0),
)

private val hideMoreBenefitsBytePatch = HermesBytePatch(
    name = "Hide More Benefits",
    offset = 10_377_299,
    expected = bytes(52, 10, 0, 59, 2, 10, 0),
    replacement = bytes(148, 0, 118, 0, 16, 0, 0),
)

private val hideMoreCelebritySupportBytePatch = HermesBytePatch(
    name = "Hide More Celebrity Support",
    offset = 10_379_000,
    expected = bytes(52, 8, 0, 59, 2, 8, 0),
    replacement = bytes(148, 0, 118, 0, 16, 0, 0),
)

private val hideMoreDeliveryBytePatch = HermesBytePatch(
    name = "Hide More Delivery",
    offset = 10_379_514,
    expected = bytes(52, 9, 0, 59, 3, 9, 0),
    replacement = bytes(148, 0, 118, 0, 16, 0, 0),
)

private val hideMoreFoodMenuBytePatch = HermesBytePatch(
    name = "Hide More Food Menu",
    offset = 10_380_079,
    expected = bytes(52, 8, 0, 59, 2, 8, 0),
    replacement = bytes(148, 0, 118, 0, 16, 0, 0),
)

private val hideMoreMailboxBytePatch = HermesBytePatch(
    name = "Hide More Mailbox",
    offset = 10_471_683,
    expected = bytes(52, 13, 0, 59, 5, 13, 0),
    replacement = bytes(148, 0, 118, 0, 16, 0, 0),
)

private val moreMenuItemBytePatches = arrayOf(
    hideMoreFoodMenuBytePatch,
    hideMoreBenefitsBytePatch,
    hideMoreDeliveryBytePatch,
    hideMoreMailboxBytePatch,
    hideMoreCelebritySupportBytePatch,
)

private val hideEmptyMoreFunctionSectionBytePatches = arrayOf(
    HermesBytePatch(
        name = "Hide Cached Empty More Function Section",
        offset = 10_375_961,
        expected = bytes(94, 7, 4, 13),
        replacement = bytes(148, 7, 126, 126),
    ),
    HermesBytePatch(
        name = "Hide Newly Rendered Empty More Function Section",
        offset = 10_376_082,
        expected = bytes(16, 7, 3),
        replacement = bytes(148, 7, 126),
    ),
)

private val removeAdLayoutBytePatches = arrayOf(
    HermesBytePatch(
        name = "Preserve Dashboard Home Ad-Free Spacing",
        offset = 10_121_214,
        expected = bytes(177, 245, 0, 0, 0, 2),
        replacement = bytes(175, 245, 0, 0, 0, 126),
    ),
    HermesBytePatch(
        name = "Remove Non-Premium Banner Ad Layout",
        offset = 10_121_649,
        expected = bytes(52, 4, 0, 137, 3, 1),
        replacement = bytes(148, 0, 118, 0, 147, 0),
    ),
    HermesBytePatch(
        name = "Remove Dashboard Home FAB Banner Ad Layout",
        offset = 10_366_268,
        expected = bytes(52, 4, 0, 59, 3, 4, 0),
        replacement = bytes(148, 0, 118, 0, 16, 0, 0),
    ),
    HermesBytePatch(
        name = "Remove Community Post Banner Ad Layout",
        offset = 11_184_024,
        expected = bytes(52, 7, 0, 59, 2, 7, 0),
        replacement = bytes(148, 0, 118, 0, 16, 0, 0),
    ),
    HermesBytePatch(
        name = "Remove Community Post Fallback Ad Layout",
        offset = 11_184_188,
        expected = bytes(52, 13, 0, 59, 2, 13, 0),
        replacement = bytes(148, 0, 118, 0, 16, 0, 0),
    ),
    HermesBytePatch(
        name = "Remove Content Detail Ad Layout",
        offset = 11_241_342,
        expected = bytes(52, 7, 0, 59, 2, 7, 0),
        replacement = bytes(148, 0, 118, 0, 16, 0, 0),
    ),
)

@Suppress("unused")
val disableHotUpdatesPatch = resourcePatch(
    name = "Disable Hot Updates",
    description = "Keeps the embedded patched UI bundle active while allowing HotUpdater startup to complete normally.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        applyHermesPatches(*disableHotUpdatesBytePatches)
    }
}

internal val removeAdLayoutsPatch = resourcePatch {
    compatibleWith("com.goondori"("5.6.0"))
    dependsOn(disableHotUpdatesPatch)

    apply {
        applyHermesPatches(*removeAdLayoutBytePatches)
    }
}

private fun hermesPatch(
    name: String,
    description: String,
    bytePatch: HermesBytePatch,
) = resourcePatch(
    name = name,
    description = description,
) {
    compatibleWith("com.goondori"("5.6.0"))
    dependsOn(disableHotUpdatesPatch)

    apply {
        applyHermesPatches(bytePatch)
    }
}

private fun moreMenuPatch(
    name: String,
    description: String,
    bytePatch: HermesBytePatch,
) = resourcePatch(
    name = name,
    description = description,
) {
    compatibleWith("com.goondori"("5.6.0"))
    dependsOn(disableHotUpdatesPatch)

    apply {
        updateHermesBundle {
            applyHermesBytePatches(bytePatch)

            if (moreMenuItemBytePatches.all { hasHermesReplacement(it) }) {
                applyHermesBytePatches(*hideEmptyMoreFunctionSectionBytePatches)
            }
        }
    }
}

@Suppress("unused")
val removeSensitiveLogsPatch = hermesPatch(
    name = "Remove Sensitive Logs",
    description = "Removes the access-token console log from Goondori session initialization.",
    bytePatch = removeSensitiveLogsBytePatch,
)

@Suppress("unused")
val disableInstallReferrerPatch = hermesPatch(
    name = "Disable Install Referrer",
    description = "Stops Goondori from requesting the Google Play install-referrer value.",
    bytePatch = disableInstallReferrerBytePatch,
)

@Suppress("unused")
val disablePushRegistrationPatch = hermesPatch(
    name = "Disable Push Registration",
    description = "Stops FCM and Expo push-token registration. Push notifications will not work.",
    bytePatch = disablePushRegistrationBytePatch,
)

@Suppress("unused")
val hidePremiumPromotionsPatch = hermesPatch(
    name = "Hide Premium Promotions",
    description = "Hides the Goondori Premium entry without changing entitlement or purchase status.",
    bytePatch = hidePremiumPromotionsBytePatch,
)

@Suppress("unused")
val hideHomeBenefitsPatch = hermesPatch(
    name = "Hide Home Benefits",
    description = "Hides Benefits from the Goondori home dashboard.",
    bytePatch = hideHomeBenefitsBytePatch,
)

@Suppress("unused")
val hideHomeDeliveryPatch = hermesPatch(
    name = "Hide Home Delivery",
    description = "Hides Goondori Delivery from the home dashboard.",
    bytePatch = hideHomeDeliveryBytePatch,
)

@Suppress("unused")
val hideHomeMailboxPatch = hermesPatch(
    name = "Hide Home Mailbox",
    description = "Hides Goondori Mailbox from the home dashboard.",
    bytePatch = hideHomeMailboxBytePatch,
)

@Suppress("unused")
val hideHomeCommunityPatch = hermesPatch(
    name = "Hide Home Community",
    description = "Hides Community from the Goondori home dashboard.",
    bytePatch = hideHomeCommunityBytePatch,
)

@Suppress("unused")
val hideHomeFoodMenuPatch = hermesPatch(
    name = "Hide Home Food Menu",
    description = "Hides the Food Menu from the Goondori home dashboard.",
    bytePatch = hideHomeFoodBytePatch,
)

@Suppress("unused")
val hideHomeVacationPatch = hermesPatch(
    name = "Hide Home Vacation",
    description = "Hides Vacation from the Goondori home dashboard.",
    bytePatch = hideHomeVacationBytePatch,
)

@Suppress("unused")
val hideHomeFeedbackPatch = hermesPatch(
    name = "Hide Home Feedback",
    description = "Hides the 'How was your new home?' feedback card from the Goondori home dashboard.",
    bytePatch = hideHomeFeedbackBytePatch,
)

@Suppress("unused")
val hideMoreBenefitsPatch = moreMenuPatch(
    name = "Hide More Benefits",
    description = "Hides Benefits from the More menu.",
    bytePatch = hideMoreBenefitsBytePatch,
)

@Suppress("unused")
val hideMoreDeliveryPatch = moreMenuPatch(
    name = "Hide More Delivery",
    description = "Hides Goondori Delivery from the More menu.",
    bytePatch = hideMoreDeliveryBytePatch,
)

@Suppress("unused")
val hideMoreFoodMenuPatch = moreMenuPatch(
    name = "Hide More Food Menu",
    description = "Hides the Food Menu from the More menu.",
    bytePatch = hideMoreFoodMenuBytePatch,
)

@Suppress("unused")
val hideMoreCelebritySupportPatch = moreMenuPatch(
    name = "Hide More Celebrity Support",
    description = "Hides Celebrity Support from the More menu.",
    bytePatch = hideMoreCelebritySupportBytePatch,
)

@Suppress("unused")
val hideMoreMailboxPatch = moreMenuPatch(
    name = "Hide More Mailbox",
    description = "Hides Goondori Mailbox from the More menu.",
    bytePatch = hideMoreMailboxBytePatch,
)
