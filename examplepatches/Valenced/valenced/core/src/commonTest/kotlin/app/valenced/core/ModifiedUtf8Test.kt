package app.valenced.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import okio.IOException

class ModifiedUtf8Test {
    /**
     * Produced by the pre-port writer:
     * ```java
     * out.writeInt(1); out.writeInt(3);
     * out.writeUTF("plain.id");            out.writeInt(3);
     * out.writeUTF("emoji\uD83D\uDE00.id"); out.writeInt(1);
     * out.writeUTF("nul\u0000.\u00e9\u20ac.id"); out.writeInt(0);
     * ```
     */
    private val fixture =
        (
            "0000000100000003" +
                "0008706c61696e2e696400000003" +
                "000e656d6f6a69eda0bdedb8802e696400000001" +
                "000e6e756cc0802ec3a9e282ac2e696400000000"
            ).decodeHex()

    private val entries = listOf(
        "plain.id" to 3,
        "emoji\uD83D\uDE00.id" to 1,
        "nul\u0000.\u00e9\u20ac.id" to 0,
    )

    @Test
    fun `reads a stream written by DataOutputStream`() {
        val source = Buffer().write(fixture)

        assertEquals(1, source.readInt())
        assertEquals(3, source.readInt())
        for ((id, mask) in entries) {
            assertEquals(id, source.readModifiedUtf8())
            assertEquals(mask, source.readInt())
        }
        assertEquals(0L, source.size)
    }

    @Test
    fun `writes the same bytes DataOutputStream would`() {
        val sink = Buffer()
        sink.writeInt(1)
        sink.writeInt(entries.size)
        for ((id, mask) in entries) {
            sink.writeModifiedUtf8(id)
            sink.writeInt(mask)
        }

        assertEquals(fixture, sink.readByteString())
    }

    @Test
    fun `round-trips every string it writes`() {
        for (value in listOf("", "ascii", "\u0000", "\u00e9\u20ac", "\uD83D\uDE00", "a".repeat(1000))) {
            val buffer = Buffer()
            buffer.writeModifiedUtf8(value)
            assertEquals(value, buffer.readModifiedUtf8())
        }
    }

    @Test
    fun `rejects malformed sequences`() {
        // Declares one byte, but 0xE2 leads a three byte sequence.
        assertFailsWith<IOException> { Buffer().write("0001e2".decodeHex()).readModifiedUtf8() }
        // Third byte is not a continuation byte.
        assertFailsWith<IOException> { Buffer().write("0003e28100".decodeHex()).readModifiedUtf8() }
    }

    @Test
    fun `rejects a string longer than the two byte length prefix`() {
        assertFailsWith<IOException> { Buffer().writeModifiedUtf8("\u20ac".repeat(30000)) }
    }
}
