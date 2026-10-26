package app.valenced.plugins

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/**
 * Slot model: the boot slot is what runs, the active slot is what the user edits.
 */
class PluginStateSlotsTest {
    private val paths = PluginPaths(FakeFileSystem(), "/data".toPath())

    @BeforeTest
    fun reset() = PluginStatesStore.resetForTests()

    @AfterTest
    fun cleanup() = PluginStatesStore.resetForTests()

    private fun load() = PluginStatesStore.ensureLoaded(paths)

    private fun bootIntoDefaults() {
        PluginStatesStore.setActiveSlot(paths, PluginStatesStore.DEFAULTS_SLOT, oneShot = true)
        PluginStatesStore.resetForTests()
        load()
    }

    @Test
    fun `normal boot runs the slot the user chose`() {
        load()

        assertEquals(PluginStatesStore.PRIMARY_SLOT, PluginStatesStore.activeSlotId)
        assertEquals(PluginStatesStore.PRIMARY_SLOT, PluginStatesStore.bootSlotId)
        assertFalse(PluginStatesStore.isDefaultsBoot)
        // Same object, so a session write is a saved write with no branch anywhere
        assertSame(PluginStatesStore.active, PluginStatesStore.boot)
    }

    @Test
    fun `a flow is not an entry`() {
        load()
        val slot = PluginStatesStore.active

        slot.flagsOf("com.example.plugin")
        assertFalse(slot.hasPlugin("com.example.plugin"), "reading must not give the setup an opinion")

        slot.write("com.example.plugin", emptySet())
        assertTrue(slot.hasPlugin("com.example.plugin"), "an empty entry still means explicitly off")
    }

    @Test
    fun `writes reach the live flow`() {
        load()
        val slot = PluginStatesStore.active
        val flow = slot.flagsOf("com.example.plugin")

        slot.write("com.example.plugin", setOf(PluginFlags.ENABLED))

        assertEquals(setOf(PluginFlags.ENABLED), flow.value, "a running plugin sees a slot write")
    }

    @Test
    fun `writes see flags a plugin set on itself`() {
        load()
        val slot = PluginStatesStore.active
        slot.write("com.example.plugin", setOf(PluginFlags.ENABLED))

        val flow = slot.flagsOf("com.example.plugin")
        flow.value += PluginFlags.PENDING_RELOAD

        assertTrue(
            slot.write("com.example.plugin", flow.value),
            "writes must see flags a plugin set on itself, or it would cause state desync",
        )
        assertFalse(slot.write("com.example.plugin", flow.value), "but only once")
    }

    @Test
    fun `a persistent slot round-trips through its file`() {
        load()
        PluginStatesStore.active.write("com.example.plugin", setOf(PluginFlags.ENABLED, PluginFlags.REQUIRED_BY_USER))

        assertTrue(paths.fs.exists(paths.slots / PluginStatesStore.PRIMARY_SLOT))

        PluginStatesStore.resetForTests()
        load()

        assertEquals(
            setOf(PluginFlags.ENABLED, PluginFlags.REQUIRED_BY_USER),
            PluginStatesStore.active.entryFlags("com.example.plugin"),
        )
    }

    @Test
    fun `a plugin id outside the BMP survives the states codec`() {
        load()
        PluginStatesStore.active.write("com.example.\uD83D\uDE00", setOf(PluginFlags.ENABLED))

        PluginStatesStore.resetForTests()
        load()

        assertEquals(
            setOf(PluginFlags.ENABLED),
            PluginStatesStore.active.entryFlags("com.example.\uD83D\uDE00"),
        )
    }

    @Test
    fun `bit-less flags live in a slot but never on disk`() {
        load()
        PluginStatesStore.active.write(
            "com.example.plugin",
            setOf(PluginFlags.ENABLED, PluginFlags.PENDING_RELOAD, PluginFlags.STARTED_LATE),
        )

        assertTrue(PluginFlags.PENDING_RELOAD in PluginStatesStore.active.entryFlags("com.example.plugin")!!)

        PluginStatesStore.resetForTests()
        load()

        assertEquals(
            setOf(PluginFlags.ENABLED),
            PluginStatesStore.active.entryFlags("com.example.plugin"),
            "flags with no bit cannot survive serialization",
        )
    }

    @Test
    fun `defaults boot runs an ephemeral slot and leaves the chosen one alone`() {
        load()
        PluginStatesStore.active.write("com.example.plugin", setOf(PluginFlags.ENABLED))

        bootIntoDefaults()

        assertTrue(PluginStatesStore.isDefaultsBoot)
        assertEquals(PluginStatesStore.PRIMARY_SLOT, PluginStatesStore.activeSlotId)
        assertEquals(PluginStatesStore.DEFAULTS_SLOT, PluginStatesStore.bootSlotId)

        // The session starts from nothing, so every plugin falls back to its defaults
        assertFalse(PluginStatesStore.boot.hasPlugin("com.example.plugin"))
        assertFalse(PluginStatesStore.boot.isPluginEnabled("com.example.plugin"))

        // The user's setup is intact and still editable
        assertTrue(PluginStatesStore.active.isPluginEnabled("com.example.plugin"))
    }

