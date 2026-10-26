package app.valenced.core

import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Writes [target] via a sibling `.tmp` file, then moves it into place, keeping the old contents in a
 * sibling `.bak` until the move lands.
 *
 * The move is atomic on every filesystem this runs on, so a reader sees either the old contents or
 * the new ones, never a half-written file. A move that fails anyway (some Android filesystems refuse
 * to replace an existing file) restores the backup instead of leaving nothing behind, so callers only
 * need the "corrupt file reads as empty" fallback for a genuinely torn write.
 */
fun FileSystem.atomicWrite(target: Path, block: (BufferedSink) -> Unit) {
    val parent = target.parent ?: throw IOException("Cannot write to a filesystem root: $target")
    val tmp = parent / "${target.name}.tmp"
    val backup = parent / "${target.name}.bak"

    createDirectories(parent)
    try {
        write(tmp) { block(this) }

        // A backup left by an earlier crashed write is older, but known good, so it stays and the
        // live file gives way instead.
        val hadTarget = exists(target)
        val restorable = hadTarget && !exists(backup)
        if (restorable) atomicMove(target, backup)
        else if (hadTarget) delete(target)

        try {
            atomicMove(tmp, target)
        } catch (t: Throwable) {
            if (restorable) runCatching { atomicMove(backup, target) }
            throw t
        }

        runCatching { delete(backup) }
    } catch (t: Throwable) {
        runCatching { delete(tmp) }
        throw t
    }
}

/** [atomicWrite] for text, which is every store but the states codec. */
fun FileSystem.atomicWriteUtf8(target: Path, text: String) = atomicWrite(target) { it.writeUtf8(text) }

/** Reads [path] as text, or returns `null` when it is absent or unreadable. */
fun FileSystem.readUtf8OrNull(path: Path): String? =
    runCatching { read(path) { readUtf8() } }.getOrNull()

/** Reads [path] through [block], or returns `null` when it is absent, empty or unreadable. */
fun <T> FileSystem.readOrNull(path: Path, block: (BufferedSource) -> T): T? {
    val size = runCatching { metadataOrNull(path)?.takeIf { it.isRegularFile }?.size }.getOrNull()
    if (size == null || size <= 0L) return null
    return runCatching { read(path) { block(this) } }.getOrNull()
}

/** Names of the regular files directly under [dir], or an empty list when it does not exist. */
fun FileSystem.listFileNames(dir: Path): List<String> =
    listOrNull(dir).orEmpty().filter { metadataOrNull(it)?.isRegularFile == true }.map { it.name }
