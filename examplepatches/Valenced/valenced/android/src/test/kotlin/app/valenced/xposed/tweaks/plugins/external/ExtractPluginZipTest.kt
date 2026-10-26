package app.valenced.xposed.tweaks.plugins.external

import app.valenced.plugins.PluginErrorCodes
import app.valenced.plugins.PluginPaths
import app.valenced.plugins.PluginSystemError
import app.valenced.plugins.external.stagePluginZip
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * The staging pipeline against the real filesystem, with ZIPs built by the JVM writer plugin authors use.
 */
class ExtractPluginZipTest {
    private val dataDir: File = Files.createTempDirectory("valenced-zip-test").toFile()
    private val paths = PluginPaths(FileSystem.SYSTEM, dataDir.absolutePath.toPath())
    private val root: File get() = paths.dist.toFile()

    @AfterTest
    fun cleanup() {
        dataDir.deleteRecursively()
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun stage(bytes: ByteArray) = stagePluginZip(paths, ".test") { it.write(bytes) }

    private val validManifest = """
        {
            "format": 1,
            "id": "com.example.zip",
            "name": "Zip Test",
            "version": "1.0.0",
            "dependencies": { "valenced.api": {}, "discord": {} }
        }
    """.trimIndent()

    @Test
    fun `a valid plugin ZIP extracts and parses`() {
        val staged = stage(zipOf("manifest.json" to validManifest, "index.js" to "code"))

        assertEquals("com.example.zip", staged.manifest.id)
        assertTrue(File(staged.dir.toFile(), "manifest.json").isFile)
        assertEquals("code", File(staged.dir.toFile(), "index.js").readText())
    }

    @Test
    fun `a ZIP without a manifest fails with a clear message without extracting`() {
        val e = assertFailsWith<PluginSystemError> {
            stage(zipOf("readme.txt" to "hello"))
        }

        assertEquals(PluginErrorCodes.INSTALL_INVALID_ZIP, e.code)
        assertTrue("missing manifest.json" in (e.message ?: ""), "unexpected message: ${e.message}")
        assertTrue(!File(root, ".test").exists(), "tmp dir shouldn't exist")
    }

    @Test
    fun `an invalid manifest aborts before anything is extracted and leaves root clean`() {
        val e = assertFailsWith<PluginSystemError> {
            stage(zipOf("manifest.json" to "{}", "index.js" to "code"))
        }

        assertEquals(PluginErrorCodes.MANIFEST_INVALID, e.code)
        assertEquals(emptyList(), root.listFiles()?.map { it.name }.orEmpty(), "no tmp dir or spool left behind")
    }

    @Test
    fun `success leaves no spool file behind`() {
        stage(zipOf("manifest.json" to validManifest))

        assertTrue(!File(root, ".test.zip").exists(), "spooled ZIP should be deleted")
    }

    @Test
    fun `bytes that are not a ZIP are rejected`() {
        val e = assertFailsWith<PluginSystemError> { stage("not a zip".toByteArray()) }

        assertEquals(PluginErrorCodes.INSTALL_INVALID_ZIP, e.code)
        assertEquals(emptyList(), root.listFiles()?.map { it.name }.orEmpty())
    }
}
