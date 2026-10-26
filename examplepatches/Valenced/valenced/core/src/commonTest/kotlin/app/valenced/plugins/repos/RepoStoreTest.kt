package app.valenced.plugins.repos

import app.valenced.plugins.PluginPaths
import app.valenced.plugins.PluginSystemError
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class RepoStoreTest {
    private val paths = PluginPaths(FakeFileSystem(), "/data".toPath())

    @BeforeTest
    fun setUp() {
        RepoStore.resetForTests()
        SourcesStore.resetForTests()
        RepoStore.ensureLoaded(paths)
        SourcesStore.ensureLoaded(paths)
    }

    @AfterTest
    fun tearDown() {
        RepoStore.resetForTests()
        SourcesStore.resetForTests()
    }

    private fun reload(): List<UserRepo> {
        RepoStore.resetForTests()
        return RepoStore.ensureLoaded(paths)
    }

    @Test
    fun `the list round-trips in priority order`() {
        RepoStore.set(listOf(UserRepo("https://one.example/"), UserRepo("https://two.example/", enabled = false)))

        assertEquals(
            listOf(UserRepo("https://one.example/"), UserRepo("https://two.example/", enabled = false)),
            reload(),
        )
    }

    @Test
    fun `relative, reserved and duplicate URLs are rejected`() {
        assertFailsWith<PluginSystemError> { RepoStore.set(listOf(UserRepo("repo.example/"))) }
        assertFailsWith<PluginSystemError> { RepoStore.set(listOf(UserRepo(INTERNAL_REPO_URL))) }
        assertFailsWith<PluginSystemError> {
            RepoStore.set(listOf(UserRepo("https://one.example/"), UserRepo("https://one.example/")))
        }

        assertTrue(reload().isEmpty(), "a rejected write must not land")
    }

    @Test
    fun `removing a repo drops its cache and its plugins' provenance`() {
        RepoStore.set(listOf(UserRepo("https://one.example/")))
        RepoStore.writeCachedIndex("https://one.example/", "{}", etag = "W/\"1\"")
        SourcesStore.record("a.b", repo = "https://one.example/", channel = REPO_CHANNEL_LATEST, hash = null)

        RepoStore.set(emptyList())

        assertFalse(paths.fs.exists(RepoStore.cacheDirFor("https://one.example/")))
        assertNull(SourcesStore.all().getValue("a.b").repo)
    }

    @Test
    fun `the cached ETag only counts while the index is cached`() {
        RepoStore.set(listOf(UserRepo("https://one.example/")))
        RepoStore.writeCachedIndex("https://one.example/", "{}", etag = "W/\"1\"")

        assertEquals("W/\"1\"", RepoStore.cachedETag("https://one.example/"))

        paths.fs.delete(RepoStore.cachedIndexFile("https://one.example/"))
        assertNull(RepoStore.cachedETag("https://one.example/"), "an ETag without a body would skip a fetch forever")
    }

    @Test
    fun `a served ETag is dropped when the next response has none`() {
        RepoStore.set(listOf(UserRepo("https://one.example/")))
        RepoStore.writeCachedIndex("https://one.example/", "{}", etag = "W/\"1\"")

        RepoStore.writeCachedIndex("https://one.example/", "{}", etag = null)

        assertNull(RepoStore.cachedETag("https://one.example/"))
    }

    @Test
    fun `an unreadable config is quarantined and reads as empty`() {
        RepoStore.set(listOf(UserRepo("https://one.example/")))
        paths.fs.write(paths.repos / "repos.json") { writeUtf8("{not json") }

        assertTrue(reload().isEmpty())
        assertTrue(paths.fs.exists(paths.repos / "repos.json.corrupt"))
    }
}
