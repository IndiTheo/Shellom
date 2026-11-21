package com.indidevs.android.shellom.integrations.adbeesh

import android.util.Log
import com.indidevs.android.adbeesh.runtime.AdbSession
import com.indidevs.android.shellom.ShellExecutor
import java.io.EOFException

/**
 * A [ShellExecutor] that executes shell commands via an ADBeesh [AdbSession].
 *
 * Drives [AdbSession.shell] directly and maps its result to [Result<Unit>].
 *
 * @param elevationSatisfied returns true once the process holds the shell identity.
 *   Bring it from Shellom itself: `provider.status.value == ShellStatus.READY`.
 *   It's injected because the executor is built before the provider
 *   exists, so Shellom cannot wire it itself. Defaults to `{ false }`.
 */
class AdbeeshExecutor(
    private val session: AdbSession,
    private val elevationSatisfied: () -> Boolean = { false },
    private val tag: String = "AdbeeshExecutor"
) : ShellExecutor {

    override suspend fun execute(command: String): Result<Unit> = try {
        val res = session.shell(command)
        if (res.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(res.error ?: "Unknown error"))
        }
    } catch (e: EOFException) {
        // EOFException after elevation succeeds is expected in case AdbSession is closed.
        if (elevationSatisfied()) {
            Result.success(Unit)
        } else {
            Result.failure(e)
        }
    } catch (e: Exception) {
        val shouldLogError = !elevationSatisfied()
        if (shouldLogError) Log.e(tag, "Shell execution failed", e)
        Result.failure(e)
    }
}
