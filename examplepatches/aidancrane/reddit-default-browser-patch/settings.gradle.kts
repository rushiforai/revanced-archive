// SPDX-License-Identifier: GPL-3.0-only

rootProject.name = "reddit-default-browser-patch"

pluginManagement {
    includeBuild("build-logic/revanced-patches-gradle-plugin")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("app.revanced.patches")
}
