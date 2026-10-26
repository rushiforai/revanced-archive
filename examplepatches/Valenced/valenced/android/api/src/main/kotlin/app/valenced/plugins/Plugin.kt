package app.valenced.plugins

/**
 * A Valenced plugin. Use the [plugin] builder to create a plugin.
 *
 * Plugins are loaded once, then [start] runs with [PluginScope].
 * If you need access to [android.content.Context], capture it with `ctx.withAppContext { ... }` inside [start].
 * Override only what you need; both lifecycle hooks are no-ops by default.
 */
abstract class Plugin internal constructor(val manifest: PluginManifest) {
    /** Called once when the plugin is loaded. Register bridge methods + install hooks here. */
    open fun start(ctx: PluginScope) {}

    /** Called when the plugin is being torn down. */
    open fun stop(ctx: PluginScope) {}
}
