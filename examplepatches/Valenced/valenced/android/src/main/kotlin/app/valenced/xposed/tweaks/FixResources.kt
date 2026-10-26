package app.valenced.xposed.tweaks

import android.content.res.Resources
import app.valenced.xposed.ValencedConstants
import app.valenced.xposed.hook
import app.valenced.xposed.method
import app.valenced.xposed.tweak

/**
 * Hooks [Resources.getIdentifier] to rewrite the package name to `com.discord` when the host app was repackaged.
 */
val fixResources by tweak {
    val hostPkg = appInfo.packageName
    if (hostPkg == ValencedConstants.TARGET_PACKAGE) return@tweak

    Resources::class.java.method(
        "getIdentifier",
        String::class.java,
        String::class.java,
        String::class.java,
    ).hook {
        before {
            if (args[2] == hostPkg) args[2] = ValencedConstants.TARGET_PACKAGE
        }
    }
}
