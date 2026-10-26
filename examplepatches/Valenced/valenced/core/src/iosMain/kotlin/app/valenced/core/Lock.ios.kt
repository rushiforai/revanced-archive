package app.valenced.core

import platform.Foundation.NSRecursiveLock

/**
 * `NSRecursiveLock` rather than a hand-rolled `pthread_mutex_t`: it is recursive, and being an ObjC object it is
 * released with its [Lock] instead of leaking a `nativeHeap` allocation for the process lifetime.
 */
internal actual fun newLockHandle(): Any = NSRecursiveLock()

internal actual fun <T> lockedBy(handle: Any, block: () -> T): T {
    val lock = handle as NSRecursiveLock
    lock.lock()
    try {
        return block()
    } finally {
        lock.unlock()
    }
}
