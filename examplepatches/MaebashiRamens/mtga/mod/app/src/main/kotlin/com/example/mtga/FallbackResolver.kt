package com.example.mtga

import android.content.Context
import android.util.Log
import com.example.mtga.common.TargetResolver
import com.example.mtga.common.TargetSet
import java.lang.reflect.Modifier

/**
 * Resolver for builds whose versionCode is not in [Targets.knownVersions].
 * Wraps the latest known [TargetSet] and tries dynamic discovery for symbols
 * with stable runtime anchors (FQN-stable classes, named resources, route
 * singletons). Discovery failures fall back to the static [TargetSet] value,
 * so this mode is strictly additive over `StaticResolver`.
 */
class FallbackResolver(
    override val targets: TargetSet,
    private val classLoader: ClassLoader,
    private val context: Context,
) : TargetResolver {
    override val exact: Boolean = false

    override fun resolveFeedClass(): String =
        firstLoadable(
            listOf(
                targets.feedClass.name,
                "com.truthsocial.core.data.models.feeds.Feed",
                "com.truthsocial.app.data.models.feeds.Feed",
            ),
        ) ?: targets.feedClass.name

    override fun resolveFeaturesClass(): String =
        firstLoadable(
            listOf(
                targets.featuresClass.name,
                "com.truthsocial.core.data.models.Features",
                "com.truthsocial.app.data.models.Features",
            ),
        ) ?: targets.featuresClass.name

    override fun resolveStringResId(
        name: String,
        staticFallback: Int,
    ): Int {
        val id = runCatching { context.resources.getIdentifier(name, "string", context.packageName) }.getOrDefault(0)
        return if (id != 0) id else staticFallback
    }

    /**
     * Build the route → tab-class map. Try the static [TargetSet] entries
     * first, then probe the package these tabs live in (single-letter
     * siblings whose `b()` returns the route literal). If every probe fails,
     * return whatever we resolved statically (possibly empty; in that case
     * [com.example.mtga.hooks.BottomBarReorderHook] declines to install).
     */
    override fun resolveBottomBarTabClasses(): Map<String, Class<*>> {
        val staticResolved =
            targets.bottomNavTabClasses
                .mapNotNull { (route, target) ->
                    runCatching { route to classLoader.loadClass(target.name) }.getOrNull()
                }.toMap()
        if (staticResolved.size == targets.bottomNavTabClasses.size) return staticResolved

        // Partial (a renamed tab class on an uncalibrated build) or empty:
        // probe the sibling package to fill the gaps. Static entries win when
        // both name the same route — they're human-verified.
        val merged = discoverTabsInSiblingPackage() + staticResolved
        if (merged.size < targets.bottomNavTabClasses.size) {
            Log.w(
                "MTGA",
                "resolveBottomBarTabClasses: only ${merged.size}/${targets.bottomNavTabClasses.size} " +
                    "routes resolved on an uncalibrated build",
            )
        }
        return merged
    }

    /**
     * Recover the route → tab-class map on an uncalibrated build where R8
     * shifted the tab classes' single-letter names.
     *
     * The tab base's accessors return resource ids, not a route string, so the
     * tab carries no route to key on. Instead we key on each tab's overridden
     * `toString()` label — stable across obfuscation — mapped to the route via
     * [tabLabelToRoute]; unmatched labels are skipped so the static map wins.
     */
    private fun discoverTabsInSiblingPackage(): Map<String, Class<*>> {
        val tabsPackage = targets.bottomNavTabs.name.substringBeforeLast('.', missingDelimiterValue = "")
        if (tabsPackage.isEmpty()) return emptyMap()

        val alertsCls = runCatching { classLoader.loadClass(targets.bottomNavAlertsTab.name) }.getOrNull() ?: return emptyMap()
        val tabBase = alertsCls.superclass ?: return emptyMap()

        val result = mutableMapOf<String, Class<*>>()
        for (ch in 'a'..'z') {
            val cls = runCatching { classLoader.loadClass("$tabsPackage.$ch") }.getOrNull() ?: continue
            if (cls == tabBase || Modifier.isAbstract(cls.modifiers) || cls.isInterface) continue
            if (!tabBase.isAssignableFrom(cls)) continue

            // Match the singleton field by type, not name, to survive renaming.
            val singletonField =
                cls.declaredFields.firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.type == cls
                } ?: continue
            val singleton =
                runCatching {
                    singletonField.isAccessible = true
                    singletonField.get(null)
                }.getOrNull() ?: continue

            val label = runCatching { singleton.toString() }.getOrNull()?.trim()?.lowercase() ?: continue
            val route = tabLabelToRoute[label] ?: continue
            result[route] = cls
        }
        return result
    }

    private fun firstLoadable(candidates: List<String>): String? =
        candidates.firstNotNullOfOrNull { fqn ->
            runCatching {
                classLoader.loadClass(fqn)
                fqn
            }.getOrNull()
        }

    private companion object {
        /**
         * Tab `toString()` label (lower-cased) → route id. The labels diverge
         * from the route ids (`Home`→`feeds`, `Messages`→`chats`), which is why
         * a tab accessor can't be read as the route directly.
         */
        val tabLabelToRoute =
            mapOf(
                "home" to "feeds",
                "messages" to "chats",
                "alerts" to "alerts",
                "discover" to "discover",
                "groups" to "groups",
                "predictions" to "predictions",
                "feeds" to "feeds",
                "chats" to "chats",
            )
    }
}
