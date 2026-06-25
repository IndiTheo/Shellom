package com.indidevs.android.shellom

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main interface for managing and obtaining a privileged context.
 *
 * A [ShellProvider] manages the lifecycle of process elevation and provides
 * a [Context] that has adopted the requested shell permissions.
 */
interface ShellProvider {
    /**
     * The current status of the elevation process.
     */
    val status: StateFlow<ShellStatus>

    /**
     * Suspends until the privileged context is available and returns it.
     *
     * If elevation hasn't started yet, this will trigger the elevation process.
     * If elevation is already in progress, it will wait for it to complete.
     *
     * @return A [Context] (usually an instrumentation context) with elevated privileges.
     * @throws Exception if elevation fails.
     */
    suspend fun awaitContext(): Context

    /**
     * Resets the provider to [ShellStatus.IDLE] state.
     *
     * This clears the cached context and allows for a fresh elevation attempt.
     */
    fun reset()

    /**
     * Convenience method to observe status changes in a given [CoroutineScope].
     *
     * @param scope The scope in which to collect the status flow.
     * @param action The action to perform when the status changes.
     */
    fun observeStatus(scope: CoroutineScope, action: suspend (ShellStatus) -> Unit) {
        scope.launch {
            status.collectLatest { action(it) }
        }
    }
}
