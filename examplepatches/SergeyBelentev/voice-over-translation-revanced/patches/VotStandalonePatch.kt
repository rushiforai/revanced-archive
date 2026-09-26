package vot.standalone.patches

import app.revanced.patcher.firstMethod
import app.revanced.patcher.extensions.*
import app.revanced.patcher.patch.*
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

private const val PREFIX = "Lapp/revanced/extension/youtube/vot/"
private const val CONTROLLER = "${PREFIX}VotController;"
private const val BUTTON = "${PREFIX}VotButton;"
private const val VIDEO = "Lapp/revanced/extension/youtube/patches/VideoInformation;"
private const val UTILS = "Lapp/revanced/extension/shared/Utils;"
private const val TRACK = "Landroid/media/AudioTrack;"
private var bottomStubId = 0L
private var topStubId = 0L

// This dependency is applied first and finalized last with the official 6.2.1
// default selection ("Add VOT" sorts before its patches). Modify the controls only
// after the official resource patch has closed its document. The integration test
// verifies this ordering against the actual official bundle.
private val playerResourceIds = resourcePatch {
    apply {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(get("res/values/public.xml"))
        val entries = doc.getElementsByTagName("public")
        for (i in 0 until entries.length) {
            val item = entries.item(i).attributes
            if (item.getNamedItem("type").nodeValue != "id") continue
            val value = item.getNamedItem("id").nodeValue.removePrefix("0x").toLong(16)
            when (item.getNamedItem("name").nodeValue) {
                "bottom_ui_container_stub" -> bottomStubId = value
                "controls_layout_stub" -> topStubId = value
            }
        }
        if (bottomStubId == 0L || topStubId == 0L) throw PatchException("VOT: YouTube player control IDs not found")
        val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(get("AndroidManifest.xml"))
        val permissions = manifest.getElementsByTagName("uses-permission")
        if ((0 until permissions.length).none { permissions.item(it).attributes.getNamedItem("android:name")?.nodeValue == "android.permission.WAKE_LOCK" }) {
            throw PatchException("VOT: required WAKE_LOCK permission missing")
        }
    }
    afterDependents {
        document("res/layout/youtube_controls_bottom_ui_container.xml").use { doc ->
            val group = doc.getElementsByTagName("android.support.constraint.ConstraintLayout").item(0)
                ?: throw PatchException("VOT: player controls layout not found")
            val children = (0 until group.childNodes.length).mapNotNull { group.childNodes.item(it) as? Element }
            if (children.none { it.getAttribute("android:id").substringAfter('/') == "revanced_vot_button" }) {
                var anchor = "fullscreen_button"
                repeat(children.size) {
                    val next = children.firstOrNull {
                        it.getAttribute("yt:layout_constraintRight_toLeftOf").substringAfter('/') == anchor &&
                            it.getAttribute("android:id").substringAfter('/') !in setOf("bottom_end_container", "multiview_button")
                    }
                    if (next != null) anchor = next.getAttribute("android:id").substringAfter('/')
                }
                val button = doc.createElement("com.google.android.libraries.youtube.common.ui.TouchImageView")
                mapOf(
                    "android:id" to "@+id/revanced_vot_button", "style" to "@style/YouTubePlayerButton",
                    "android:layout_width" to "48.0dip", "android:layout_height" to "60.0dip",
                    "android:paddingTop" to "6dp", "android:paddingBottom" to "0dp",
                    "android:scaleType" to "center", "android:contentDescription" to "VOT — перевод видео. Удерживайте для настройки.",
                    "android:src" to "@drawable/revanced_vot",
                    "yt:layout_constraintBottom_toTopOf" to "@id/quick_actions_container",
                    "yt:layout_constraintRight_toLeftOf" to "@id/$anchor",
                ).forEach { (key, value) -> button.setAttribute(key, value) }
                group.appendChild(button)
                children.filter { it.getAttribute("android:id").substringAfter('/') in setOf("bottom_end_container", "multiview_button") }
                    .forEach { it.setAttribute("yt:layout_constraintRight_toLeftOf", "@id/revanced_vot_button") }
            }
        }
        val icon = get("res/drawable/revanced_vot.xml")
        icon.parentFile.mkdirs()
        icon.writeText("""<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFFFF" android:pathData="M2,5h3l3,10 3,-10h3L9.5,19h-3zM14,5h9v3h-3v11h-3V8h-3z"/></vector>""")
    }
}

private fun Instruction.methodRef() = (this as? ReferenceInstruction)?.reference as? MethodReference
private fun Method.code() = implementation?.instructions?.toList().orEmpty()
private fun Method.signature() = "$name(${parameterTypes.joinToString("")})$returnType"
private fun Method.hasLiteral(value: Long) = code().any { (it as? WideLiteralInstruction)?.wideLiteral == value }
private fun Method.hasString(value: String) = code().any { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == value }

