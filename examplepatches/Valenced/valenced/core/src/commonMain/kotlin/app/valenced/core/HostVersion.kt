package app.valenced.core

import app.valenced.plugins.Version

private var current: Version? = null

/**
 * Version of the host app the loader runs inside.
 *
 * The platform reads it at boot and assigns it before plugin discovery. Android takes it from Discord's
 * `BuildConfig.VERSION_NAME_RNA`, which only the target app's class loader can reach, so this is an
 * injected value rather than an `expect fun`.
 */
var hostVersion: Version
    get() = current ?: error("Host version read before the platform assigned it")
    set(value) {
        current = value
    }
