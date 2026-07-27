package app.revanced.patches.dcinside

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue
import org.w3c.dom.Element

/**
 * Original (pre-R8) names of the three ad-framework classes this patch needs.
 *
 * DCInside's ad code carries no readable names (`G.a`, `com.dcinside.app.ad.support.b`, …) and R8
 * re-shuffles them every release, but the Kotlin compiler emits the ORIGINAL fully-qualified name into
 * the SMAP debug metadata (`SourceDebugExtension`), which R8 does not rewrite. Matching on that string
 * — narrowed by a structural check on the method we patch — survives the per-release renaming.
 */
private const val AD_CONFIG = "com/dcinside/app/ad/AdConfig"
private const val AD_UTIL = "com/dcinside/app/ad/support/AdUtil"
private const val AD_SEQUENCE = "com/dcinside/app/ad/support/AdSequence"

private const val LIST = "Ljava/util/List;"
private const val ACTIVITY = "Landroidx/appcompat/app/AppCompatActivity;"

/** Every string of every class annotation — in practice the SMAP blobs, which name the source class. */
private fun ClassDef.sourceDebugNames() = annotations.asSequence()
    .flatMap { it.elements }
    .flatMap { element ->
        when (val value = element.value) {
            is StringEncodedValue -> sequenceOf(value.value)
            is ArrayEncodedValue -> value.value.asSequence().mapNotNull { (it as? StringEncodedValue)?.value }
            else -> emptySequence()
        }
    }

private fun Method.parameters() = parameterTypes.map(CharSequence::toString)

private fun Method.invokes(classType: String, methodName: String) =
    implementation?.instructions?.any { instruction ->
        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
            ?.let { it.definingClass == classType && it.name == methodName } == true
    } == true

/**
 * Hides the two ad-only decorations of the gallery post list's bottom banner slot.
 *
 * `list_quick_ad_wrap` (50dp) and its `_divider` are laid out visible and are only ever *hidden* by the
 * post-list fragment (its quick-ad setup path, and the `list_quick_menu_ad_enable` branch); the banner
 * they frame is gone after [removeAdsPatch], so the wrapper would linger as an empty 50dp strip pinned
 * over the bottom of every list. Nothing in the app makes either view visible again.
 */
val removeAdsResourcePatch = resourcePatch(
    name = "Remove advertisements resources",
    description = "Collapses the empty quick-menu banner slot at the bottom of the post list.",
    use = false,
) {
    compatibleWith("com.dcinside.app.android")

    apply {
        document("res/layout/fragment_post_list.xml").use { doc ->
            // apktool decodes ids as "@id/…" while jadx shows "@+id/…" — match by suffix.
            // "list_quick_ad_wrap_bg" does not end with "list_quick_ad_wrap", so the ad container
            // itself (a child of the wrapper) is deliberately left untouched.
            val wanted = setOf("list_quick_ad_wrap", "list_quick_ad_wrap_divider")
            val hidden = mutableSetOf<String>()

            val nodes = doc.getElementsByTagName("*")
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                val id = element.getAttribute("android:id").substringAfterLast('/')
                if (id in wanted) {
                    element.setAttribute("android:visibility", "gone")
                    hidden += id
                }
            }

            check(hidden == wanted) {
                "quick-menu ad slot not found in fragment_post_list.xml (matched $hidden); " +
                    "the post list layout changed"
            }
        }
    }
}

/**
 * Removes every advertisement: the AdMob / Kakao AdFit / NASMedia AdMixer / AdPie / Cauly banners and
 * native ads, DCInside's own server-delivered WebView script ads, and the Naver PowerLink ads in the
 * feed, post-read and search screens.
 *
 * DCInside funnels all of them through one waterfall pipeline (see local/notes/com.dcinside.md):
 *
 *     RemoteConfig `<slot>_ad_*`  ->  AdConfig.selectAds(config)  ->  AdSequence(loaders, host)
 *                                                                 ->  AdSequential*.load() per network
 *
 * so the patch cuts it in two places, both of which the app already exercises when nothing fills:
 *
 *  1. [AD_CONFIG]`.selectAds` returns an EMPTY list. All 14 call sites — including the two headless
 *     reply-native loaders and the main-feed row gate — then build no loader at all, so no SDK object
 *     is constructed and no ad request is issued. `AdSequence`'s constructor takes its own
 *     `if (list.isEmpty()) return` path, which is important: it fires neither `onAdChanged` (host
 *     reserves slot height) nor `onAdSequenceConsumed` (host retries the whole waterfall on a timer),
 *     so nothing reserves space and nothing loops.
 *  2. Every ad-slot setup method — resolved structurally as "constructs an [AD_SEQUENCE]" — returns
 *     immediately. Those methods make their container visible and give it a minimum height *before*
 *     consulting the config (50dp for a list row, 103dp for a small Naver ad, 190dp for the post-read
 *     footer), so skipping them is what actually keeps the layout free of blank strips; the containers
 *     stay in the collapsed state their layout declares. Every one of them is ad-only: the feed row's
 *     page-number label and the post-read footer's own content are set elsewhere and are untouched.
 *
 * Ad-SDK initialisation ([AD_UTIL]`.adInitialize`, the single MobileAds + AdPie + AdMixer bootstrap
 * called once from `HomeActivity`) is skipped as well, so the SDKs never start or phone home.
 *
 * `AdSequence`'s own constructors and the main-feed ad holder's constructor are intentionally left
 * alone — a constructor cannot be skipped without breaking the object, and step 1 already leaves both
 * with an empty waterfall.
 */
