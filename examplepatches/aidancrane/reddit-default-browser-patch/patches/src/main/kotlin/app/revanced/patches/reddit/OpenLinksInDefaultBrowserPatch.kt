// SPDX-FileCopyrightText: 2026 Aidan
// SPDX-License-Identifier: MIT OR GPL-3.0-only

package app.revanced.patches.reddit

import app.revanced.patcher.firstMethod
import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.extensions.string
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val EXTENSION_METHOD =
    "Lapp/revanced/extension/reddit/DefaultBrowser;->open(Landroid/app/Activity;Landroid/net/Uri;)Z"
private const val CONTEXT_URL_EXTENSION_METHOD =
    "Lapp/revanced/extension/reddit/DefaultBrowser;->open(Landroid/content/Context;Ljava/lang/String;)Z"

@Suppress("unused")
val openLinksInDefaultBrowserPatch = bytecodePatch(
    name = "Open links in default browser",
    description = "Opens web links from Reddit in Android's default external browser.",
) {
    compatibleWith("com.reddit.frontpage"("2026.15.1"))

    extendWith("extensions/extension.rve")

    apply {
        val screenNavigatorMethod = firstMethod {
            returnType == "V" &&
                AccessFlags.PUBLIC.isSet(accessFlags) &&
                AccessFlags.FINAL.isSet(accessFlags) &&
                parameterTypes.size == 2 &&
                parameterTypes[0] == "Landroid/app/Activity;" &&
                parameterTypes[1] == "Landroid/net/Uri;"
        }

        val uriCheckIndex = screenNavigatorMethod.implementation!!.instructions
            .indexOfFirst { it.string == "uri" }
            .also { require(it >= 0) { "Could not locate the URI parameter check" } }
        val insertIndex = uriCheckIndex + 2

        screenNavigatorMethod.addInstructionsWithLabels(
            insertIndex,
            """
                invoke-static {p1, p2}, $EXTENSION_METHOD
                move-result v0
                if-eqz v0, :continue_original
                return-void
            """.trimIndent(),
            ExternalLabel("continue_original", screenNavigatorMethod.getInstruction(insertIndex)),
        )

        // Full-bleed-player posts use DeepLinkNavigatorImpl.openInAppBrowser(Context, String,
        // Boolean) directly, bypassing the Activity/Uri navigator hook above.
        val openInAppBrowserMethod = firstMethod {
            returnType == "V" &&
                AccessFlags.PUBLIC.isSet(accessFlags) &&
                AccessFlags.FINAL.isSet(accessFlags) &&
                parameterTypes.size == 3 &&
                parameterTypes[0] == "Landroid/content/Context;" &&
                parameterTypes[1] == "Ljava/lang/String;" &&
                parameterTypes[2] == "Z"
        }

        openInAppBrowserMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static {p1, p2}, $CONTEXT_URL_EXTENSION_METHOD
                move-result v0
                if-eqz v0, :continue_in_app_browser
                return-void
            """.trimIndent(),
            ExternalLabel("continue_in_app_browser", openInAppBrowserMethod.getInstruction(0)),
        )

        // Link thumbnails in post detail decide whether to use ACTION_VIEW or launch the
        // full-bleed player before either navigator above is called. Force the existing
        // ACTION_VIEW branch for this handler so Android sends the URL to the default browser.
        val linkThumbnailHandlerMethod = firstMethod {
            definingClass ==
                "Lcom/reddit/postdetail/refactor/events/handlers/postunit/" +
                "PostUnitLinkThumbnailClickEventHandler${'$'}handleEvent${'$'}2;" &&
                name == "invokeSuspend" &&
                returnType == "Ljava/lang/Object;"
        }

        val preferenceResultIndices = linkThumbnailHandlerMethod.implementation!!.instructions
            .mapIndexedNotNull { index, instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference?.toString()
                if (reference == "Lcom/reddit/account/repository/c;->D()Z") index + 1 else null
            }
        require(preferenceResultIndices.isNotEmpty()) {
            "Could not locate Reddit's external-browser preference checks"
        }

        preferenceResultIndices.asReversed().forEach { moveResultIndex ->
            val resultRegister =
                linkThumbnailHandlerMethod.getInstruction<OneRegisterInstruction>(moveResultIndex).registerA
            linkThumbnailHandlerMethod.addInstruction(moveResultIndex + 1, "const/4 v$resultRegister, 0x1")
        }

        // Tapping a link post's title/body dispatches a container click rather than a
        // thumbnail click. It has an independent copy of the same preference gate.
        val linkContainerHandlerMethod = firstMethod {
            definingClass ==
                "Lcom/reddit/postdetail/refactor/events/handlers/postunit/" +
                "PostUnitContainerClickEventHandler${'$'}handleEvent${'$'}2;" &&
                name == "invokeSuspend" &&
                returnType == "Ljava/lang/Object;"
        }

        val containerPreferenceResultIndices = linkContainerHandlerMethod.implementation!!.instructions
            .mapIndexedNotNull { index, instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference?.toString()
                if (reference == "Lcom/reddit/account/repository/c;->D()Z") index + 1 else null
            }
        require(containerPreferenceResultIndices.isNotEmpty()) {
            "Could not locate Reddit's title external-browser preference check"
        }

        containerPreferenceResultIndices.asReversed().forEach { moveResultIndex ->
            val resultRegister =
                linkContainerHandlerMethod.getInstruction<OneRegisterInstruction>(moveResultIndex).registerA
            linkContainerHandlerMethod.addInstruction(moveResultIndex + 1, "const/4 v$resultRegister, 0x1")
        }

        // Subreddit/home-feed link cards are handled by OnClickPostLinkEventHandler. Its
        // thumbnail route can otherwise enter the full-bleed player before reaching either
        // post-detail handler above. Force this handler's existing external-browser gate too.
        val feedLinkHandlerMethod = firstMethod {
            definingClass == "Lcom/reddit/feeds/impl/ui/actions/g0;" &&
                name == "d" &&
                returnType == "Ljava/lang/Object;"
        }

        val feedPreferenceResultIndices = feedLinkHandlerMethod.implementation!!.instructions
            .mapIndexedNotNull { index, instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference?.toString()
                if (reference == "Lcom/reddit/account/repository/c;->D()Z") index + 1 else null
            }
        require(feedPreferenceResultIndices.isNotEmpty()) {
            "Could not locate Reddit's feed external-browser preference check"
        }

        feedPreferenceResultIndices.asReversed().forEach { moveResultIndex ->
            val resultRegister =
                feedLinkHandlerMethod.getInstruction<OneRegisterInstruction>(moveResultIndex).registerA
            feedLinkHandlerMethod.addInstruction(moveResultIndex + 1, "const/4 v$resultRegister, 0x1")
        }
    }
}
