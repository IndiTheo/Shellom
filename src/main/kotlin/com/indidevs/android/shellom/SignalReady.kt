package com.indidevs.android.shellom

import kotlinx.coroutines.CompletableDeferred

/**
 * Internal signaling mechanism to communicate readiness between different threads
 * or components within the same process.
 */
internal object SignalReady {
    /**
     * A deferred value that completes when the shell identity has been successfully adopted.
     */
    @Volatile
    var isReady = CompletableDeferred<Unit>()

    /**
     * Signals that the shell identity is ready.
     */
    fun signalReady() {
        isReady.complete(Unit)
    }

    /**
     * Resets the signal for a new elevation attempt.
     */
    fun reset() {
        isReady = CompletableDeferred()
    }
}
