package com.indidevs.android.shellom

import android.os.Bundle
import android.util.Log
import androidx.test.runner.AndroidJUnitRunner

/**
 * A custom [AndroidJUnitRunner] designed for shell instrumentation elevation.
 *
 * This runner can be used as the target for `am instrument` to allow the process
 * to adopt shell permissions via [ShellIdentityElevator].
 *
 * While [AndroidJUnitRunner] can be used directly, this class provides better
 * logging for the elevation lifecycle.
 */
class ShellRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        Log.i("ShellRunner", "ShellRunner created")
        super.onCreate(arguments)
    }

    override fun onStart() {
        Log.i("ShellRunner", "ShellRunner starting")
        super.onStart()
    }
}
