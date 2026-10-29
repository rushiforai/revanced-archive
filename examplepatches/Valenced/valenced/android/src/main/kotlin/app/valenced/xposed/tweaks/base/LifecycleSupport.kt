package app.valenced.xposed.tweaks.base

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import app.valenced.xposed.Tweak
import app.valenced.xposed.hook
import app.valenced.xposed.method
import app.valenced.xposed.tweak
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

@Volatile
private var contextRef = WeakReference<Context>(null)

@Volatile
private var activityRef = WeakReference<Activity>(null)

/** Guards both subscriber lists so a concurrent subscribe can't be lost mid-drain. */
private val subscriberLock = Any()
private val contextSubscribers = mutableListOf<(Context) -> Unit>()
private val activitySubscribers = mutableListOf<(Activity) -> Unit>()

private val activityHooked = AtomicBoolean(false)

/**
 * Captures [Context] (from the host Application's `attachBaseContext`) and [Activity] (from the launcher activity's
 * `onCreate`), so other tweaks can use [withAppContext]/[withAppActivity] to await them.
 *
 * The launcher activity is resolved from the package manager, since its class name isn't stable across Discord versions.
 */
val lifecycleSupport by tweak {
    // Since we updated LSPatch, dexopt now compiles the host's code, inlining ContextWrapper.attachBaseContext into its super calls, so the hook never fires.
    // We need to hook the host's own override, since the framework reaches it through a virtual call, so it won't be inlined.
    val appClass = appInfo.className?.let { classLoader.loadClass(it) } ?: Application::class.java
    val attachBaseContext = generateSequence(appClass) { it.superclass }
        .firstNotNullOf { runCatching { it.method("attachBaseContext", Context::class.java) }.getOrNull() }

    log.d("Hooking ${attachBaseContext.declaringClass.name}.attachBaseContext")
    attachBaseContext.hook {
        after { onContext(thisObject as Context) }
    }
}

private fun Tweak.onContext(ctx: Context) {
    contextRef = WeakReference(ctx)
    log.i("Got Context")
    drainContextSubscribers(ctx)

    if (activityHooked.compareAndSet(false, true)) {
        try {
            hookLauncherActivity(ctx)
        } catch (e: Throwable) {
            log.e("Failed to hook the launcher activity", e)
        }
    }
}

private fun Tweak.hookLauncherActivity(ctx: Context) {
    val name = try {
        launcherActivityName(ctx)
    } catch (e: Throwable) {
        log.e("Unable to resolve the launcher activity", e)
        return
    }

    // The nearest declaration may belong to a superclass, so filter by the exact class.
    var cls: Class<*>? = classLoader.loadClass(name)
    var onCreate: Method? = null
    while (cls != null && onCreate == null) {
        val current = cls
        onCreate = runCatching { current.method("onCreate", Bundle::class.java) }.getOrNull()
        cls = current.superclass
    }

    if (onCreate == null) {
        log.e("No onCreate found for launcher activity $name")
        return
    }

    log.i("Hooking launcher activity $name (onCreate in ${onCreate.declaringClass.name})")
    onCreate.hook {
        after {
            if (thisObject?.javaClass?.name != name) return@after

            val act = thisObject as Activity
            activityRef = WeakReference(act)
            log.i("Got Activity")

            if (contextRef.get() == null) {
                contextRef = WeakReference(act)
                log.w("Activity created before Context; process may have been recreated")
                drainContextSubscribers(act)
            }
            drainActivitySubscribers(act)
        }
    }
}

/** Returns the class name of the activity that the launcher intent resolves to, following activity aliases. */
private fun launcherActivityName(ctx: Context): String {
    val pm = ctx.packageManager
    val intent = pm.getLaunchIntentForPackage(ctx.packageName)
        ?: error("No launch intent for ${ctx.packageName}")
    val info = pm.resolveActivity(intent, 0)?.activityInfo
        ?: error("Launch intent for ${ctx.packageName} doesn't resolve")
    return info.targetActivity ?: info.name
}

// Subscribers are removed before they run, so one throwing must not skip the rest.
private fun Tweak.drainContextSubscribers(ctx: Context) {
    val pending = synchronized(subscriberLock) {
        val copy = contextSubscribers.toList()
        contextSubscribers.clear()
        copy
    }
    for (s in pending) {
        try {
            s(ctx)
        } catch (e: Throwable) {
            log.e("Context subscriber threw", e)
        }
    }
}

private fun Tweak.drainActivitySubscribers(act: Activity) {
    val pending = synchronized(subscriberLock) {
        val copy = activitySubscribers.toList()
        activitySubscribers.clear()
        copy
    }
    for (s in pending) {
        try {
            s(act)
        } catch (e: Throwable) {
            log.e("Activity subscriber threw", e)
        }
    }
}

/** Tweak or plugin code should use [app.valenced.xposed.api.HostScope.withAppContext] instead. */
fun withAppContext(block: (Context) -> Unit) {
    val ctx = synchronized(subscriberLock) {
        contextRef.get() ?: run {
            contextSubscribers += block
            return
        }
    }
    block(ctx)
}

/** Tweak or plugin code should use [app.valenced.xposed.api.HostScope.withAppActivity] instead. */
fun withAppActivity(block: (Activity) -> Unit) {
    val act = synchronized(subscriberLock) {
        activityRef.get() ?: run {
            activitySubscribers += block
            return
        }
    }
    block(act)
}
