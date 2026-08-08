plugins {
    // Matches the Kotlin the patcher itself is built with, so metadata versions line up.
    kotlin("jvm") version "2.3.10"
}

group = "app.revanced"

// CI passes -PpatchesVersion=<tag without the leading v> so the artifact is named
// after the release. Without this, tagging v1.1.0 would still emit
// patches-1.0.0.rvp and patches.json would point at a file that does not exist.
version = (findProperty("patchesVersion") as String?) ?: "1.0.0"

dependencies {
    // MUST match the patcher bundled inside the ReVanced CLI you patch with.
    // The .rvp is loaded by the CLI's own patcher at apply time, so a mismatch
    // fails at load with NoClassDefFoundError rather than at build time.
    // CLI 6.0.0 bundles 22.0.0 — verified in its gradle/libs.versions.toml.
    //
    // Note the coordinate rename: 21.x and earlier were `app.revanced:revanced-patcher`,
    // 22.0.0+ is `app.revanced:patcher`. Asking for the old name at a 22.x version
    // resolves to nothing and looks like "22 was never published".
    compileOnly("app.revanced:patcher:22.0.0")

    // dexlib2 types (Method, AccessFlags, Opcode) appear in matcher predicates.
    compileOnly("com.android.tools.smali:smali:3.0.5")
}

kotlin {
    // Portable replacement for a hardcoded `org.gradle.java.home`.
    //
    // Homebrew's Gradle launches on JDK 26, and the Kotlin compiler cannot parse a
    // two-digit major version — it dies with `IllegalArgumentException: 26.0.1`
    // inside JavaVersion.parse, surfacing only as "Internal compiler error" and
    // pointing nowhere near the cause. A toolchain runs the compiler on JDK 21
    // regardless of which JVM Gradle itself started on, and needs no absolute path
    // (`org.gradle.java.home` does not expand `~`, and committing a home directory
    // breaks every other machine).
    jvmToolchain(21)

    compilerOptions {
        // patcher 22.x declares accessors with context parameters, e.g.
        // `context(_: BytecodePatchContext)`. Without this the build fails with
        // "To use contextual declarations, specify ...". This is the Kotlin 2.2+
        // spelling; 21.x used the older `-Xcontext-receivers`.
        freeCompilerArgs.add("-Xcontext-parameters")

        // patcher 22.0.0 is published compiled by a *pre-release* Kotlin, which a
        // release compiler refuses to read:
        //   "Class 'app.revanced.patcher.patch.Patch' was compiled by a pre-release
        //    version of Kotlin and cannot be loaded by this version of the compiler"
        // This opts out of that check. The trade-off named in the warning — our own
        // output also gets marked pre-release — costs nothing here, because the only
        // consumer is ReVanced CLI, which runs the same pre-release-built patcher.
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

// --- Dexing -----------------------------------------------------------------
//
// An .rvp must carry BOTH representations of the same patches:
//
//   * .class entries — ReVanced CLI runs on the JVM and loads them with a
//     URLClassLoader (Patch.jvm.kt).
//   * classes.dex    — ReVanced Manager runs on Android and loads them with
//     MultiDexIO + DexClassLoader (Patch.android.kt).
//
// A JVM-only jar patches fine from the CLI and then fails in Manager with
// `EmptyMultiDexContainerException`, which reads as a download failure. The
// official bundle ships 863 .class entries alongside one classes.dex; this
// reproduces that, using the same D8 the upstream Gradle plugin invokes.
val r8: Configuration by configurations.creating

dependencies {
    r8("com.android.tools:r8:9.2.23") // Google's Maven, not Maven Central
}

val dexDir = layout.buildDirectory.dir("dex")

val dexPatches by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("classes"))
    val classesDirs = sourceSets.main.get().output.classesDirs

    inputs.files(classesDirs)
    outputs.dir(dexDir)

    classpath = r8
    mainClass.set("com.android.tools.r8.D8")

    doFirst {
        dexDir.get().asFile.mkdirs()
        val classFiles = classesDirs.asFileTree.matching { include("**/*.class") }.files
        require(classFiles.isNotEmpty()) { "no compiled classes to dex" }

        args = buildList {
            add("--release")
            // Manager requires Android 8+, so nothing older needs to load this.
            add("--min-api"); add("26")
            add("--output"); add(dexDir.get().asFile.absolutePath)
            // Referenced but NOT dexed: the patcher and Kotlin stdlib are
            // supplied by the host at runtime. Without these D8 cannot resolve
            // the supertypes it needs.
            configurations.compileClasspath.get().files.forEach {
                add("--classpath"); add(it.absolutePath)
            }
            addAll(classFiles.map { it.absolutePath })
        }
    }
}

// --- Bundle identity --------------------------------------------------------
//
// Derived from the repository rather than hardcoded, so a fork that runs the
// release workflow publishes under its own name instead of the upstream author's.
//
// Resolution order, first hit wins:
//   1. -PpatchesName / -PpatchesAuthor / -PpatchesSource  (explicit override)
//   2. GITHUB_* environment variables                     (GitHub Actions)
//   3. the `origin` git remote                            (local builds)
//   4. neutral placeholders                               (no remote, e.g. a tarball)

/** `owner/repo` parsed from the origin remote, or null if there is no usable remote. */
fun gitRemoteSlug(): String? = runCatching {
    val remote = providers.exec {
        commandLine("git", "remote", "get-url", "origin")
    }.standardOutput.asText.get().trim()

    // Handles https://host/owner/repo(.git) and git@host:owner/repo(.git),
    // including SSH host aliases such as git@gh-personal:owner/repo.git.
    Regex("""[:/]([^/:]+/[^/:]+?)(?:\.git)?/?$""").find(remote)?.groupValues?.get(1)
}.getOrNull()

val repoSlug: String? =
    providers.environmentVariable("GITHUB_REPOSITORY").orNull ?: gitRemoteSlug()

val repoOwner: String =
    providers.environmentVariable("GITHUB_REPOSITORY_OWNER").orNull
        ?: repoSlug?.substringBefore('/')
        ?: "unknown"

// GITHUB_SERVER_URL differs on GitHub Enterprise; the git remote host is not
// usable here because it may be an SSH alias that resolves nowhere for a reader.
val serverUrl: String =
    providers.environmentVariable("GITHUB_SERVER_URL").orNull ?: "https://github.com"

val bundleName: String =
    (findProperty("patchesName") as String?) ?: "$repoOwner's ReVanced Patches"
val bundleAuthor: String =
    (findProperty("patchesAuthor") as String?) ?: repoOwner
val bundleSource: String =
    (findProperty("patchesSource") as String?)
        ?: repoSlug?.let { "$serverUrl/$it" }
        ?: ""

// An .rvp is an ordinary JAR; the extension is what ReVanced CLI expects to see.
tasks.jar {
    dependsOn(dexPatches)
    archiveExtension.set("rvp")

    // classes.dex sits at the archive root, beside the package directories.
    from(dexDir)

    // ReVanced Manager reads the bundle's display name straight from the JAR
    // manifest. Without `Name` it lists the bundle as "Unnamed".
    manifest {
        attributes(
            "Name" to bundleName,
            "Description" to "Patches for Android apps",
            "Version" to project.version.toString(),
            "Timestamp" to System.currentTimeMillis().toString(),
            "Source" to bundleSource,
            "Author" to bundleAuthor,
            "License" to "GNU General Public License v3.0",
        )
    }
}
