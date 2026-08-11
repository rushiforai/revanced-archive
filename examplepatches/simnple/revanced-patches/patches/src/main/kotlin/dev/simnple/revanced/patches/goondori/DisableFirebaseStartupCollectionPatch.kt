package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element

@Suppress("unused")
val disableFirebaseStartupCollectionPatch = resourcePatch(
    name = "Disable Firebase Startup Collection",
    description = "Disables Firebase Analytics collection and advertising-personalization signals at startup.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as Element
            application.setMetaData("firebase_analytics_collection_enabled", "false")
            application.setMetaData("firebase_analytics_collection_deactivated", "true")
            application.setMetaData("google_analytics_adid_collection_enabled", "false")
            application.setMetaData("google_analytics_default_allow_ad_personalization_signals", "false")
        }
    }
}
