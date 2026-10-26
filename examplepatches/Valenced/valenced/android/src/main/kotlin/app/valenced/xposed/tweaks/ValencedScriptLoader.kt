package app.valenced.xposed.tweaks

import app.valenced.xposed.ValencedConstants
import app.valenced.xposed.tweak
import app.valenced.xposed.tweaks.base.InjectorScope
import app.valenced.xposed.tweaks.base.registerScriptInjector

/**
 * Loads the Valenced bundle shipped in this module's APK. Depends on [app.valenced.xposed.tweaks.base.scriptLoader].
 *
 * Debug builds can run preloads and a development bundle from [DevLoader] instead.
 */
val valencedScriptLoader by tweak {
    registerScriptInjector { scope: InjectorScope ->
        runValencedScripts(scope)
    }
}

private fun runValencedScripts(scope: InjectorScope) {
    val log = scope.tweakLog
    log.i("Running Valenced scripts...")

    try {
        val devBundle = DevLoader.awaitBundle()

        for (f in DevLoader.preloads()) {
            log.d("Running preload: ${f.absolutePath}")
            scope.runFile(f.absolutePath)
        }

        if (devBundle != null) {
            log.i("Loading development bundle: ${devBundle.absolutePath}")
            scope.runFile(devBundle.absolutePath)
        } else {
            scope.runAsset(ValencedConstants.BUNDLE_ASSET)
        }
    } catch (e: Throwable) {
        log.e("Unable to run Valenced scripts", e)
    }
}
