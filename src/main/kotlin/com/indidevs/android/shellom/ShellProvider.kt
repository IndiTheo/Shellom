package com.indidevs.android.shellom

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/**
 * Base implementation of [ShellProvider] that handles state management,
 * thread-safety, and context caching.
 */
abstract class AbstractShellProvider : ShellProvider {

    private val _status = MutableStateFlow(ShellStatus.IDLE)
    override val status: StateFlow<ShellStatus> get() = _status

    protected fun updateStatus(newStatus: ShellStatus) {
        _status.value = newStatus
    }

    private val mutex = Mutex()
    private var cachedContext: Context? = null

    companion object {
        private const val TAG = "AbstractShellProvider"
    }

    override suspend fun awaitContext(): Context = mutex.withLock {
        cachedContext?.let { return it }

        updateStatus(ShellStatus.ELEVATING)

        return try {
            val context = performElevation()
            cachedContext = context
            updateStatus(ShellStatus.READY)
            context
        } catch (e: Exception) {
            Log.e(TAG, "Elevation failed", e)
            updateStatus(ShellStatus.ERROR)
            throw e
        }
    }

    override fun reset() {
        cachedContext = null
        updateStatus(ShellStatus.IDLE)
        onReset()
    }

    /**
     * Implementation-specific elevation logic.
     *
     * This method should contain the core logic to trigger elevation and wait for it
     * to complete. It should suspend until the privileged context is fully acquired.
     *
     * @return The elevated [Context].
     * @throws Exception if elevation fails.
     */
    protected abstract suspend fun performElevation(): Context

    /**
     * Optional hook for subclasses to clean up state on reset.
     *
     * This is called when [reset] is invoked.
     */
    protected open fun onReset() {}
}
