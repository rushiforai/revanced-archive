package app.valenced.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionTest {
    @Test
    fun `parses numbers and labels`() {
        assertEquals(Version(listOf(1)), Version.parse("1"))
        assertEquals(Version(listOf(1, 2, 3)), Version.parse("1.2.3"))
        assertEquals(Version(listOf(1, 2), "rc1"), Version.parse("1.2-rc1"))
    }

    @Test
    fun `leading zeros are insignificant`() {
        assertEquals(Version.parse("1.2"), Version.parse("1.02"))
    }

    @Test
    fun `rejects bad grammar`() {
        for (bad in listOf("", "1.", "-rc", "1.0-", "1.0-RC", "1.0-rc_1", "a", "1.-2")) {
            assertFailsWith<IllegalArgumentException>("expected '$bad' to be rejected") { Version.parse(bad) }
        }
    }

    @Test
    fun `shorter side is right padded`() {
        assertEquals(0, Version.parse("1.2").compareTo(Version.parse("1.2.0")))
        assertTrue(Version.parse("1.2.1") > Version.parse("1.2"))
    }

    @Test
    fun `bare beats labeled`() {
        assertTrue(Version.parse("1.2.0") > Version.parse("1.2.0-rc"))
    }

    @Test
    fun `labels compare by digit runs`() {
        assertTrue(Version.parse("1-beta10") > Version.parse("1-beta2"))
        assertTrue(Version.parse("1-beta1") > Version.parse("1-beta"))
        assertTrue(Version.parse("1-alpha") < Version.parse("1-beta"))
    }

    @Test
    fun `round trips through toString`() {
        for (text in listOf("1", "1.2.3", "1.2-rc1", "0.0.1-beta10")) {
            assertEquals(text, Version.parse(text).toString())
        }
    }
}

class VersionRangeTest {
    @Test
    fun `wildcard accepts everything`() {
        assertEquals(VersionRange.ANY, VersionRange.parse("*"))
        assertTrue(VersionRange.ANY.satisfies(Version.parse("0-rc")))
    }

    @Test
    fun `bounds are a conjunction`() {
        val range = VersionRange.parse(">=1.0 <2")
        assertTrue(range.satisfies(Version.parse("1.5")))
        assertFalse(range.satisfies(Version.parse("2.0")))
        assertFalse(range.satisfies(Version.parse("0.9")))
    }

    @Test
    fun `satisfaction strips the candidate label`() {
        val range = VersionRange.parse(">=1.0 <2")
        assertTrue(range.satisfies(Version.parse("1.5-rc")))
        assertFalse(range.satisfies(Version.parse("2.0-rc")))
    }

    @Test
    fun `rejects bad ranges`() {
        for (bad in listOf("", "^1.0", "~1.0", "1.0", ">=1.0-rc")) {
            assertFailsWith<IllegalArgumentException>("expected '$bad' to be rejected") { VersionRange.parse(bad) }
        }
    }

    @Test
    fun `round trips through toString`() {
        assertEquals("*", VersionRange.ANY.toString())
        assertEquals(">=1.0 <2", VersionRange.parse(">=1.0 <2").toString())
    }
}
