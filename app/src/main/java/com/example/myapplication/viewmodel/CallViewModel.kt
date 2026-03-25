package com.example.myapplication.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.ContactsContract
import android.telecom.VideoProfile
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.CallingApp
import com.example.myapplication.model.CallState
import com.example.myapplication.ui.screen.ContactSuggestion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for all call state.
 *
 * Two modes of operation:
 *  1. **Real Telecom calls** — [onTelecomCallAdded] / [onTelecomCallRemoved] are called
 *     by [CallingInCallService].  All control actions delegate to the live
 *     [android.telecom.Call] object.
 *  2. **Simulated calls** — used when telephony is unavailable or for demo.
 *     State is driven purely by ViewModel logic.
 */
class CallViewModel(private val app: Application) : ViewModel() {

    companion object {
        private const val TAG = "CallViewModel"
    }

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

    // ─── Device Contacts ──────────────────────────────────────────────────────
    /**
     * All contacts loaded from the device's ContactsContract.
     * Populated by [loadDeviceContacts] once READ_CONTACTS is granted.
     * Falls back to [fallbackContacts] until then.
     */
    private val _deviceContacts = MutableStateFlow<List<ContactSuggestion>>(emptyList())
    val deviceContacts: StateFlow<List<ContactSuggestion>> = _deviceContacts.asStateFlow()

    /** Returns true once real device contacts have been loaded. */
    val contactsLoaded: Boolean get() = _deviceContacts.value.isNotEmpty()

    /**
     * Hardcoded fallback contacts — used for name resolution and suggestions
     * when READ_CONTACTS has not been granted yet.
     */
    private val fallbackContacts = listOf(
        ContactSuggestion(name = "Rohan Sharma", number = "9876543210"),
        ContactSuggestion(name = "Priya Patel", number = "8765432109"),
        ContactSuggestion(name = "Amit Kumar", number = "7654321098"),
        ContactSuggestion(name = "Sneha Singh", number = "6543210987"),
        ContactSuggestion(name = "Rahul Gupta", number = "9123456789"),
        ContactSuggestion(name = "Mom", number = "9999999999"),
        ContactSuggestion(name = "Dad", number = "9888888888"),
    )

    // ─── Live Telecom Call reference ──────────────────────────────────────────
    @Volatile
    private var activeCall: android.telecom.Call? = null

