package com.indidevs.android.shellom.instrumentation

import android.os.Process
import android.util.Log
import androidx.annotation.Keep
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import org.junit.Test

/**
 * The entry point for the instrumentation-based elevation.
 *
 * This class is executed in the target process via `am instrument --no-restart`.
 * It adopts the requested shell permissions using [android.app.UiAutomation]
 * and signals readiness back to the application process.
 *
 * This class must be kept and accessible via 'am instrument'.
 */
@Keep
class ShellIdentityElevator {
    companion object {
        private const val TAG = "ShellIdentityInstrumentor"
        private const val ARG_PERMISSIONS = "permissions"
    }

    /**
     * The test method invoked by instrumentation.
     *
     * It reads required permissions from the instrumentation arguments,
     * adopts them, and then blocks indefinitely to maintain the privileged identity.
     */
    @Test
    fun elevate() {
        val pid = Process.myPid()
        Log.i(TAG, "Starting instrumentation elevation in PID: $pid")

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val permissionsString = args.getString(ARG_PERMISSIONS)

        if (permissionsString == null) {
            Log.e(TAG, "FATAL: No permissions provided via '-e $ARG_PERMISSIONS'")
            return
        }

        val permissions = permissionsString.split(",").map { it.trim() }.toTypedArray()

        val uiAutomation = try {
            instrumentation.uiAutomation
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get UiAutomation", e)
            null
        }

        if (uiAutomation != null) {
            Log.d(TAG, "Adopting permissions: ${permissions.joinToString()}")
            uiAutomation.adoptShellPermissionIdentity(*permissions)
            Log.i(TAG, "Shell identity privileges adopted for PID $pid")
        } else {
            Log.e(TAG, "FATAL: UiAutomation is null. ADB required.")
            return
        }

        // Signal readiness back to the Provider in the app process
        ShellElevationSignal.signalReady()

        // Keep the instrumentation alive to maintain the adopted permissions
        Log.d(TAG, "Elevation successful. Holding process identity...")
        CountDownLatch(1).await()
    }
}