    @Test
    fun `a session write during a defaults boot cannot reach disk`() {
        load()
        PluginStatesStore.active.write("com.example.plugin", setOf(PluginFlags.ENABLED))
        bootIntoDefaults()

        PluginStatesStore.boot.write("com.example.plugin", setOf(PluginFlags.PENDING_RELOAD))

        PluginStatesStore.resetForTests()
        load()

        assertEquals(
            setOf(PluginFlags.ENABLED),
            PluginStatesStore.active.entryFlags("com.example.plugin"),
            "the setup the user is repairing must survive the session",
        )
    }

    @Test
    fun `the user's edits during a defaults boot stick`() {
        load()
        PluginStatesStore.active.write("com.example.plugin", setOf(PluginFlags.ENABLED))
        bootIntoDefaults()

        PluginStatesStore.active.write("com.example.plugin", emptySet())

        PluginStatesStore.resetForTests()
        load()

        assertFalse(PluginStatesStore.active.isPluginEnabled("com.example.plugin"))
    }

    @Test
    fun `a one-shot is consumed before any plugin loads`() {
        PluginStatesStore.setActiveSlot(paths, PluginStatesStore.DEFAULTS_SLOT, oneShot = true)
        assertTrue(paths.fs.exists(paths.nextSlotFile))

        PluginStatesStore.resetForTests()
        load()
        assertEquals(PluginStatesStore.DEFAULTS_SLOT, PluginStatesStore.bootSlotId)
        assertFalse(paths.fs.exists(paths.nextSlotFile), "deleted before any plugin loads")

        // Next boot is normal again, so a crash loop cannot trap the user
        PluginStatesStore.resetForTests()
        load()
        assertEquals(PluginStatesStore.PRIMARY_SLOT, PluginStatesStore.bootSlotId)
    }

    @Test
    fun `slot ids that escape the directory are rejected`() {
        for (id in listOf("../evil", "a/b", ".hidden", "")) {
            assertFailsWith<IllegalArgumentException>("expected '$id' to be rejected") {
                requireValidSlotId(id)
            }
        }

        requireValidSlotId("primary")
        requireValidSlotId(PluginStatesStore.DEFAULTS_SLOT)
    }

    @Test
    fun `the active slot survives a reload`() {
        PluginStatesStore.setActiveSlot(paths, "work", oneShot = false)

        PluginStatesStore.resetForTests()
        load()

        assertEquals("work", PluginStatesStore.activeSlotId)
        assertEquals("work", PluginStatesStore.bootSlotId)
    }

    @Test
    fun `getSlots lists the slots on disk`() {
        load()
        PluginStatesStore.active.write("com.example.plugin", setOf(PluginFlags.ENABLED))
        PluginStatesStore.setActiveSlot(paths, "work", oneShot = false)
        PluginStatesStore.resetForTests()
        load()
        PluginStatesStore.active.write("com.example.plugin", emptySet())

        assertEquals(
            listOf(PluginStatesStore.PRIMARY_SLOT, "work"),
            PluginStatesStore.persistentSlotIds(paths).sorted(),
        )
    }

    @Test
    fun `getSlots skips the scratch files a write leaves behind`() {
        load()
        paths.fs.createDirectories(paths.slots)
        // A crashed write and a quarantined corrupt read both sit beside the slot they belong to.
        for (name in listOf("work.tmp", "work.bak", "work.corrupt")) {
            paths.fs.write(paths.slots / name) { writeUtf8("x") }
        }

        assertEquals(
            listOf(PluginStatesStore.PRIMARY_SLOT),
            PluginStatesStore.persistentSlotIds(paths),
            "a scratch file listed as a slot would offer the user a setup that cannot be read",
        )
    }

    @Test
    fun `uninstall drops the plugin from a slot this boot never loaded`() {
        load()
        PluginStatesStore.active.write("com.example.plugin", setOf(PluginFlags.ENABLED))
        PluginStatesStore.resetForTests()

        // A second setup the user isn't in, so nothing loads it this boot.
        PluginStatesStore.setActiveSlot(paths, "work", oneShot = false)
        load()
        PluginStatesStore.active.write("com.example.plugin", setOf(PluginFlags.ENABLED))

        PluginStatesStore.removePluginFromAllSlots(paths, "com.example.plugin")

        PluginStatesStore.resetForTests()
        PluginStatesStore.setActiveSlot(paths, PluginStatesStore.PRIMARY_SLOT, oneShot = false)
        load()

        assertFalse(
            PluginStatesStore.active.hasPlugin("com.example.plugin"),
            "a leftover entry would resurrect the plugin's setup on reinstall",
        )
    }

    @Test
    fun `read sends every loaded slot`() {
        load()
        PluginStatesStore.active.write("com.example.plugin", setOf(PluginFlags.ENABLED))
        bootIntoDefaults()

        val payload = PluginStatesStore.toJSPayload()

        assertEquals(true, payload[PluginStatesStore.PRIMARY_SLOT]?.get("com.example.plugin")?.get("enabled"))
        assertTrue(
            payload[PluginStatesStore.DEFAULTS_SLOT]!!.isEmpty(),
            "JS must agree with native or it runs plugins native skipped",
        )
    }
}
