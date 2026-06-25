package com.indidevs.android.shellom

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.indidevs.android.shellom.instrumentation.ShellElevationSignal
import com.indidevs.android.shellom.instrumentation.ShellIdentityElevator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A [ShellProvider] that uses Android Instrumentation to elevate the current
 * process's permissions.
 *
 * @property context The application context.
 * @property scope The [CoroutineScope] in which to launch the elevation command.
 * @property executor The [ShellExecutor] used to run the `am instrument` command via ADB.
 * @property requiredPermissions The list of permissions to adopt (e.g., "android.permission.WRITE_SECURE_SETTINGS").
 * @property runnerClass The fully qualified name of the instrumentation runner (defaults to [ShellRunner]).
 */
class InstrumentationShellProvider(
    private val context: Context,
    private val scope: CoroutineScope,
    private val executor: ShellExecutor,
    private val requiredPermissions: List<String>,
    private val runnerClass: String = ShellRunner::class.java.name
) : AbstractShellProvider() {

    companion object {
        private const val TAG = "InstrumentationShellProvider"
    }

    override suspend fun performElevation(): Context {
        if (!ShellElevationSignal.isReady.isCompleted) {
            triggerElevation()
        }

        Log.d(TAG, "Waiting for Shell readiness signal...")
        ShellElevationSignal.isReady.await()
        Log.i(TAG, "Privileged context acquired via InstrumentationRegistry")

        return InstrumentationRegistry.getInstrumentation().context
    }

    override fun onReset() {
        ShellElevationSignal.reset()
    }

    /**
     * Builds the 'am instrument' command used for elevation.
     *
     * @return The raw shell command string to be executed via ADB.
     */
    fun buildElevationCommand(): String {
        val packageName = context.packageName
        val testClass = ShellIdentityElevator::class.java.name
        val permissions = requiredPermissions.joinToString(",")

        return listOf(
            "am instrument",
            "-w",
            "--no-restart",
            "-e debug false",
            "-e class $testClass",
            "-e permissions $permissions",
            "$packageName/$runnerClass"
        ).joinToString(" ")
    }

    private fun triggerElevation() {
        val cmd = buildElevationCommand()
        Log.i(TAG, "Triggering elevation: $cmd")

        scope.launch(Dispatchers.IO) {
            executor.execute(cmd).onFailure { e ->
                Log.e(TAG, "Failed to execute elevation command", e)
                updateStatus(ShellStatus.ERROR)
            }
        }
    }
}
