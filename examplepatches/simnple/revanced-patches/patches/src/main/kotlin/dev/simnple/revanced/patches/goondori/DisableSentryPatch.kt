package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element

@Suppress("unused")
val disableSentryPatch = resourcePatch(
    name = "Disable Sentry",
    description = "Disables Sentry error and performance telemetry through Android manifest metadata.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as Element
            application.setMetaData("io.sentry.enabled", "false")
            application.setMetaData("io.sentry.dsn", "")
        }
    }
}