private fun BytecodePatchContext.requireMethod(owner: String, signature: String): Method =
    classDefs[owner]?.methods?.singleOrNull { it.signature() == signature }
        ?: throw PatchException("VOT: required ReVanced 6.2.1 API missing: $owner->$signature. Select the official YouTube patches together with VOT.")

private fun BytecodePatchContext.hookStaticEnd(owner: String, signature: String, target: String, registers: String) {
    val source = requireMethod(owner, signature)
    if (source.accessFlags and AccessFlags.STATIC.value == 0) throw PatchException("VOT: expected static API: $signature")
    val returns = source.code().mapIndexedNotNull { i, op -> i.takeIf { op.opcode == Opcode.RETURN_VOID } }
    if (returns.isEmpty()) throw PatchException("VOT: API has no implementation: $signature")
    val method = firstMethod(source)
    for (index in returns.asReversed()) method.addInstruction(index, "invoke-static/range { $registers }, $target")
}

private fun BytecodePatchContext.verifyExtension() {
    for (name in listOf("VotController", "VotButton", "VotApi", "Proto", "PlaybackClock", "OriginalAudio", "AuthCallback", "VotAccount", "VotLogin", "VotVoiceSettings")) {
        if (classDefs["$PREFIX$name;"] == null) throw PatchException("VOT: native extension missing: $name")
    }
    // Check actual method definitions, not just DEX strings. This catches the original crash.
    for (clazz in classDefs.filter { it.type.startsWith(PREFIX) }) {
        for (method in clazz.methods) for (instruction in method.code()) {
            val ref = instruction.methodRef() ?: continue
            if (!ref.definingClass.startsWith("Lapp/revanced/extension/")) continue
            val signature = "${ref.name}(${ref.parameterTypes.joinToString("")})${ref.returnType}"
            requireMethod(ref.definingClass, signature)
        }
    }
}

