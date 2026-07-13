package com.example.mtga.hooks

import com.example.mtga.MainHook.Companion.TAG
import com.example.mtga.common.SettingKeys
import com.example.mtga.common.TargetResolver
import com.example.mtga.config.Settings
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Reorder the bottom navigation bar's tab list (v1.26.2+).
 *
 * Truth Social's v1.26.2 Compose-Nav rewrite moved the tab list off an
 * instance method onto static fields on a single holder class
 * ([TargetSet.bottomNavTabs]): one list per app variant, currently
 * `a` (predictions-enabled) and `b` (chats-enabled).
 *
 * Each static field holds a Kotlin `listOf(...)` of singleton tab objects
 * (instances of [TargetSet.bottomNavTabClasses] values, keyed by route id).
 * We:
 *   1. Force <clinit> by loading the class.
 *   2. Read each static field as `List<Any>`.
 *   3. Tag every entry with its route id; entries the user didn't list
 *      (known-but-unlisted or unknown) keep their original relative order
 *      at the tail, so a partial/stale order never drops a native tab.
 *   4. Reorder per the user's pref, write back via
 *      [XposedHelpers.setStaticObjectField].
 *
 * Compose reads the list when composing the BottomBar; replacing the static
 * slot before any composition runs ensures the UI picks up the new order.
 * Static fields aren't `final` after R8 minification (they were
 * `companion object` properties in Kotlin source).
 *
 * Silently no-ops on v1.26.1 and earlier; the dynamic-list shape there is
 * not supported.
 */
class BottomBarReorderHook(
    resolver: TargetResolver,
) : BaseHook(resolver) {
    override val name = "BottomBarReorder"

    override fun hook(classLoader: ClassLoader) {
        val staticFields = targets.bottomNavTabsStaticFields
        if (staticFields.isEmpty()) {
            XposedBridge.log("[$TAG] BottomBarReorder skipped — current build has no static tab list")
            return
        }

        // Resolve the route→class map through the resolver. StaticResolver
        // returns targets.bottomNavTabClasses loaded; FallbackResolver also
        // probes neighbour single-letter classes for tabs added in a fresh
        // uncalibrated build.
        val routeToClass = resolver.resolveBottomBarTabClasses()
        if (routeToClass.isEmpty()) {
            XposedBridge.log("[$TAG] BottomBarReorder skipped — no tab classes resolved")
            return
        }

        fun parseOrder(raw: String): List<String> = raw.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        val defaultOrder = parseOrder(SettingKeys.DefaultBottomBarTabOrder)
        val preferredOrder =
            Settings
                .getString(SettingKeys.BottomBarTabOrder, SettingKeys.DefaultBottomBarTabOrder)
                .let(::parseOrder)
                // A blank or token-empty pref (e.g. ", ," from clearing every
                // row) must still yield a defined order, not the stock bar.
                .ifEmpty { defaultOrder }

        val tabsClass = XposedHelpers.findClass(targets.bottomNavTabs.name, classLoader)
        // Trigger <clinit> so the static fields are populated before we read them.
        try {
            Class.forName(targets.bottomNavTabs.name, true, classLoader)
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] BottomBarReorder: <clinit> trigger failed: ${t.message}")
            return
        }

        for (fieldName in staticFields) {
            reorderField(tabsClass, fieldName, routeToClass, preferredOrder)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun reorderField(
        tabsClass: Class<*>,
        fieldName: String,
        routeToClass: Map<String, Class<*>>,
        preferredOrder: List<String>,
    ) {
        val current =
            try {
                XposedHelpers.getStaticObjectField(tabsClass, fieldName) as? List<Any>
            } catch (t: Throwable) {
                XposedBridge.log("[$TAG] BottomBarReorder: read ${tabsClass.name}.$fieldName failed: ${t.message}")
                return
            } ?: return

        // Tab singletons are distinct final objects, so match by exact class;
        // fall back to isInstance only if a build ever wraps them. Exact-match
        // stops a base/subclass pair from collapsing two tabs onto one route.
        fun routeOf(tab: Any): String? =
            routeToClass.entries.firstOrNull { (_, cls) -> cls == tab.javaClass }?.key
                ?: routeToClass.entries.firstOrNull { (_, cls) -> cls.isInstance(tab) }?.key

        val tagged: List<Pair<String?, Any>> = current.map { tab -> routeOf(tab) to tab }
        val byRoute = tagged.filter { it.first != null }.associateBy { it.first!! }
        // Dedup by object identity, not route: even if two tabs ever resolved
        // to one route (only possible via the isInstance fallback on a future
        // wrapping build), every native tab is emitted exactly once.
        val emitted = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        val reordered: List<Any> =
            buildList {
                for (route in preferredOrder) {
                    byRoute[route]?.let { if (emitted.add(it.second)) add(it.second) }
                }
                // Keep every unlisted tab at the tail: dropping a native tab
                // hides it and desyncs the length of the two variant lists
                // Truth Social swaps between at runtime.
                for ((_, tab) in tagged) {
                    if (emitted.add(tab)) add(tab)
                }
            }

        val originalRoutes = tagged.map { it.first ?: "?" }
        val newRoutes = reordered.map { routeOf(it) ?: "?" }

        if (reordered == current) {
            XposedBridge.log("[$TAG] BottomBarReorder: $fieldName already in desired order ($originalRoutes)")
            return
        }

        try {
            XposedHelpers.setStaticObjectField(tabsClass, fieldName, reordered)
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] BottomBarReorder: write ${tabsClass.name}.$fieldName failed: ${t.message}")
            return
        }

        // R8 may keep the field `final` and ART can silently no-op a reflective
        // write; read the slot back (log-only) so a dead reorder is diagnosable.
        val readBack = runCatching { XposedHelpers.getStaticObjectField(tabsClass, fieldName) }.getOrNull()
        if (readBack === reordered) {
            XposedBridge.log("[$TAG] BottomBarReorder: $fieldName  $originalRoutes -> $newRoutes")
        } else {
            XposedBridge.log("[$TAG] BottomBarReorder: $fieldName write did not stick (final/inlined field?) — bar may be unchanged")
        }
    }
}
