// Deliberately NOT using the `app.revanced.patches` convention plugin.
//
// That plugin is a dev pre-release, and 1.0.0-dev.11 declares its repository as
// `githubPackages`, which makes Gradle demand credentials under
// `githubPackagesUsername`/`githubPackagesPassword` rather than the `gpr.user`/
// `gpr.key` names ReVanced documents everywhere else. Its only real job is
// packaging, and an .rvp is just a JAR — manifest, kotlin_module, patch classes —
// so a plain Kotlin JVM build produces an equivalent artifact with one less
// moving part and one less credential convention to track.
//
// To go back to the plugin: add the githubPackages* properties to
// ~/.gradle/gradle.properties and restore the `plugins { id("app.revanced.patches") }`
// block plus gradle/libs.versions.toml.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven {
            name = "GitHubPackages"
            // ReVanced publishes only here — there is no Maven Central mirror —
            // and GitHub requires auth even for public packages.
            url = uri("https://maven.pkg.github.com/revanced/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name = "personal-revanced-patches"

include("patches")