/** The sole public Patch export. Shared ReVanced extension/classes are never bundled. */
@Suppress("unused")
val voiceOverTranslationPatch = bytecodePatch(
    name = "Add VOT (voice-over translation)",
    description = "Standalone VOT add-on for official ReVanced 6.2.1 and YouTube 20.40.45. Select the official patches, including background playback, in the same run.",
    use = false,
) {
    compatibleWith("com.google.android.youtube"("20.40.45"), "app.revanced.android.youtube"("20.40.45"))
    dependsOn(playerResourceIds)
    // Unique path prevents classloader resource collisions with extensions/youtube.rve.
    extendWith("standalone-vot/translation.rve")

    afterDependents {
        // All selected patches have applied by this phase, irrespective of source ordering.
        verifyExtension()
        val methods = classDefs.filter { !it.type.startsWith(PREFIX) }.flatMap { it.methods.toList() }

        // Remove obsolete controller/button hooks from the first experimental bundle.
        // AudioTrack wrappers must stay: removing them would remove actual audio operations.
        for (source in methods) {
            val old = source.code().mapIndexedNotNull { index, instruction ->
                val ref = instruction.methodRef()
                index.takeIf { ref?.definingClass in listOf(CONTROLLER, BUTTON) && instruction.opcode in listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) }
            }
            if (old.isNotEmpty()) {
                val mutable = firstMethod(source)
                for (index in old.asReversed()) mutable.removeInstruction(index)
            }
        }

        hookStaticEnd(VIDEO, "setVideoId(Ljava/lang/String;)V", "$CONTROLLER->onVideoId(Ljava/lang/String;)V", "p0 .. p0")
        hookStaticEnd(VIDEO, "setVideoTime(J)V", "$CONTROLLER->onTime(J)V", "p0 .. p1")
        hookStaticEnd(VIDEO, "videoSpeedChanged(F)V", "$CONTROLLER->onSpeed(F)V", "p0 .. p0")
        hookStaticEnd(VIDEO, "userSelectedPlaybackSpeed(F)V", "$CONTROLLER->onSpeed(F)V", "p0 .. p0")

        // At onStart entry p0 is still the fragment; the screen has already been created.
        // Do not inject at returns: optimized catch blocks may reuse parameter registers.
        val settings = requireMethod("Lapp/revanced/extension/youtube/settings/preference/YouTubePreferenceFragment;", "onStart()V")
        if (settings.accessFlags and AccessFlags.STATIC.value != 0) throw PatchException("VOT: expected settings fragment instance")
        firstMethod(settings).addInstruction(0, "invoke-static/range { p0 .. p0 }, ${PREFIX}VotVoiceSettings;->install(Landroid/preference/PreferenceFragment;)V")

        val controllerClass = classDefs.singleOrNull { clazz -> clazz.methods.any { it.hasString("playVideo called on player response with no videoStreamingData.") } }
            ?: throw PatchException("VOT: YouTube player controller not found")
        val state = controllerClass.methods.singleOrNull { method ->
            val parameter = method.parameterTypes.singleOrNull()?.toString()
            method.returnType == "V" && parameter != null && classDefs[parameter]?.superclass == "Ljava/lang/Enum;" &&
                method.accessFlags and AccessFlags.STATIC.value == 0 &&
                method.code().any { it.methodRef()?.let { ref -> ref.name == "plus" && ref.definingClass == "Lj$/time/Instant;" } == true }
        } ?: throw PatchException("VOT: playback stage callback not found")
        firstMethod(state).addInstruction(0, "invoke-static/range { p1 .. p1 }, $CONTROLLER->onState(Ljava/lang/Enum;)V")

        val currentMethods = classDefs.filter { !it.type.startsWith("Lapp/revanced/extension/") }.flatMap { it.methods.toList() }
        val bottom = currentMethods.singleOrNull { it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/Object;" && it.hasLiteral(bottomStubId) }
            ?: throw PatchException("VOT: bottom controls inflation not found")
        val bottomCode = bottom.code()
        val inflate = bottomCode.indexOfFirst { it.methodRef()?.let { ref -> ref.name == "inflate" && ref.definingClass == "Landroid/view/ViewStub;" } == true }
        val result = bottomCode.getOrNull(inflate + 1) as? OneRegisterInstruction
        if (inflate < 0 || result?.opcode != Opcode.MOVE_RESULT_OBJECT) throw PatchException("VOT: unexpected player inflation code")
        firstMethod(bottom).addInstruction(inflate + 2, "invoke-static/range { v${result.registerA} .. v${result.registerA} }, $BUTTON->initializeButton(Landroid/view/View;)V")
        val top = currentMethods.singleOrNull { it.parameterTypes.isEmpty() && it.returnType == "V" && it.hasLiteral(topStubId) }
            ?: throw PatchException("VOT: top controls inflation not found")
        val visibility = classDefs[top.definingClass]!!.methods.singleOrNull {
            it.parameterTypes == listOf("Z", "Z") && it.returnType == "V" && it.accessFlags and AccessFlags.PRIVATE.value != 0
        } ?: throw PatchException("VOT: player controls visibility callback not found")
        firstMethod(visibility).addInstruction(0, "invoke-static/range { p1 .. p2 }, $BUTTON->setVisibility(ZZ)V")
        val controls = "Lapp/revanced/extension/youtube/patches/PlayerControlsPatch;"
        hookStaticEnd(controls, "fullscreenButtonVisibilityChanged(Z)V", "$BUTTON->setVisibilityImmediate(Z)V", "p0 .. p0")
        firstMethod(requireMethod(controls, "fullscreenButtonVisibilityCallbacksExist()Z")).addInstructions(0, "const/4 v0, 0x1\nreturn v0")

        val audioSignatures = setOf("play()V", "pause()V", "stop()V", "release()V", "setVolume(F)I", "setStereoVolume(FF)I")
        val found = mutableSetOf<String>()
        for (source in currentMethods) {
            val changes = source.code().mapIndexedNotNull { index, instruction ->
                val ref = instruction.methodRef() ?: return@mapIndexedNotNull null
                val signature = "${ref.name}(${ref.parameterTypes.joinToString("")})${ref.returnType}"
                if (ref.definingClass == "${PREFIX}OriginalAudio;") { found.add(ref.name); return@mapIndexedNotNull null }
                if (ref.definingClass != TRACK || signature !in audioSignatures) return@mapIndexedNotNull null
                if (instruction.opcode !in listOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE)) return@mapIndexedNotNull null
                found.add(ref.name)
                val invoke = when (instruction) {
                    is RegisterRangeInstruction -> "invoke-static/range { v${instruction.startRegister} .. v${instruction.startRegister + instruction.registerCount - 1} }"
                    is FiveRegisterInstruction -> "invoke-static { " + listOf(instruction.registerC, instruction.registerD, instruction.registerE, instruction.registerF, instruction.registerG).take(instruction.registerCount).joinToString(", ") { "v$it" } + " }"
                    else -> throw PatchException("VOT: unsupported AudioTrack instruction")
                }
                index to "$invoke, ${PREFIX}OriginalAudio;->${ref.name}($TRACK${ref.parameterTypes.joinToString("")})${ref.returnType}"
            }
            if (changes.isNotEmpty()) {
                val mutable = firstMethod(source)
                for ((index, replacement) in changes.asReversed()) mutable.replaceInstruction(index, replacement)
            }
        }
        if (!found.containsAll(listOf("play", "pause", "setVolume"))) throw PatchException("VOT: required background audio hooks not found")
        verifyExtension()
    }
}
