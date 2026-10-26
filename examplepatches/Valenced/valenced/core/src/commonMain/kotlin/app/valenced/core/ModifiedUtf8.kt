package app.valenced.core

import okio.BufferedSink
import okio.BufferedSource
import okio.IOException

/**
 * Java's modified UTF-8, as written by `DataOutputStream.writeUTF`.
 *
 * It differs from standard UTF-8 in two places that matter here: `U+0000` is encoded as two bytes
 * so it never appears as a NUL in the stream, and characters outside the BMP are encoded as their
 * two surrogates rather than one four-byte sequence. The plugin states file is written in this
 * codec, so reading it with [BufferedSource.readUtf8] would corrupt a plugin id containing an emoji.
 */
internal fun BufferedSink.writeModifiedUtf8(value: String) {
    var length = 0
    for (c in value) length += modifiedUtf8Size(c.code)
    if (length > 0xFFFF) throw IOException("String too long for modified UTF-8: $length bytes")

    writeShort(length)
    for (c in value) {
        when (val code = c.code) {
            in 1..0x7F -> writeByte(code)
            in 0..0x7FF -> {
                writeByte(0xC0 or (code shr 6))
                writeByte(0x80 or (code and 0x3F))
            }

            else -> {
                writeByte(0xE0 or (code shr 12))
                writeByte(0x80 or ((code shr 6) and 0x3F))
                writeByte(0x80 or (code and 0x3F))
            }
        }
    }
}

/** Reads a string written by [writeModifiedUtf8] or `DataOutputStream.writeUTF`. */
internal fun BufferedSource.readModifiedUtf8(): String {
    val length = readShort().toInt() and 0xFFFF
    val bytes = readByteArray(length.toLong())
    val out = StringBuilder(length)

    var i = 0
    while (i < length) {
        val b1 = bytes[i].toInt() and 0xFF
        when (b1 shr 4) {
            in 0..7 -> {
                out.append(b1.toChar())
                i += 1
            }

            12, 13 -> {
                val b2 = bytes.continuationAt(i + 1, length)
                out.append((((b1 and 0x1F) shl 6) or b2).toChar())
                i += 2
            }

            14 -> {
                val b2 = bytes.continuationAt(i + 1, length)
                val b3 = bytes.continuationAt(i + 2, length)
                out.append((((b1 and 0x0F) shl 12) or (b2 shl 6) or b3).toChar())
                i += 3
            }

            else -> throw IOException("Malformed modified UTF-8 at byte $i")
        }
    }

    return out.toString()
}

private fun modifiedUtf8Size(code: Int) = when (code) {
    in 1..0x7F -> 1
    in 0..0x7FF -> 2
    else -> 3
}

private fun ByteArray.continuationAt(index: Int, length: Int): Int {
    if (index >= length) throw IOException("Truncated modified UTF-8 sequence at byte $index")
    val b = this[index].toInt()
    if (b and 0xC0 != 0x80) throw IOException("Malformed modified UTF-8 at byte $index")
    return b and 0x3F
}
