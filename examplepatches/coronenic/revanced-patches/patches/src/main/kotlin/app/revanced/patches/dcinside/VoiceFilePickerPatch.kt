package app.revanced.patches.dcinside

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import org.w3c.dom.Element

/**
 * Adds an "upload an audio file" source to the DCInside voice-reply composer's 녹음하기 (record) tab.
 *
 * v1 (see local/notes/voice-file-picker-spec.md): the user picks an audio file; any AAC-bearing
 * container (.m4a/.mp4/.aac/…) is remuxed to the MPEG-4/AAC clip the recorder itself produces and
 * presented as a finished recording, so the existing submit path uploads it unchanged. Non-AAC input
 * is refused (transcoding is deferred to v1.1).
 *
 * Runtime logic lives in the extension (extensions/dcinside .../voice), prebuilt to
 * dcinside/voice-file-picker.rve. The composer view (com.dcinside.app.voice, obfuscated) exposes
 * getInputRecord()/getInputType() under their real Kotlin names; only the private stop/finalize
 * (which sets PLAY_STATE + reads duration) is obfuscated, so this patch injects two stable-named
 * accessors the extension calls reflectively:
 *   - revancedVoiceTarget()  -> getInputRecord() (the cache File recording writes / upload reads)
 *   - revancedVoiceFinalize() -> the view's own stop/finalize
 * and wires the upload button by calling VoiceFilePicker.attach(this) at the end of the constructor.
 */

/** Adds the upload button to the record tab and registers the picker proxy activity. */
val voiceFilePickerResourcePatch = resourcePatch(
    name = "Voice reply file upload resources",
    description = "Layout + manifest changes for the voice-reply audio file picker.",
    use = false,
) {
    compatibleWith("com.dcinside.app.android")

    apply {
        // 1. Add an upload ImageView to the left of the round record button, inside the record area.
        document("res/layout/view_voice_recorder_body.xml").use { doc ->
            val constraintLayouts = doc.getElementsByTagName("androidx.constraintlayout.widget.ConstraintLayout")
            var recordArea: Element? = null
            for (i in 0 until constraintLayouts.length) {
                val el = constraintLayouts.item(i) as Element
                if (el.getAttribute("android:id").endsWith("voice_recorder_input_record")) {
                    recordArea = el
                    break
                }
            }
            checkNotNull(recordArea) {
                "voice_recorder_input_record not found — voice composer layout changed"
            }

            val upload = doc.createElement("ImageView")
            upload.setAttribute("android:id", "@+id/voice_recorder_file_pick")
            upload.setAttribute("android:layout_width", "42dp")
            upload.setAttribute("android:layout_height", "42dp")
            upload.setAttribute("android:padding", "9dp")
            upload.setAttribute("android:scaleType", "fitCenter")
            upload.setAttribute("android:src", "@drawable/ico_folder")
            upload.setAttribute("android:tint", "?attr/icTintNormal")
            upload.setAttribute("android:background", "?android:attr/selectableItemBackgroundBorderless")
            upload.setAttribute("android:contentDescription", "@null")
            upload.setAttribute("android:layout_marginEnd", "12dp")
            upload.setAttribute("app:layout_constraintEnd_toStartOf", "@id/voice_recorder_icon")
            upload.setAttribute("app:layout_constraintTop_toTopOf", "@id/voice_recorder_icon")
            upload.setAttribute("app:layout_constraintBottom_toBottomOf", "@id/voice_recorder_icon")
            recordArea.appendChild(upload)
        }

        // 2. Register the transparent picker proxy activity (extension class).
        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as Element
            val activity = doc.createElement("activity")
            activity.setAttribute(
                "android:name",
                "app.revanced.extension.dcinside.voice.AudioPickerActivity",
            )
            activity.setAttribute("android:theme", "@android:style/Theme.Translucent.NoTitleBar")
            activity.setAttribute("android:exported", "false")
            application.appendChild(activity)
        }
    }
}

val voiceFilePickerPatch = bytecodePatch(
    name = "Voice reply file upload",
    description = "Adds an upload button to the voice-reply record tab so an existing .m4a (AAC) " +
        "audio file can be sent as a voice reply, like a recording.",
    use = true,
) {
    compatibleWith("com.dcinside.app.android")

    dependsOn(voiceFilePickerResourcePatch)

    extendWith("dcinside/voice-file-picker.rve")

    apply {
        // The composer view: keeps the real public getInputRecord():File (creates the cache "voice" file).
        val voiceClass = classBy { classDef ->
            classDef.type.startsWith("Lcom/dcinside/app/voice/") &&
                classDef.methods.any { it.name == "getInputRecord" && it.returnType == "Ljava/io/File;" }
        } ?: error("VoiceRecordView (getInputRecord) not found — voice composer changed")

        val mutableClass = voiceClass.mutableClass
        val voiceType = mutableClass.type

        // The private stop/finalize is the only void no-arg method that reads via MediaMetadataRetriever.
        val finalizeMethod = mutableClass.methods.first { m ->
            m.returnType == "V" && m.parameters.isEmpty() && m.name != "<init>" &&
                (m.implementation?.instructions?.any { insn ->
                    (insn as? ReferenceInstruction)?.reference?.toString()
                        ?.contains("MediaMetadataRetriever") == true
                } ?: false)
        }
        val finalizeName = finalizeMethod.name

        check(mutableClass.methods.none {
            it.name == "revancedVoiceTarget" || it.name == "revancedVoiceFinalize"
        }) { "voice accessors already present; patch applied twice?" }

        // File revancedVoiceTarget() { return getInputRecord(); }
        val target = ImmutableMethod(
            voiceType,
            "revancedVoiceTarget",
            emptyList(),
            "Ljava/io/File;",
            AccessFlags.PUBLIC.value,
            null,
            null,
            ImmutableMethodImplementation(2, emptyList(), null, null),
        ).let { MutableMethod(it) }
        target.addInstructions(
            0,
            """
                invoke-virtual { p0 }, $voiceType->getInputRecord()Ljava/io/File;
                move-result-object v0
                return-object v0
            """,
        )
        mutableClass.methods.add(target)

        // void revancedVoiceFinalize() { <stop/finalize>(); }  — presents the imported clip as recorded.
        val finalize = ImmutableMethod(
            voiceType,
            "revancedVoiceFinalize",
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value,
            null,
            null,
            ImmutableMethodImplementation(1, emptyList(), null, null),
        ).let { MutableMethod(it) }
        finalize.addInstructions(
            0,
            """
                invoke-direct { p0 }, $voiceType->$finalizeName()V
                return-void
            """,
        )
        mutableClass.methods.add(finalize)

        // Wire the upload button at the end of the constructor (after the layout is inflated).
        val constructor = mutableClass.methods.first {
            it.name == "<init>" &&
                it.parameterTypes.map { p -> p.toString() } == listOf("Landroid/content/Context;")
        }
        constructor.addInstructions(
            constructor.implementation!!.instructions.count() - 1,
            "invoke-static { p0 }, " +
                "Lapp/revanced/extension/dcinside/voice/VoiceFilePicker;->attach(Landroid/view/View;)V",
        )
    }
}
