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
 * Each reader calls `getPackageManager()` **virtually on whatever Context it is handed**, so we
 * override `getPackageManager()` on that Context's class to return a [SpoofPackageManager] whose
 * `getPackageInfo(...)` injects the ORIGINAL certificate bytes (raw bytes, not a precomputed hash,
 * so every consumer recomputes the correct digest even if DCInside changes the algorithm):
 *  - Almost every reader uses the app Context (`Application.m74553g()` == `getApplicationContext()`
 *    == the Application instance), covered by overriding `Application.getPackageManager()`.
 *  - The post-submit gate `PostWriteActivity.m99308Za` is the lone exception: it hashes the cert
 *    from its own Activity (`Application.sh(this)`). `Activity.getPackageManager()` never dispatches
 *    to `Application.getPackageManager()`, so `PostWriteActivity.getPackageManager()` is hooked too —
 *    otherwise submitting a post intermittently fails with an "error" popup.
 *
 * The proxy + spoof logic live in the extension (extensions/dcinside), prebuilt to
 * `dcinside/signature-spoof.rve` and merged by [extendWith].
 */
val spoofSignaturePatch = bytecodePatch(
    name = "Spoof signature",
    description = "Presents the original signing certificate to the app's own tamper checks " +
        "so the re-signed build passes client- and server-side verification " +
        "(fixes the blank 실시간 베스트 feed, error 2109, and the intermittent \"error\" popup on post submission).",
    use = true,
) {
    compatibleWith("com.dcinside.app.android")

    extendWith("dcinside/signature-spoof.rve")

    apply {
        val spoof = "Lapp/revanced/extension/dcinside/SignatureSpoof;"

        // Add a getPackageManager() override that wraps the real PackageManager so the app's own
        // getPackageInfo(..., GET_SIGNATURES) reads the ORIGINAL certificate. Both signature readers
        // (native sh()/gt() and Kotlin AppSignatureVerifier) call getPackageManager() *virtually on
        // the Context they are handed*, so an override on that Context's class is dispatched to.
        //
        //   public PackageManager getPackageManager() {
        //       return SignatureSpoof.wrap(super.getPackageManager());
        //   }
        fun hookGetPackageManager(classType: String, frameworkSuperType: String) {
            val clazz = classBy { it.type == classType }
                ?: error("$classType not found — target app changed; re-verify the choke point")
            val mutableClass = clazz.mutableClass

            // The class must not already declare getPackageManager(); if a future version does,
            // fail loudly instead of silently double-defining / mis-wiring the override.
            check(mutableClass.methods.none { it.name == "getPackageManager" && it.parameters.isEmpty() }) {
                "$classType already declares getPackageManager(); " +
                    "spoof wiring must be revisited for this app version"
            }

            val getPackageManager = ImmutableMethod(
                classType,
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
                    invoke-super { p0 }, $frameworkSuperType->getPackageManager()Landroid/content/pm/PackageManager;
                    move-result-object v0
                    invoke-static { v0 }, $spoof->wrap(Landroid/content/pm/PackageManager;)Landroid/content/pm/PackageManager;
                    move-result-object v0
                    return-object v0
                """,
            )

            mutableClass.methods.add(getPackageManager)
        }

        // App-Context path: the native sh()/gt() readers and the Kotlin AppSignatureVerifier
        // (com.dcinside.app.auth.b) all read the cert via getApplicationContext(), i.e. the
        // Application instance. Application.getApplicationContext() returns `this`, so overriding
        // Application.getPackageManager() covers every app-context read in one hook.
        hookGetPackageManager("Lcom/dcinside/app/Application;", "Landroid/app/Application;")

        // Post-submit path: PostWriteActivity.m99308Za (the write-time signature gate) is the ONE
        // reader that hashes the cert from the *Activity* context — Application.sh(this) — not the
        // app context. Activity.getPackageManager() never dispatches to Application.getPackageManager(),
        // so without this second hook the re-signed cert leaks through and the gate rejects the post
        // with a generic "error" popup (intermittently: the verification type is chosen at random and
        // only some types carry a non-empty native whitelist that trips the check).
        hookGetPackageManager("Lcom/dcinside/app/write/PostWriteActivity;", "Landroid/app/Activity;")
    }
}
