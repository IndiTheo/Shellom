package com.indidevs.android.shellom

/**
 * Represents the current status of the privileged environment elevation.
 */
enum class ShellStatus {
    /** Elevation has not been requested yet. */
    IDLE,

    /** Elevation command has been sent and is currently in progress. */
    ELEVATING,

    /** Elevation was successful and the privileged context is ready for use. */
    READY,

    /** An error occurred during the elevation process. */
    ERROR
}
