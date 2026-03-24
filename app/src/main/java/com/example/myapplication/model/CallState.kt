package com.example.myapplication.model

/**
 * Represents the complete call lifecycle:
 * Idle → Calling → Active (outgoing answered)
 * Idle → Ringing → Active (incoming accepted)
 * Any state → Ended → Idle
 */
sealed class CallState {
    /** No active or pending call */
    object Idle : CallState()

    /** Outgoing call initiated, waiting for remote to answer */
    data class Calling(val number: String) : CallState()

    /** Incoming call received, waiting for local user to respond */
    data class Ringing(
        val callerName: String,
        val callerNumber: String
    ) : CallState()

    /** Call is connected and ongoing */
    data class Active(
        val number: String,
        val callerName: String = "",
        val isIncoming: Boolean = false
    ) : CallState()

    /** Call has ended - carries duration for display */
    data class Ended(val durationSeconds: Int = 0) : CallState()
}
