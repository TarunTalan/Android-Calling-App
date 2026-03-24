package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.model.CallState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CallViewModel : ViewModel() {

    // ─── Call State ───────────────────────────────────────────────────────────
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    // ─── Dial Pad ─────────────────────────────────────────────────────────────
    private val _dialedNumber = MutableStateFlow("")
    val dialedNumber: StateFlow<String> = _dialedNumber.asStateFlow()

    // ─── Active Call Controls ─────────────────────────────────────────────────
    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds: StateFlow<Int> = _callDurationSeconds.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isOnHold = MutableStateFlow(false)
    val isOnHold: StateFlow<Boolean> = _isOnHold.asStateFlow()

    private var timerJob: Job? = null

    // ─── Sample contact list for name mapping (Bonus) ─────────────────────────
    private val contacts = mapOf(
        "9876543210" to "Rohan Sharma",
        "8765432109" to "Priya Patel",
        "7654321098" to "Amit Kumar",
        "6543210987" to "Sneha Singh",
        "9123456789" to "Rahul Gupta",
        "9999999999" to "Mom",
        "9888888888" to "Dad",
    )

    // ─── Dial Pad Actions ────────────────────────────────────────────────────

    fun appendDigit(digit: String) {
        if (_dialedNumber.value.length < 15) {
            _dialedNumber.value += digit
        }
    }

    fun deleteLastDigit() {
        val current = _dialedNumber.value
        if (current.isNotEmpty()) {
            _dialedNumber.value = current.dropLast(1)
        }
    }

    fun clearNumber() {
        _dialedNumber.value = ""
    }

    // ─── Call Flow Actions ───────────────────────────────────────────────────

    /** Initiates an outgoing call with the currently dialed number */
    fun startOutgoingCall() {
        val number = _dialedNumber.value
        if (number.isNotEmpty()) {
            _callState.value = CallState.Calling(number)
        }
    }

    /** Simulates an incoming call (for demo / evaluation) */
    fun simulateIncomingCall() {
        val sampleCallers = listOf(
            "Rohan Sharma" to "9876543210",
            "Priya Patel" to "8765432109",
            "Amit Kumar" to "7654321098",
            "Mom" to "9999999999",
        )
        val (name, number) = sampleCallers.random()
        _callState.value = CallState.Ringing(
            callerName = name,
            callerNumber = number
        )
    }

    /** Accepts an incoming or outgoing call — moves to Active state */
    fun acceptCall() {
        val current = _callState.value
        val (number, name, isIncoming) = when (current) {
            is CallState.Ringing -> Triple(current.callerNumber, current.callerName, true)
            is CallState.Calling -> Triple(
                current.number,
                resolveContactName(current.number),
                false
            )
            else -> return
        }
        _callState.value = CallState.Active(
            number = number,
            callerName = name,
            isIncoming = isIncoming
        )
        startCallTimer()
    }

    /** Rejects an incoming call */
    fun rejectCall() {
        _callState.value = CallState.Ended(0)
    }

    /** Ends any active or outgoing call */
    fun endCall() {
        timerJob?.cancel()
        _callState.value = CallState.Ended(_callDurationSeconds.value)
        resetControls()
    }

    /** Resets to Idle after call ended - called after showing call-ended UI */
    fun resetToIdle() {
        _callState.value = CallState.Idle
        _dialedNumber.value = ""
    }

    // ─── In-Call Control Toggles ─────────────────────────────────────────────

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
    }

    fun toggleHold() {
        _isOnHold.value = !_isOnHold.value
    }

    // ─── Utilities ───────────────────────────────────────────────────────────

    fun resolveContactName(number: String): String {
        val digits = number.filter { it.isDigit() }.takeLast(10)
        return contacts[digits] ?: ""
    }

    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private fun startCallTimer() {
        timerJob?.cancel()
        _callDurationSeconds.value = 0
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _callDurationSeconds.value++
            }
        }
    }

    private fun resetControls() {
        _callDurationSeconds.value = 0
        _isMuted.value = false
        _isSpeakerOn.value = false
        _isOnHold.value = false
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
