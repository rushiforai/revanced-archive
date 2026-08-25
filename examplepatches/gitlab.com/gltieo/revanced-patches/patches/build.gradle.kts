group = "app.revanced"

patches {
    about {
        name = "gltieo patches"
        description = "My patches for the YouTube app. Contains: BetterCaptions: Change style of the captions and also add a second caption in a different language, e.g. for language learning."
        source = "https://gitlab.com/gltieo/revanced-patches.git"
        author = "gltieo"
        contact = "14310368-gltieo@users.noreply.gitlab.com"
        website = "https://gitlab.com/gltieo/revanced-patches"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    implementation(libs.apksig)

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexplicit-backing-fields",
            "-Xcontext-parameters"
        )
    }
}

publishing {
    repositories {
        // Declaring the repository demands the credentials even for a build that
        // never publishes, so only offer it when they are actually configured.
        if (providers.gradleProperty("githubPackagesUsername").isPresent) {
            maven {
                name = "githubPackages"
                url = uri("https://maven.pkg.github.com/revanced/revanced-patches")
                credentials(PasswordCredentials::class)
            }
        }
    }
}

apply(from = "strings-processing.gradle.kts")
