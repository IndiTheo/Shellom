package com.indidevs.android.shellom

import kotlinx.coroutines.CompletableDeferred

/**
 * Process-wide orchestrator of the shell identity elevation.
 *
 * The shell identity is a process-wide fact: several [ShellProvider] instances may exist
 * (one per executor), but only one elevation attempt may run at a time. This object owns
 * that shared state and drives the elevation on behalf of the providers.
 */
internal object ElevationOrchestrator {
    /**
     * The signal of the elevation attempt currently in flight, or null when no attempt
     * is running.
     */
    private var pendingElevation: CompletableDeferred<Unit>? = null

    /**
     * Whether the process currently holds the shell identity.
     *
     * The pending signal alone cannot answer that: a failed attempt also completes it,
     * but exceptionally. This flag tracks only successful elevations.
     */
    @Volatile
    var isElevated = false
        private set

    /**
     * Awaits the shell identity, triggering the elevation when needed.
     *
     * The elevation is claimed atomically: the first caller becomes the owner and runs
     * [triggerElevation], every other caller joins the already pending attempt and only
     * waits for it. When the process is already elevated, the trigger is not run at all.
     *
     * @param triggerElevation runs the elevation command. It is invoked at most once per
     *   attempt, by the owner of that attempt, and receives the signal of that attempt so
     *   that a failure can be reported against it.
     * @throws Exception if the elevation attempt fails.
     */
    suspend fun awaitElevation(triggerElevation: (CompletableDeferred<Unit>) -> Unit) {
        while (true) {  // iterations are bound by the number of concurrent callers
            val elevationSignal: CompletableDeferred<Unit>
            val ownsAttempt: Boolean
            synchronized(this) {
                if (isElevated) {
                    return
                }
                val pending = pendingElevation
                if (pending != null) {
                    elevationSignal = pending
                    ownsAttempt = false
                } else {
                    val claimed = CompletableDeferred<Unit>()
                    pendingElevation = claimed
                    elevationSignal = claimed
                    ownsAttempt = true
                }
            }

            // The trigger runs outside the lock: it must not block other callers.
            if (ownsAttempt) {
                triggerElevation(elevationSignal)
            }

            try {
                elevationSignal.await()
                return
            } catch (e: Exception) {
                // The attempt failed: drop it so the next caller starts a fresh one.
                synchronized(this) {
                    if (pendingElevation === elevationSignal) pendingElevation = null
                }
                // The owner reports the failure of its own attempt. A caller that only
                // joined the attempt retries with a fresh one, so that a failure of
                // another provider's attempt does not fail it as well.
                if (ownsAttempt) throw e
            }
        }
    }

    /**
     * Signals that the shell identity is ready.
     */
    fun signalReady() {
        // TODO: look upon it again if true resets are to be supported. Should only complete own.
        synchronized(this) {
            isElevated = true
            pendingElevation?.complete(Unit)
            pendingElevation = null
        }
    }

    /**
     * Signals that an elevation attempt failed. The process is not elevated, so the
     * next attempt must start from scratch.
     *
     * The failure is reported against the attempt it belongs to, so a late failure of an
     * older attempt cannot bring down a newer one.
     *
     * @param attempt the signal of the failed attempt.
     * @param cause the failure.
     */
    fun fail(attempt: CompletableDeferred<Unit>, cause: Throwable) {
        synchronized(this) {
            attempt.completeExceptionally(cause)
            if (pendingElevation === attempt) {
                isElevated = false
                pendingElevation = null
            }
        }
    }
}
