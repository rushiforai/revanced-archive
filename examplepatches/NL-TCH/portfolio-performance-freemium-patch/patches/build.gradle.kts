version = "2.0.0"
group = "app.revanced"

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "app.revanced" && requested.name == "revanced-patcher") {
            useTarget("app.revanced:patcher:${requested.version}")
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexplicit-backing-fields",
            "-Xcontext-parameters"
        )
    }
}

patches {
    about {
        name = "Portfolio Performance Freemium"
        description = "Patches for Portfolio Performance to unlock all premium features"
        source = "https://github.com/NL-TCH/portfolio-performance-freemium-patch"
        author = "NL-TCH"
        contact = ""
        website = ""
        license = "GNU General Public License v3.0"
    }
}
