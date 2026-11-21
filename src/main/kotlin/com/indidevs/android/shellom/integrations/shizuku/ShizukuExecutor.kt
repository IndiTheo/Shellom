package com.indidevs.android.shellom.integrations.shizuku

import android.content.pm.PackageManager
import com.indidevs.android.shellom.ShellExecutor
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * A [ShellExecutor] implementation that routes privileged commands through Shizuku service.
 *
 * Shizuku operates by establishing an IPC Binder channel between the client process and a privileged
 * server daemon (running under ADB user 2000 or root user 0). This executor verifies Binder connectivity,
 * validates client permission state, and executes target shell commands directly on the remote server.
 *
 * Works across official Shizuku and compatible forks.
 *
 * @property binderTimeoutMs Maximum duration in milliseconds to wait for Shizuku Binder IPC connectivity.
 * @property autoRequestPermission Whether to automatically trigger and await permission if unauthorized.
 * @property requestCode The request code supplied to Shizuku when requesting permission.
 */
class ShizukuExecutor(
    private val binderTimeoutMs: Long = 5000L,
    private val autoRequestPermission: Boolean = false,
    private val requestCode: Int = 1001
) : ShellExecutor {

    override suspend fun execute(command: String): Result<Unit> {
        return try {
            /*
             * Shizuku requires an active Binder transaction channel before any remote IPC operations can be performed.
             * The Binder received listener handles asynchronous initialization when Shizuku service binds dynamically.
             */
            if (!awaitBinder(binderTimeoutMs)) {
                return Result.failure(
                    IllegalStateException(
                        "Shizuku Binder is unavailable. Ensure Shizuku manager or compatible server is running."
                    )
                )
            }

            /*
             * Client permission model in Shizuku is enforced per UID on the remote server side.
             * If autoRequestPermission is enabled, suspends and awaits user authorization.
             */
            if (!awaitPermission()) {
                return Result.failure(
                    SecurityException(
                        "Shizuku permission has not been granted for this application package."
                    )
                )
            }

            /*
             * Command execution sends a Binder IPC transaction to the remote Shizuku daemon interface (IShizukuService).
             * The process runs asynchronously under the privilege context of the Shizuku server daemon.
             */
            val binder = Shizuku.getBinder()
                ?: return Result.failure(IllegalStateException("Shizuku binder is null."))

            val service = IShizukuService.Stub.asInterface(binder)
            service.newProcess(arrayOf("sh", "-c", command), null, null)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks whether the Shizuku service Binder is bound and responsive.
     */
    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /**
     * Checks whether this application package holds granted Shizuku permissions.
     */
    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * Suspends until Shizuku permission is granted, or returns false if denied / autoRequestPermission is false.
     */
    suspend fun awaitPermission(): Boolean {
        if (hasPermission()) return true
        if (!autoRequestPermission) return false

        return suspendCancellableCoroutine { continuation ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(reqCode: Int, grantResult: Int) {
                    if (reqCode == requestCode) {
                        Shizuku.removeRequestPermissionResultListener(this)
                        if (continuation.isActive) {
                            continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                        }
                    }
                }
            }

            Shizuku.addRequestPermissionResultListener(listener)

            continuation.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }

            runCatching { Shizuku.requestPermission(requestCode) }.onFailure {
                Shizuku.removeRequestPermissionResultListener(listener)
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }
    }

    /**
     * Suspends until the Shizuku Binder becomes available or until [timeoutMs] elapses.
     */
    suspend fun awaitBinder(timeoutMs: Long = binderTimeoutMs): Boolean {
        if (isAvailable()) return true
        if (timeoutMs <= 0) return false

        return withTimeoutOrNull(timeoutMs.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Shizuku.OnBinderReceivedListener {
                    override fun onBinderReceived() {
                        Shizuku.removeBinderReceivedListener(this)
                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    }
                }

                Shizuku.addBinderReceivedListener(listener)

                continuation.invokeOnCancellation {
                    Shizuku.removeBinderReceivedListener(listener)
                }

                if (isAvailable()) {
                    Shizuku.removeBinderReceivedListener(listener)
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
            }
        } ?: false
    }
}
