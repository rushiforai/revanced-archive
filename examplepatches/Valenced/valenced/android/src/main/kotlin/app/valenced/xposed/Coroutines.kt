package app.valenced.xposed

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Rethrows if the calling coroutine is cancelled. */
suspend fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) currentCoroutineContext().ensureActive()
}

/** [runCatching] that lets cancellation through. See [rethrowIfCancelled]. */
suspend inline fun <R> runSuspendCatching(block: () -> R): Result<R> =
    runCatching(block).onFailure { it.rethrowIfCancelled() }