    private val telecomCallCallback = object : android.telecom.Call.Callback() {
        override fun onStateChanged(call: android.telecom.Call, newState: Int) {
            Log.d(TAG, "telecomCallCallback onStateChanged → $newState")
            when (newState) {
                android.telecom.Call.STATE_ACTIVE -> {
                    if (_callState.value is CallState.Calling) {
                        val number = call.details?.handle?.schemeSpecificPart ?: ""
                        val name = resolveContactName(number)
                        _callState.value = CallState.Active(
                            number = number, callerName = name, isIncoming = false
                        )
                        startCallTimer()
                    }
                }

                android.telecom.Call.STATE_DISCONNECTED -> {
                    if (_callState.value !is CallState.Ended && _callState.value !is CallState.Idle) {
                        timerJob?.cancel()
                        _callState.value = CallState.Ended(_callDurationSeconds.value)
                        resetControls()
                    }
                    activeCall?.unregisterCallback(this)
                    activeCall = null
                }

                android.telecom.Call.STATE_RINGING -> {
                    val number = call.details?.handle?.schemeSpecificPart ?: ""
                    if (_callState.value is CallState.Idle) {
                        receiveIncomingCall(number)
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Device Contacts — loads real SIM / phone contacts via ContentResolver
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Queries [ContactsContract] on an IO thread and populates [deviceContacts].
     *
     * Call this from the UI once READ_CONTACTS permission is granted. Safe to
     * call multiple times — subsequent calls simply refresh the list.
     *
     * Each contact entry includes:
     *  - display name
     *  - normalized phone number
     *  - phone type label (Mobile, Work, Home, …)
     */
    fun loadDeviceContacts(context: Context) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                queryDeviceContacts(context)
            }
            _deviceContacts.value = loaded
            Log.d(TAG, "loadDeviceContacts: loaded ${loaded.size} contacts")
        }
    }

    private fun queryDeviceContacts(context: Context): List<ContactSuggestion> {
        val results = mutableListOf<ContactSuggestion>()
        // Seen set to avoid duplicate (name, number) pairs across multiple phone rows
        val seen = mutableSetOf<String>()

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
            )
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,   // no selection filter — load all contacts
                null,
                // Sort: display name ascending so list is predictable
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            ) ?: return fallbackContacts

            cursor.use { c ->
                val nameCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)

                while (c.moveToNext()) {
                    val rawName = c.getString(nameCol)?.trim() ?: continue
                    val rawNumber = c.getString(numCol)?.trim() ?: continue
                    val type = c.getInt(typeCol)
                    val label = c.getString(labelCol)

                    // Normalize: keep only digits and leading '+'
                    val normalizedNumber = normalizeNumber(rawNumber)
                    if (normalizedNumber.isEmpty()) continue

                    // Type label (Mobile, Work, Home, …) shown in the suggestion chip
                    val typeLabel =
                        ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, type, label).toString()

                    val key = "$rawName|$normalizedNumber"
                    if (seen.add(key)) {
                        results.add(
                            ContactSuggestion(
                                name = rawName,
                                number = normalizedNumber,
                                numberLabel = typeLabel,
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryDeviceContacts failed: ${e.message}", e)
            return fallbackContacts
        }

        return if (results.isEmpty()) fallbackContacts else results
    }

    /** Strip spaces / dashes / parentheses; preserve a leading '+'. */
    private fun normalizeNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (raw.trimStart().startsWith("+")) "+$digits" else digits
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Telecom bridge methods — called by CallingInCallService
    // ═══════════════════════════════════════════════════════════════════════════

    fun onTelecomCallAdded(call: android.telecom.Call) {
        Log.d(TAG, "onTelecomCallAdded  state=${call.state}")
        activeCall = call
        call.registerCallback(telecomCallCallback)

        val number = call.details?.handle?.schemeSpecificPart ?: ""
        when (call.state) {
            android.telecom.Call.STATE_RINGING -> receiveIncomingCall(number)
            android.telecom.Call.STATE_DIALING, android.telecom.Call.STATE_CONNECTING -> {
                if (_callState.value !is CallState.Calling) {
                    _callState.value = CallState.Calling(number)
                }
            }

            android.telecom.Call.STATE_ACTIVE -> {
                val name = resolveContactName(number)
                _callState.value = CallState.Active(number = number, callerName = name)
                startCallTimer()
            }
        }
    }

    fun onTelecomCallRemoved(call: android.telecom.Call) {
        Log.d(TAG, "onTelecomCallRemoved")
        if (activeCall == call) {
            call.unregisterCallback(telecomCallCallback)
            activeCall = null
        }
        if (_callState.value !is CallState.Ended && _callState.value !is CallState.Idle) {
            timerJob?.cancel()
            _callState.value = CallState.Ended(_callDurationSeconds.value)
            resetControls()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Dial Pad Actions
    // ═══════════════════════════════════════════════════════════════════════════

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

    /**
     * Directly sets the dialed number (used when BasicTextField changes).
     * Guards against spurious StateFlow emissions when only the cursor moved.
     */
    fun setNumber(number: String) {
        val filtered = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }.take(15)
        // Guard: only emit when text actually changed to avoid LaunchedEffect
        // snapping the cursor back to the end on selection-only changes.
        if (_dialedNumber.value != filtered) {
            _dialedNumber.value = filtered
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Call Flow Actions
    // ═══════════════════════════════════════════════════════════════════════════

    fun startOutgoingCall() {
        val number = _dialedNumber.value
        if (number.isNotEmpty()) {
            _callState.value = CallState.Calling(number)
        }
    }

    fun simulateIncomingCall() {
        val activeList = _deviceContacts.value.ifEmpty { fallbackContacts }
        val sample = activeList.shuffled().take(4)
        val pick = sample.random()
        _callState.value = CallState.Ringing(callerName = pick.name, callerNumber = pick.number)
    }

    fun receiveIncomingCall(number: String) {
        val name = resolveContactName(number)
        _callState.value = CallState.Ringing(callerName = name, callerNumber = number)
    }

    fun acceptCall() {
        val current = _callState.value
        val (number, name, isIncoming) = when (current) {
            is CallState.Ringing -> Triple(current.callerNumber, current.callerName, true)
            is CallState.Calling -> Triple(current.number, resolveContactName(current.number), false)
            else -> return
        }
        _callState.value = CallState.Active(
            number = number, callerName = name, isIncoming = isIncoming
        )
        startCallTimer()
        activeCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        Log.d(TAG, "acceptCall  telecom=${activeCall != null}")
    }

    fun rejectCall() {
        _callState.value = CallState.Ended(0)
        activeCall?.reject(false, null)
        Log.d(TAG, "rejectCall  telecom=${activeCall != null}")
    }

    fun endCall() {
        timerJob?.cancel()
        val duration = _callDurationSeconds.value
        _callState.value = CallState.Ended(duration)
        resetControls()
        activeCall?.disconnect()
        Log.d(TAG, "endCall  duration=$duration  telecom=${activeCall != null}")
    }

    fun resetToIdle() {
        _callState.value = CallState.Idle
        _dialedNumber.value = ""
    }

    // ─── In-Call Control Toggles ──────────────────────────────────────────────

    fun toggleMute() {
        val next = !_isMuted.value
        _isMuted.value = next
        val inCallService = (app as CallingApp).inCallService
        if (inCallService != null) {
            inCallService.setMuted(next)
        } else {
            val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isMicrophoneMute = next
        }
        Log.d(TAG, "toggleMute → $next")
    }

    fun toggleSpeaker() {
        val next = !_isSpeakerOn.value
        _isSpeakerOn.value = next
        val inCallService = (app as CallingApp).inCallService
        if (inCallService != null) {
            val route = if (next) android.telecom.CallAudioState.ROUTE_SPEAKER
            else android.telecom.CallAudioState.ROUTE_EARPIECE
            inCallService.setAudioRoute(route)
            Log.d(TAG, "toggleSpeaker → $next (InCallService.setAudioRoute)")
            return
        }
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (next) {
                val speaker =
                    audioManager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) audioManager.setCommunicationDevice(speaker)
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = next
        }
        Log.d(TAG, "toggleSpeaker → $next (AudioManager fallback)")
    }

    fun toggleHold() {
        val next = !_isOnHold.value
        _isOnHold.value = next
        try {
            if (next) activeCall?.hold() else activeCall?.unhold()
        } catch (e: Exception) {
            Log.w(TAG, "toggleHold: carrier does not support hold: ${e.message}")
        }
        Log.d(TAG, "toggleHold → $next")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Utilities
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resolves a phone number to a contact display name.
     * Checks device contacts first (last 10 digits match), then fallback list.
     */
    fun resolveContactName(number: String): String {
        val digits = number.filter { it.isDigit() }.takeLast(10)
        val activeList = _deviceContacts.value.ifEmpty { fallbackContacts }
        return activeList.firstOrNull { c ->
            c.number.filter { it.isDigit() }.takeLast(10) == digits
        }?.name ?: ""
    }

    /**
     * Returns up to 8 contacts whose number or name matches [query].
     *
     * Sources: real device contacts when loaded, fallback list otherwise.
     *
     * Matching priority:
     *  1. Number starts with query
     *  2. Number contains query
     *  3. Name contains query (case-insensitive)
     */
    fun searchContacts(query: String): List<ContactSuggestion> {
        if (query.length < 2) return emptyList()
        val q = query.lowercase()
        val source = _deviceContacts.value.ifEmpty { fallbackContacts }
        return source.filter { c ->
            val num = c.number.filter { it.isDigit() }
            num.startsWith(q) || num.contains(q) || c.name.lowercase().contains(q)
        }.sortedWith(compareByDescending<ContactSuggestion> {
            it.number.filter { ch -> ch.isDigit() }.startsWith(q)
        }.thenByDescending {
            it.number.filter { ch -> ch.isDigit() }.contains(q)
        }).take(8)
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
        try {
            val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = false
            }
            audioManager.isMicrophoneMute = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "resetControls audio cleanup: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        activeCall?.unregisterCallback(telecomCallCallback)
    }
}