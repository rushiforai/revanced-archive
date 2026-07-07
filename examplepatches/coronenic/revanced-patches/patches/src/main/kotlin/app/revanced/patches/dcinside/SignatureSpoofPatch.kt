package app.revanced.patches.dcinside

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

/**
 * Defeats DCInside's signing-certificate anti-tamper so the ReVanced-rebuilt (re-signed)
 * app is accepted by both the client-side check and the server (`msign.dcinside.com`
 * `mobile_app_verification`, error 2109 "service id mismatched").
 *
 * Root cause (see local/notes/com.dcinside.md): the app reads its own signing cert via
 *   context.getPackageManager().getPackageInfo(pkg, GET_SIGNATURES).signatures[0]
 * both natively (libnative-lib.so: JNI `sh()`/`gt()` -> SHA-256 -> POST to msign) and in
 * Kotlin (AppSignatureVerifier / MainContentHelper gate). Re-signing changes that cert, so
 * every check fails and the live-best feed is blanked / realtime is rejected.
 *
 * Both readers call `getPackageManager()` **virtually on the app Context**
 * (`Application.m74553g()` == `getApplicationContext()` == the Application instance). We add
 * `Application.getPackageManager()` returning a [SpoofPackageManager] whose
 * `getPackageInfo(...)` injects the ORIGINAL certificate bytes. Feeding raw bytes (not a
 * precomputed hash) is future-proof: every consumer recomputes the correct digest even if
 * DCInside changes the algorithm.
 *
 * The proxy + spoof logic live in the extension (extensions/dcinside), prebuilt to
 * `dcinside/signature-spoof.rve` and merged by [extendWith].
 */
val spoofSignaturePatch = bytecodePatch(
    name = "Spoof signature",
    description = "Presents the original signing certificate to the app's own tamper checks " +
        "so the re-signed build passes client- and server-side verification " +
        "(fixes the blank 실시간 베스트 feed and error 2109).",
    use = true,
) {
    compatibleWith("com.dcinside.app.android")

    extendWith("dcinside/signature-spoof.rve")

    apply {
        val applicationType = "Lcom/dcinside/app/Application;"
        val spoof = "Lapp/revanced/extension/dcinside/SignatureSpoof;"

        val application = classBy { it.type == applicationType }
            ?: error("$applicationType not found — target app changed; re-verify the choke point")
        val mutableApplication = application.mutableClass

        // The app must not already declare getPackageManager(); if a future version does,
        // fail loudly instead of silently double-defining / mis-wiring the override.
        check(mutableApplication.methods.none { it.name == "getPackageManager" && it.parameters.isEmpty() }) {
            "com.dcinside.app.Application already declares getPackageManager(); " +
                "spoof wiring must be revisited for this app version"
        }

        // public PackageManager getPackageManager() {
        //     return SignatureSpoof.wrap(super.getPackageManager());
        // }
        val getPackageManager = ImmutableMethod(
            applicationType,
            "getPackageManager",
            emptyList(),
            "Landroid/content/pm/PackageManager;",
            AccessFlags.PUBLIC.value,
            null,
            null,
            ImmutableMethodImplementation(2, emptyList(), null, null),
        ).let { MutableMethod(it) }

        getPackageManager.addInstructions(
            0,
            """
                invoke-super { p0 }, Landroid/app/Application;->getPackageManager()Landroid/content/pm/PackageManager;
                move-result-object v0
                invoke-static { v0 }, $spoof->wrap(Landroid/content/pm/PackageManager;)Landroid/content/pm/PackageManager;
                move-result-object v0
                return-object v0
            """,
        )

        mutableApplication.methods.add(getPackageManager)
    }
}
