package com.indidevs.android.shellom

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
