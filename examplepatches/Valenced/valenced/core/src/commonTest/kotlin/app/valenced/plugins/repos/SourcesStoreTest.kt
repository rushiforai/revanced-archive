package app.valenced.plugins.repos

import app.valenced.plugins.PluginPaths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/**
 * Provenance writes, which were untestable while this ran on `AtomicFile`.
 */
class SourcesStoreTest {
    private val paths = PluginPaths(FakeFileSystem(), "/data".toPath())

    @BeforeTest
    fun setUp() {
        SourcesStore.resetForTests()
        SourcesStore.ensureLoaded(paths)
    }

    @AfterTest
    fun tearDown() = SourcesStore.resetForTests()

    private fun reload(): Map<String, PluginSource> {
        SourcesStore.resetForTests()
        return SourcesStore.ensureLoaded(paths)
    }

    @Test
    fun `a record survives a reload`() {
        SourcesStore.record("a.b", repo = "https://repo.example/", channel = "beta", hash = null)

        assertEquals(PluginSource("https://repo.example/", channel = "beta"), reload()["a.b"])
    }

    @Test
    fun `record replaces provenance without touching every other plugin`() {
        SourcesStore.record("a.b", repo = "https://one.example/", channel = REPO_CHANNEL_LATEST, hash = null)
        SourcesStore.record("c.d", repo = "https://two.example/", channel = REPO_CHANNEL_LATEST, hash = null)

        SourcesStore.record("a.b", repo = "https://three.example/", channel = "beta", hash = null)

        val all = reload()
        assertEquals(PluginSource("https://three.example/", channel = "beta"), all["a.b"])
        assertEquals(PluginSource("https://two.example/", channel = REPO_CHANNEL_LATEST), all["c.d"])
    }

    @Test
    fun `held survives the install that follows it`() {
        SourcesStore.record("a.b", repo = "https://repo.example/", channel = REPO_CHANNEL_LATEST, hash = null)
        SourcesStore.setHeld("a.b", held = true)

        SourcesStore.record("a.b", repo = "https://repo.example/", channel = "beta", hash = null)

        val source = reload().getValue("a.b")
        assertTrue(source.held, "a user hold must outlive a reinstall")
        assertEquals("beta", source.channel)
    }

    @Test
    fun `setHeld on an unknown plugin starts from the defaults`() {
        assertEquals(PluginSource(held = true), SourcesStore.setHeld("a.b", held = true))
        assertEquals(PluginSource(held = true), reload()["a.b"])
    }

    @Test
    fun `forgetRepos drops the repo and keeps everything else`() {
        SourcesStore.record("a.b", repo = "https://gone.example/", channel = "beta", hash = null)
        SourcesStore.setHeld("a.b", held = true)
        SourcesStore.record("c.d", repo = "https://kept.example/", channel = REPO_CHANNEL_LATEST, hash = null)

        SourcesStore.forgetRepos(listOf("https://gone.example/"))

        val all = reload()
        assertNull(all.getValue("a.b").repo, "a removed repo makes its plugins sideloaded")
        assertTrue(all.getValue("a.b").held, "the hold is the user's, not the repository's")
        assertEquals("beta", all.getValue("a.b").channel)
        assertEquals("https://kept.example/", all.getValue("c.d").repo)
    }

    @Test
    fun `forgetRepos with nothing to forget writes nothing`() {
        SourcesStore.record("a.b", repo = "https://kept.example/", channel = REPO_CHANNEL_LATEST, hash = null)

        SourcesStore.forgetRepos(emptyList())
        SourcesStore.forgetRepos(listOf("https://unknown.example/"))

        assertEquals("https://kept.example/", reload().getValue("a.b").repo)
    }

    @Test
    fun `remove deletes only that plugin`() {
        SourcesStore.record("a.b", repo = null, channel = REPO_CHANNEL_LATEST, hash = null)
        SourcesStore.record("c.d", repo = null, channel = REPO_CHANNEL_LATEST, hash = null)

        SourcesStore.remove("a.b")
        SourcesStore.remove("missing.plugin")

        val all = reload()
        assertNull(all["a.b"])
        assertTrue("c.d" in all)
    }

    @Test
    fun `an unreadable document is quarantined and reads as empty`() {
        SourcesStore.record("a.b", repo = null, channel = REPO_CHANNEL_LATEST, hash = null)
        paths.fs.write(paths.repos / "sources.json") { writeUtf8("{not json") }

        assertTrue(reload().isEmpty())
        assertTrue(paths.fs.exists(paths.repos / "sources.json.corrupt"), "kept for a bug report")
    }
}
