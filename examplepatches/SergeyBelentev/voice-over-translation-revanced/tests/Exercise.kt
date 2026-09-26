package vot.standalone.tests

import app.revanced.patcher.patcher
import app.revanced.patcher.patch.loadPatches
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.zip.CRC32
import javax.xml.parsers.DocumentBuilderFactory

fun main(args: Array<String>) {
    val standalone = File(args[0])
    val apk = File(args[1])
    val directory = File(args[2]); directory.mkdirs()
    val official = File(standalone.parentFile.parentFile.parentFile.parentFile, "official-patches-6.2.1.rvp")
    check(official.exists()) { "Official 6.2.1 RVP not found: $official" }
    // Use the real loader with BOTH files in one classloader, just as Manager does.
    val patches = loadPatches(official, standalone) { file, error -> throw IllegalStateException("Cannot load $file", error) }
    val selected = patches.filter { patch ->
        val compatible = patch.compatiblePackages?.any { it.first == "com.google.android.youtube" && (it.second == null || "20.40.45" in it.second!!) } ?: true
        compatible && (patch.use || patch.name == "Add VOT (voice-over translation)")
    }.toSet()
    check(selected.count { it.name == "Add VOT (voice-over translation)" } == 1)
    check(selected.any { it.name == "Remove background playback restrictions" })
    check(selected.minBy { it.name!! }.name == "Add VOT (voice-over translation)") { "Unexpected official patch ordering; resource finalization needs review" }
    File(directory, "selected-patches.txt").writeText(selected.map { it.name!! }.sorted().joinToString("\n"))
    println("Selected ${selected.size} default official patches + VOT (combined total).")
    val temporary = File(System.getProperty("java.io.tmpdir"), "vot-clean-apply")
    java.util.logging.Logger.getLogger("brut.androlib.res.decoder.AXmlResourceParser").level = java.util.logging.Level.SEVERE
    val execute = patcher(apk, temporary, frameworkFileDirectory = File(directory, "framework").absolutePath, getPatches = { pkg, version ->
        check(pkg == "com.google.android.youtube" && version == "20.40.45") { "Wrong clean APK: $pkg $version" }
        selected
    })
    val failures = mutableListOf<String>()
    val result = execute { outcome ->
        if (outcome.exception != null) {
            failures.add(outcome.patch.name.toString()); outcome.exception!!.printStackTrace()
        } else println("PASS patch: ${outcome.patch.name}")
    }
    check(failures.isEmpty()) { "Patch failures: $failures" }
    val dexDirectory = File(directory, "dex"); dexDirectory.mkdirs()
    val definitions = linkedSetOf<String>()
    val methodDefinitions = linkedSetOf<String>()
    val votReferences = linkedSetOf<String>()
    var settingsHook = false
    for (dex in result.dexFiles) {
        val file = File(dexDirectory, dex.name)
        dex.stream.use { input -> file.outputStream().use { input.copyTo(it) } }
        val parsed = DexFileFactory.loadDexFile(file, Opcodes.getDefault())
        for (clazz in parsed.classes) {
            definitions.add(clazz.type)
            for (method in clazz.methods) {
                if (clazz.type == "Lapp/revanced/extension/youtube/settings/preference/YouTubePreferenceFragment;" && method.name == "onStart") {
                    val first = method.implementation!!.instructions.first() as ReferenceInstruction
                    val target = first.reference as MethodReference
                    settingsHook = target.definingClass == "Lapp/revanced/extension/youtube/vot/VotVoiceSettings;" && target.name == "install"
                }
                methodDefinitions.add("${clazz.type}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}")
                for (instruction in method.implementation?.instructions ?: emptyList()) {
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                    if (ref.definingClass.startsWith("Lapp/revanced/extension/youtube/vot/")) {
                        votReferences.add("${ref.definingClass}->${ref.name}(${ref.parameterTypes.joinToString("")})${ref.returnType}")
                    }
                }
            }
        }
    }
    for (name in listOf("VotController", "VotButton", "OriginalAudio", "PlaybackClock", "VotApi", "Proto", "AuthCallback", "VotAccount", "VotLogin", "VotVoiceSettings")) {
        check("Lapp/revanced/extension/youtube/vot/$name;" in definitions) { "Missing $name in final DEX" }
    }
    check(votReferences.isNotEmpty())
    check(settingsHook) { "Missing VOT voice settings hook in final DEX" }
    check(votReferences.all { it in methodDefinitions }) { "Unresolved VOT references: ${votReferences - methodDefinitions}" }
    val xml = File(temporary, "apk/res/layout/youtube_controls_bottom_ui_container.xml")
    val controls = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
    val views = controls.getElementsByTagName("*")
    check((0 until views.length).count { views.item(it).attributes?.getNamedItem("android:id")?.nodeValue == "@+id/revanced_vot_button" } == 1) { "Expected exactly one VOT control" }
    xml.copyTo(File(directory, "player-controls.xml"), overwrite = true)

    // Assemble an unsigned diagnostic APK from the clean input and Patcher result.
    // It is never installed. Preserve ordinary original entries and replace patched ones.
    val resources = result.resources!!
    val replacements = linkedMapOf<String, ByteArray>()
    for (file in dexDirectory.listFiles()!!) replacements[file.name] = file.readBytes()
    ZipFile(resources.resourcesApk!!).use { zip ->
        zip.entries().asSequence().filter { !it.isDirectory }.forEach { replacements[it.name] = zip.getInputStream(it).readBytes() }
    }
    resources.otherResources?.let { root ->
        root.walkTopDown().filter { it.isFile }.forEach { replacements[it.relativeTo(root).invariantSeparatorsPath] = it.readBytes() }
    }
    val assembled = File(directory, "youtube-defaults-vot-unsigned.apk")
    ZipOutputStream(assembled.outputStream().buffered()).use { out ->
        fun write(name: String, data: ByteArray, method: Int) {
            val entry = ZipEntry(name)
            entry.method = method
            if (method == ZipEntry.STORED) { entry.size = data.size.toLong(); entry.crc = CRC32().apply { update(data) }.value }
            out.putNextEntry(entry); out.write(data); out.closeEntry()
        }
        ZipFile(apk).use { original ->
            original.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                if (entry.name !in replacements && entry.name !in resources.deleteResources &&
                    !entry.name.matches(Regex("classes[0-9]*\\.dex")) &&
                    !entry.name.matches(Regex("META-INF/.*\\.(RSA|DSA|EC|SF|MF)", RegexOption.IGNORE_CASE))) {
                    write(entry.name, original.getInputStream(entry).readBytes(), entry.method)
                }
            }
        }
        replacements.forEach { (name, data) ->
            if (name !in resources.deleteResources) write(name, data, if (name == "resources.arsc" || name.endsWith(".so") || name.endsWith(".dex")) ZipEntry.STORED else ZipEntry.DEFLATED)
        }
    }
    ZipFile(assembled).use { zip ->
        check(zip.getEntry("res/drawable/revanced_vot.xml") != null)
        for (file in dexDirectory.listFiles()!!) check(zip.getInputStream(zip.getEntry(file.name)).readBytes().contentEquals(file.readBytes()))
    }
    File(directory, "verification.txt").writeText("PASS: clean YouTube 20.40.45 + official ReVanced 6.2.1 defaults + standalone VOT.\n${definitions.count { it.startsWith("Lapp/revanced/extension/youtube/vot/") }} VOT class definitions; ${votReferences.size} VOT method references all resolve.\nExactly one VOT control; voice settings hook verified; unsigned diagnostic APK assembled with verified DEX and icon.\nDevice playback and live Yandex account login not tested for 0.3.1.\n")
    println(File(directory, "verification.txt").readText())
}
