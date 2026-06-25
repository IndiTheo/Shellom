package com.indidevs.android.shellom.instrumentation

import kotlinx.coroutines.CompletableDeferred

/**
 * Internal signaling mechanism to communicate between the Instrumentation thread
 * and the Application process.
 *
 * Since the instrumentation runs in the same process but on a different thread
 * and lifecycle, this object provides a way for the [com.indidevs.android.shellom.InstrumentationShellProvider]
 * to wait for [ShellIdentityElevator] to complete its work.
 */
internal object ShellElevationSignal {
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
