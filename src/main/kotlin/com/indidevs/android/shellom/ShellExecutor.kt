package com.indidevs.android.shellom

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Functional interface for executing shell commands.
 *
 * This abstracts the underlying transport layer (e.g., ADB, Shizuku, or local shell).
 * The implementation is responsible for routing the command to the privileged channel.
 */
fun interface ShellExecutor {
    /**
     * Executes the given shell [command].
     *
     * @param command The raw shell command to execute.
     * @return A [Result] indicating whether the command was successfully dispatched.
     */
    suspend fun execute(command: String): Result<Unit>

    companion object {
        /**
         * Creates a [ShellExecutor] that waits for the first non-null executor from a [Flow].
         *
         * This is useful when the execution channel is dynamic (e.g., an ADB connection
         * that might connect/disconnect). It suspends until a valid executor is available
         * at the moment a command needs to be executed.
         *
         * @param flow A [Flow] providing the current [ShellExecutor], or null if unavailable.
         * @return A virtual [ShellExecutor] that delegates to the latest available implementation.
         */
        fun fromFlow(flow: Flow<ShellExecutor?>): ShellExecutor = ShellExecutor { command ->
            try {
                val executor = flow.filterNotNull().first()
                executor.execute(command)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
