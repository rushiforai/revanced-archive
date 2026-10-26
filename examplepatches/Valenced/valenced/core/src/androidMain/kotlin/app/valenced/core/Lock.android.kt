package app.valenced.core

internal actual fun newLockHandle(): Any = Any()

/** A JVM monitor is already reentrant. */
internal actual fun <T> lockedBy(handle: Any, block: () -> T): T = synchronized(handle, block)