val removeAdsPatch = bytecodePatch(
    name = "Remove advertisements",
    description = "Removes all advertisements: banners, native ads, in-feed ad rows, DCInside's own " +
        "script ads and Naver PowerLink. No ad SDK is initialised and no ad is ever requested.",
    use = true,
) {
    compatibleWith("com.dcinside.app.android")

    dependsOn(removeAdsResourcePatch)

    apply {
        fun classByOriginalName(originalName: String, what: String, declares: (ClassDef) -> Boolean): ClassDef {
            val candidates = classDefs.filter { classDef ->
                classDef.sourceDebugNames().any { originalName in it } && declares(classDef)
            }

            return candidates.singleOrNull()
                ?: error(
                    "expected exactly one $originalName ($what), found ${candidates.size} " +
                        "${candidates.map { it.type }} — the app's ad framework changed",
                )
        }

        // 1. AdConfig.selectAds(List<AdConfig>): List<AdConfig> — the weighted waterfall picker every ad
        //    slot calls. Handing back an empty list is the app's own "no ad configured" state; a fresh
        //    ArrayList is exactly what the method itself returns when nothing passes the filter, so
        //    callers that keep or copy the result behave identically.
        //    (The @JvmStatic bridge on AdConfig itself delegates here, so both entry points are covered.)
        val adConfig = classByOriginalName(AD_CONFIG, "ad slot picker") { classDef ->
            classDef.methods.any { it.parameters() == listOf(LIST) && it.returnType == LIST }
        }

        proxy(adConfig).mutableClass.methods
            .single { it.parameters() == listOf(LIST) && it.returnType == LIST }
            .addInstructions(
                0,
                """
                    new-instance v0, Ljava/util/ArrayList;
                    invoke-direct { v0 }, Ljava/util/ArrayList;-><init>()V
                    return-object v0
                """,
            )

        // 2. AdUtil.adInitialize(activity) — MobileAds + AdPieSDK + AdMixer bootstrap, called once from
        //    HomeActivity. Skipped so no ad SDK ever initialises.
        val adUtil = classByOriginalName(AD_UTIL, "ad SDK bootstrap") { classDef ->
            classDef.methods.any { it.parameters() == listOf(ACTIVITY) && it.returnType == "V" }
        }

        proxy(adUtil).mutableClass.methods
            .single { it.parameters() == listOf(ACTIVITY) && it.returnType == "V" }
            .addInstructions(0, "return-void")

        // 3. Every ad-slot setup method, i.e. everything that starts a waterfall.
        val adSequence = classByOriginalName(AD_SEQUENCE, "ad waterfall") { classDef ->
            classDef.methods.any { it.name == "<init>" && it.parameters().firstOrNull() == LIST }
        }

        val setups = classDefs.asSequence()
            .filter { it.type != adSequence.type }
            .flatMap { classDef ->
                classDef.methods.asSequence()
                    // A constructor cannot return early; step 1 leaves those waterfalls empty instead.
                    .filter { it.name != "<init>" && it.invokes(adSequence.type, "<init>") }
                    .map { classDef to it }
            }
            .toList()

        check(setups.isNotEmpty()) { "no ad slot setup found — the app's ad framework changed" }

        setups.groupBy({ it.first }, { it.second }).forEach { (classDef, methods) ->
            val mutableClass = proxy(classDef).mutableClass

            methods.forEach { method ->
                // Every known setup returns void. A non-void one would need its own return value, so
                // fail loudly instead of silently leaving an ad slot live.
                check(method.returnType == "V") {
                    "${classDef.type}->${method.name} returns ${method.returnType}; " +
                        "ad slot setup must be revisited for this app version"
                }

                mutableClass.methods
                    .single { it.name == method.name && it.parameters() == method.parameters() }
                    .addInstructions(0, "return-void")
            }
        }
    }
}
