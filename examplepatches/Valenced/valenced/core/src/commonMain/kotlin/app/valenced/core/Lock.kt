package app.valenced.core

/**
 * A reentrant lock, because `synchronized`/`@Synchronized` are JVM-only.
 *
 * Reentrancy is required: the stores call locked accessors from inside locked methods.
 *
 * The class itself is common. Only the platform state is `expect`, as an opaque handle, so this needs no
 * `expect class` (still a Beta feature behind `-Xexpect-actual-classes`).
 */
class Lock {
    private val handle: Any = newLockHandle()

    fun <T> withLock(block: () -> T): T = lockedBy(handle, block)
}

/** Platform lock state, owned by one [Lock]. */
internal expect fun newLockHandle(): Any

/** Runs [block] holding [handle], which is always a handle [newLockHandle] returned. */
internal expect fun <T> lockedBy(handle: Any, block: () -> T): T
