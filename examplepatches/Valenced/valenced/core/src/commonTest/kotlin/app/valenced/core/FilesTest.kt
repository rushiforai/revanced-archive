package app.valenced.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class FilesTest {
    private val fs = FakeFileSystem()
    private val dir = "/data".toPath()

    @Test
    fun `atomicWrite creates parent directories and leaves no temp file`() {
        val target = dir / "nested" / "file"
        fs.atomicWriteUtf8(target, "hello")

        assertEquals("hello", fs.readUtf8OrNull(target))
        assertFalse(fs.exists(dir / "nested" / "file.tmp"))
    }

    @Test
    fun `atomicWrite replaces existing contents`() {
        val target = dir / "file"
        fs.atomicWriteUtf8(target, "old")
        fs.atomicWriteUtf8(target, "new")

        assertEquals("new", fs.readUtf8OrNull(target))
    }

    @Test
    fun `a failed write leaves the old contents and no temp file`() {
        val target = dir / "file"
        fs.atomicWriteUtf8(target, "old")

        assertFailsWith<IllegalStateException> {
            fs.atomicWrite(target) { sink ->
                sink.writeUtf8("partial")
                error("boom")
            }
        }

        assertEquals("old", fs.readUtf8OrNull(target))
        assertFalse(fs.exists(dir / "file.tmp"))
    }

    @Test
    fun `a completed write leaves no backup behind`() {
        val target = dir / "file"
        fs.atomicWriteUtf8(target, "old")
        fs.atomicWriteUtf8(target, "new")

        assertFalse(fs.exists(dir / "file.bak"))
    }

    @Test
    fun `readUtf8OrNull returns null for a missing file`() {
        assertNull(fs.readUtf8OrNull(dir / "missing"))
    }

    @Test
    fun `readOrNull returns null for missing and empty files`() {
        fs.createDirectories(dir)
        fs.write(dir / "empty") {}

        assertNull(fs.readOrNull(dir / "missing") { it.readUtf8() })
        assertNull(fs.readOrNull(dir / "empty") { it.readUtf8() })
        assertNull(fs.readOrNull(dir) { it.readUtf8() })
    }

    @Test
    fun `listFileNames skips directories and missing roots`() {
        fs.createDirectories(dir / "sub")
        fs.write(dir / "a") { writeUtf8("a") }
        fs.write(dir / "b") { writeUtf8("b") }

        assertEquals(listOf("a", "b"), fs.listFileNames(dir).sorted())
        assertTrue(fs.listFileNames(dir / "missing").isEmpty())
    }
}
