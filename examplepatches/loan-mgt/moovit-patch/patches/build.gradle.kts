group = "app.revanced"

patches {
    about {
        name = "Moovit Patches"
        description = "ReVanced patches for Moovit: remove ads, unlock Moovit+, and add GmsCore support"
        author = "ReVanced"
        license = "GNU General Public License v3.0"
        source = "https://github.com/loan-mgt/revanced-patches-moovit"
        contact = "qypol342@gmail.com"
        website = "https://github.com/loan-mgt"
    }
}

// Shared GmsCore infrastructure sources are vendored directly
// under patches/src/main/kotlin/app/revanced/patches/{shared,all}/.
// These were copied from the upstream revanced-patches repo (v6.1.1-dev.4)
// and should be refreshed when updating the upstream dependency.
// Tracked: https://github.com/revanced/revanced-patches
dependencies {
    // No external dependencies needed beyond revanced-patcher (provided by the plugin)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
        )
    }
}
