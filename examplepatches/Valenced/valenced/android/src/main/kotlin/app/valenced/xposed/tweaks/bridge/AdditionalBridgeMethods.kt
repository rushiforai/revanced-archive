package app.valenced.xposed.tweaks.bridge

import app.valenced.bridge.asDelegate
import app.valenced.reloadApp
import app.valenced.xposed.api.registerNativeMethod
import app.valenced.xposed.openFileGuarded
import app.valenced.xposed.tweak
import java.io.File

/**
 * `valenced.fs.*` + `valenced.app.*` bridge methods.
 */
val additionalBridgeMethods by tweak {
    registerNativeMethod("valenced.app.reload") {
        reloadApp()
    }

    withAppContext { ctx ->
        registerNativeMethod("valenced.fs.getConstants") {
            mapOf(
                "data" to ctx.dataDir.absolutePath,
                "files" to ctx.filesDir.absolutePath,
                "cache" to ctx.cacheDir.absolutePath,
            )
        }

        registerNativeMethod("valenced.fs.delete") { args ->
            val argv = args.asDelegate()
            val path by argv.string()
            val f = File(path)
            if (f.isDirectory) f.deleteRecursively() else f.delete()
        }

        registerNativeMethod("valenced.fs.exists") { args ->
            val argv = args.asDelegate()
            val path by argv.string()
            File(path).exists()
        }

        registerNativeMethod("valenced.fs.read") { args ->
            val argv = args.asDelegate()
            val path by argv.string()
            val file = File(path).also { it.openFileGuarded() }
            file.bufferedReader().use { it.readText() }
        }

        registerNativeMethod("valenced.fs.write") { args ->
            val argv = args.asDelegate()
            val path by argv.string()
            val contents by argv.string()
            File(path).apply {
                if (isDirectory) throw Error("Path is a directory: $path")
                parentFile?.mkdirs()
                writeText(contents)
            }

            null
        }
    }
}
