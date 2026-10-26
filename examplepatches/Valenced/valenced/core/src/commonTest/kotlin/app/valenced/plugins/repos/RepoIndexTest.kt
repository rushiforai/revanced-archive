package app.valenced.plugins.repos

import app.valenced.core.logger
import kotlin.test.*

private val log = logger("test")

private const val REPO_URL = "https://example.test/plugins"

private const val SHA = "0000000000000000000000000000000000000000000000000000000000000000"

/** An index with a single plugin `a.b` whose only version `1.0.0` is built from [version]. */
private fun index(version: String, icon: String? = null) = """
    {
      "format": $REPO_INDEX_FORMAT,
      "name": "Test Repo",
      ${icon?.let { "\"icon\": \"$it\"," } ?: ""}
      "plugins": {
        "a.b": {
          "name": "A",
          "channels": { "latest": "1.0.0" },
          "versions": { "1.0.0": $version }
        }
      }
    }
""".trimIndent()

private fun version(url: String, sha256: String = SHA, size: Long = 1) =
    """{ "url": "$url", "sha256": "$sha256", "size": $size }"""

class RepoIndexTest {
    @Test
    fun `rejects an unsupported index format`() {
        val json = """{ "format": ${REPO_INDEX_FORMAT + 1}, "name": "Test Repo" }"""

        assertFailsWith<IllegalArgumentException> { parseRepoIndex(json, REPO_URL, log) }
    }

    @Test
    fun `keeps absolute http urls`() {
        val parsed = parseRepoIndex(index(version("https://example.test/a.zip")), REPO_URL, log)

        assertEquals(setOf("1.0.0"), parsed.plugins.getValue("a.b").versions.keys)
    }

    @Test
    fun `drops javascript and file urls, and everything else that isn't absolute`() {
        for (url in listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "ftp://example.test/a.zip",
            "/a.zip",
            "a.zip"
        )) {
            val parsed = parseRepoIndex(index(version(url)), REPO_URL, log)

            assertTrue(parsed.plugins.isEmpty(), "expected '$url' to be dropped")
        }
    }

    @Test
    fun `drops malformed digests and non-positive sizes`() {
        val badSha = index(version("https://example.test/a.zip", sha256 = "nope"))
        val badSize = index(version("https://example.test/a.zip", size = 0))

        assertTrue(parseRepoIndex(badSha, REPO_URL, log).plugins.isEmpty())
        assertTrue(parseRepoIndex(badSize, REPO_URL, log).plugins.isEmpty())
    }

    @Test
    fun `channels pointing at a dropped version go with it`() {
        val json = """
            {
              "format": $REPO_INDEX_FORMAT,
              "name": "Test Repo",
              "plugins": {
                "a.b": {
                  "name": "A",
                  "channels": { "latest": "1.0.0", "testing": "2.0.0" },
                  "versions": {
                    "1.0.0": ${version("https://example.test/a.zip")},
                    "2.0.0": ${version("javascript:alert(1)")}
                  }
                }
              }
            }
        """.trimIndent()

        val plugin = parseRepoIndex(json, REPO_URL, log).plugins.getValue("a.b")

        assertEquals(mapOf(REPO_CHANNEL_LATEST to "1.0.0"), plugin.channels)
        assertEquals(setOf("1.0.0"), plugin.versions.keys)
    }

    @Test
    fun `icons are asset names or data urls, never fetchable`() {
        val asset = parseRepoIndex(index(version("https://example.test/a.zip"), icon = "ic_asset"), REPO_URL, log)
        val remote = parseRepoIndex(
            index(version("https://example.test/a.zip"), icon = "https://example.test/icon.png"),
            REPO_URL,
            log,
        )

        assertEquals("ic_asset", asset.icon)
        assertNull(remote.icon)
    }
}
