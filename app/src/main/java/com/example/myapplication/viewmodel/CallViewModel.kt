package com.example.myapplication.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.ContactsContract
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.CallingApp
import com.example.myapplication.model.CallState
import com.example.myapplication.ui.screen.ContactSuggestion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class CallViewModel(private val app: Application) : ViewModel() {

    companion object {
        private const val TAG = "CallViewModel"
    }

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _dialedNumber = MutableStateFlow("")
    val dialedNumber: StateFlow<String> = _dialedNumber.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds: StateFlow<Int> = _callDurationSeconds.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isOnHold = MutableStateFlow(false)
    val isOnHold: StateFlow<Boolean> = _isOnHold.asStateFlow()

    private var timerJob: Job? = null

    private val _deviceContacts = MutableStateFlow<List<ContactSuggestion>>(emptyList())
    val deviceContacts: StateFlow<List<ContactSuggestion>> = _deviceContacts.asStateFlow()

    private val _recentContacts = MutableStateFlow<List<ContactSuggestion>>(emptyList())
    val recentContacts: StateFlow<List<ContactSuggestion>> = _recentContacts.asStateFlow()

    val contactsLoaded: Boolean get() = _deviceContacts.value.isNotEmpty()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val contactSuggestions: StateFlow<List<ContactSuggestion>> = combine(
        _dialedNumber, _deviceContacts, _recentContacts
    ) { query, _, _ ->
        searchContacts(query)
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Expanded dummy contacts ──────────────────────────────────────────────
    private val fallbackContacts = listOf(
        // Family
        ContactSuggestion(name = "Mom", number = "9999999999"),
        ContactSuggestion(name = "Dad", number = "9888888888"),
        ContactSuggestion(name = "Sister", number = "9777777770"),
        ContactSuggestion(name = "Brother", number = "9666666660"),
        // Friends
        ContactSuggestion(name = "Rohan Sharma", number = "9876543210"),
        ContactSuggestion(name = "Priya Patel", number = "8765432109"),
        ContactSuggestion(name = "Amit Kumar", number = "7654321098"),
        ContactSuggestion(name = "Sneha Singh", number = "6543210987"),
        ContactSuggestion(name = "Rahul Gupta", number = "9123456789"),
        ContactSuggestion(name = "Ananya Verma", number = "9012345678"),
        ContactSuggestion(name = "Vikram Nair", number = "8901234567"),
        ContactSuggestion(name = "Deepa Menon", number = "7890123456"),
        ContactSuggestion(name = "Arjun Malhotra", number = "9456789012"),
        ContactSuggestion(name = "Neha Kapoor", number = "7123456789"),
        ContactSuggestion(name = "Karan Mehta", number = "9345612345"),
        ContactSuggestion(name = "Pooja Iyer", number = "8234561234"),
        ContactSuggestion(name = "Siddharth Rao", number = "7012345678"),
        ContactSuggestion(name = "Divya Krishnan", number = "9556789012"),
        ContactSuggestion(name = "Manish Tiwari", number = "8412345678"),
        ContactSuggestion(name = "Riya Banerjee", number = "9867890123"),
        // Colleagues
        ContactSuggestion(name = "Suresh Joshi", number = "9345678901"),
        ContactSuggestion(name = "Kavita Reddy", number = "8234567890"),
        ContactSuggestion(name = "Ravi Shankar", number = "8811223344"),
        ContactSuggestion(name = "Meera Iyer", number = "9711223344"),
        ContactSuggestion(name = "Arun Pillai", number = "9512345670"),
        ContactSuggestion(name = "Shreya Jain", number = "8312345670"),
        // Saved Numbers
        ContactSuggestion(name = "Work", number = "1800123456"),
        ContactSuggestion(name = "Doctor", number = "9911223344"),
        ContactSuggestion(name = "Gym", number = "9800112233"),
        ContactSuggestion(name = "Landlord", number = "9700112233"),
    )

    @Volatile
    private var activeCall: android.telecom.Call? = null

    // If a transition to ACTIVE is observed but the app doesn't have CALL_PHONE
    // permission, defer committing the Active UI until the permission is granted.
    @Volatile
    private var pendingTransitionToActive: Boolean = false

    private val telecomCallCallback = object : android.telecom.Call.Callback() {
        override fun onStateChanged(call: android.telecom.Call, newState: Int) {
            Log.d(TAG, "telecomCallCallback onStateChanged -> $newState  current=${_callState.value}")
            when (newState) {
                android.telecom.Call.STATE_DIALING, android.telecom.Call.STATE_CONNECTING -> {
                    // Outgoing call confirmed by Telecom — ensure UI shows Calling state.
                    // (UI may already be in Calling from startOutgoingCall(), this is idempotent.)
                    val number = call.details?.handle?.schemeSpecificPart ?: _dialedNumber.value
                    if (_callState.value !is CallState.Calling) {
                        _callState.value = CallState.Calling(number, resolveContactName(number))
                    }
                }

                android.telecom.Call.STATE_ACTIVE -> {
                    val current = _callState.value
                    val number = call.details?.handle?.schemeSpecificPart ?: _dialedNumber.value
                    val name = resolveContactName(number)
                    val isIncoming = current is CallState.Ringing
                    if (current is CallState.Calling || current is CallState.Ringing) {
                        if (hasCallPermission()) {
                            _callState.value = CallState.Active(
                                number = number, callerName = name, isIncoming = isIncoming
                            )
                            startCallTimer()
                            pendingTransitionToActive = false
                        } else {
                            Log.w(TAG, "Deferring Active transition: CALL_PHONE permission missing")
                            pendingTransitionToActive = true
                        }
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
                    if (_callState.value is CallState.Idle) receiveIncomingCall(number)
                }
            }
        }
    }

    fun loadDeviceContacts(context: Context) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { queryDeviceContacts(context) }
            _deviceContacts.value = loaded
            
            val recents = withContext(Dispatchers.IO) { queryRecentCalls(context) }
            _recentContacts.value = recents
            Log.d(TAG, "loadDeviceContacts: loaded ${loaded.size} contacts, ${recents.size} recents")
        }
    }

    private fun queryDeviceContacts(context: Context): List<ContactSuggestion> {
        val results = mutableListOf<ContactSuggestion>()
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
                null,
                null,
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
                    val normalizedNumber = normalizeNumber(rawNumber)
                    if (normalizedNumber.isEmpty()) continue
                    val typeLabel =
                        ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, type, label).toString()
                    val key = "$rawName|$normalizedNumber"
                    if (seen.add(key)) {
                        results.add(
                            ContactSuggestion(
                                name = rawName, number = normalizedNumber, numberLabel = typeLabel
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

    private fun normalizeNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (raw.trimStart().startsWith("+")) "+$digits" else digits
    }

    private fun queryRecentCalls(context: Context): List<ContactSuggestion> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val results = mutableListOf<ContactSuggestion>()
        val seen = mutableSetOf<String>()
        try {
            val projection = arrayOf(
                android.provider.CallLog.Calls.CACHED_NAME,
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.TYPE
            )
            val cursor = context.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${android.provider.CallLog.Calls.DATE} DESC LIMIT 15"
            ) ?: return emptyList()

            cursor.use { c ->
                val nameCol = c.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
                val numCol = c.getColumnIndex(android.provider.CallLog.Calls.NUMBER)

                while (c.moveToNext() && results.size < 8) {
                    val rawNumber = c.getString(numCol)?.trim() ?: continue
                    val rawName = c.getString(nameCol)?.trim()?.takeIf { it.isNotEmpty() } ?: resolveContactName(rawNumber).takeIf { it.isNotEmpty() } ?: "Unknown"
                    val normalizedNumber = normalizeNumber(rawNumber)
                    if (normalizedNumber.isEmpty()) continue
                    
                    val key = normalizedNumber
                    if (seen.add(key)) {
                        results.add(ContactSuggestion(name = rawName, number = normalizedNumber, numberLabel = "Recent"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryRecentCalls failed: ${e.message}")
        }
        return results
    }

    // ── Telecom bridge ────────────────────────────────────────────────────────

    fun onTelecomCallAdded(call: android.telecom.Call) {
        Log.d(TAG, "onTelecomCallAdded  state=${call.state}")
        activeCall = call

        // Register callback BEFORE reading state — avoids missing a transition
        // that fires immediately after registration on some devices.
        call.registerCallback(telecomCallCallback)

        val number = call.details?.handle?.schemeSpecificPart ?: ""

        when (call.state) {
            android.telecom.Call.STATE_RINGING -> {
                receiveIncomingCall(number)
            }

            android.telecom.Call.STATE_DIALING, android.telecom.Call.STATE_CONNECTING -> {
                // Outgoing — idempotent if startOutgoingCall() already set Calling
                if (_callState.value !is CallState.Calling) {
                    _callState.value = CallState.Calling(number, resolveContactName(number))
                }
            }

            android.telecom.Call.STATE_ACTIVE -> {
                // Some devices fire STATE_ACTIVE directly in onCallAdded when the
                // InCallService binds after the call is already live. Always pass
                // through Calling first so OutgoingCallScreen has time to render,
                // then transition to Active after a short nav-animation delay.
                val name = resolveContactName(number)
                if (_callState.value !is CallState.Calling) {
                    _callState.value = CallState.Calling(number, name)
                }
                viewModelScope.launch {
                    kotlinx.coroutines.delay(350)
                    if (_callState.value is CallState.Calling) {
                        if (hasCallPermission()) {
                            _callState.value = CallState.Active(
                                number = number, callerName = name, isIncoming = false
                            )
                            startCallTimer()
                            pendingTransitionToActive = false
                        } else {
                            Log.w(
                                TAG, "Deferring Active transition in onTelecomCallAdded: CALL_PHONE permission missing"
                            )
                            pendingTransitionToActive = true
                        }
                    }
                }
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

    // ── Dial Pad Actions ──────────────────────────────────────────────────────

    fun appendDigit(digit: String) {
        if (_dialedNumber.value.length < 15) _dialedNumber.value += digit
    }

    fun deleteLastDigit() {
        val current = _dialedNumber.value
        if (current.isNotEmpty()) _dialedNumber.value = current.dropLast(1)
    }

    fun clearNumber() {
        _dialedNumber.value = ""
    }

    /**
     * Directly sets the dialed number string (called by BasicTextField onValueChange
     * or when a contact is tapped).
     *
     * Emits only when text actually changes, which prevents LaunchedEffect in
     * DialpadScreen from resetting the cursor on selection-only movements.
     */
    fun setNumber(number: String) {
        val filtered = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }.take(15)
        if (_dialedNumber.value != filtered) _dialedNumber.value = filtered
    }

    // ── Call Flow Actions ─────────────────────────────────────────────────────

    fun startOutgoingCall() {
        val number = _dialedNumber.value.ifEmpty { return }

        // Update UI state immediately so the Calling screen shows at once
        _callState.value = CallState.Calling(number, resolveContactName(number))

        // Place the real SIM call through TelecomManager.
        // This triggers the carrier which causes Telecom to call back into
        // CallingInCallService.onCallAdded() with STATE_DIALING → STATE_ACTIVE,
        // keeping the ViewModel in sync via telecomCallCallback.
        try {
            val telecom = app.getSystemService(android.telecom.TelecomManager::class.java)
                ?: throw IllegalStateException("TelecomManager unavailable")
            val uri = android.net.Uri.fromParts("tel", number, null)
            val extras = android.os.Bundle().apply {
                putBoolean(android.telecom.TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
            }
            telecom.placeCall(uri, extras)
            Log.d(TAG, "startOutgoingCall: placeCall($number)")
        } catch (e: SecurityException) {
            // CALL_PHONE permission not yet granted - simulate the call UI
            Log.w(TAG, "startOutgoingCall: no CALL_PHONE permission, simulating call UI")
            // We'll transition to Active after a delay to simulate it
            viewModelScope.launch {
                delay(2000)
                if (_callState.value is CallState.Calling) {
                    _callState.value = CallState.Active(number, resolveContactName(number), false)
                    startCallTimer()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startOutgoingCall placeCall failed: ${e.message}", e)
            _callState.value = CallState.Idle
        }
    }

    /** Picks a random contact and triggers the Ringing UI (dev / demo mode). */
    fun simulateIncomingCall() {
        val activeList = _deviceContacts.value.ifEmpty { fallbackContacts }
        val pick = activeList.shuffled().first()
        _callState.value = CallState.Ringing(callerName = pick.name, callerNumber = pick.number)
    }

    /** Called by PhoneStateReceiver for a real SIM incoming call. */
    fun receiveIncomingCall(number: String) {
        val name = resolveContactName(number)
        _callState.value = CallState.Ringing(callerName = name, callerNumber = number)
    }

    /**
     * Accepts incoming/outgoing call.
     *  - UI: Ringing/Calling → Active, starts timer.
     *  - Telecom: answers real call with audio-only if attached.
     */
    fun acceptCall() {
        val current = _callState.value
        val (number, name, isIncoming) = when (current) {
            is CallState.Ringing -> Triple(current.callerNumber, current.callerName, true)
            is CallState.Calling -> Triple(current.number, resolveContactName(current.number), false)
            else -> return
        }
        _callState.value = CallState.Active(number = number, callerName = name, isIncoming = isIncoming)
        startCallTimer()
        
        if (activeCall != null) {
            activeCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
            Log.d(TAG, "acceptCall: telecom call answered")
        } else if (!hasCallPermission()) {
            Log.w(TAG, "acceptCall: missing CALL_PHONE permission; simulated active call")
        }
        
        pendingTransitionToActive = false
    }

    /**
     * Check whether the app has CALL_PHONE runtime permission.
     */
    private fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            app, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Should be called by the Activity when CALL_PHONE (and any related) permissions
     * are granted. If a previously-deferred Active transition exists, commit it now.
     */
    fun onCallPermissionsGranted() {
        if (!hasCallPermission()) return
        if (!pendingTransitionToActive) return

        val current = _callState.value
        when (current) {
            is CallState.Calling -> {
                // If telecom call is active, reflect that
                val call = activeCall
                if (call != null && call.state == android.telecom.Call.STATE_ACTIVE) {
                    val number = current.number
                    val name = resolveContactName(number)
                    _callState.value = CallState.Active(number = number, callerName = name, isIncoming = false)
                    startCallTimer()
                    pendingTransitionToActive = false
                }
            }

            is CallState.Ringing -> {
                val ringing = current as CallState.Ringing
                val call = activeCall
                if (call != null && call.state == android.telecom.Call.STATE_ACTIVE) {
                    _callState.value = CallState.Active(
                        number = ringing.callerNumber, callerName = ringing.callerName, isIncoming = true
                    )
                    startCallTimer()
                    pendingTransitionToActive = false
                }
            }

            else -> {}
        }
    }

    /** Rejects incoming call (duration 0). Also rejects real Telecom call if attached. */
    fun rejectCall() {
        _callState.value = CallState.Ended(0)
        activeCall?.reject(false, null)
        Log.d(TAG, "rejectCall  telecom=${activeCall != null}")
    }

    /** Ends active/outgoing call. Cancels timer, records duration, disconnects Telecom. */
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

    // ── In-Call Control Toggles ───────────────────────────────────────────────

    /**
     * Toggles microphone mute.
     *  1. InCallService.setMuted()        — real Telecom call (preferred)
     *  2. AudioManager.isMicrophoneMute   — simulated call fallback
     */
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
        Log.d(TAG, "toggleMute -> $next")
    }

    /**
     * Toggles speaker routing.
     *  1. InCallService.setAudioRoute()        — real Telecom call (preferred)
     *  2. AudioManager.setCommunicationDevice  — API 31+
     *  3. AudioManager.isSpeakerphoneOn        — legacy fallback
     */
    fun toggleSpeaker() {
        val next = !_isSpeakerOn.value
        _isSpeakerOn.value = next
        val inCallService = (app as CallingApp).inCallService
        if (inCallService != null) {
            val route = if (next) android.telecom.CallAudioState.ROUTE_SPEAKER
            else android.telecom.CallAudioState.ROUTE_EARPIECE
            inCallService.setAudioRoute(route)
            Log.d(TAG, "toggleSpeaker -> $next (InCallService.setAudioRoute)")
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
        Log.d(TAG, "toggleSpeaker -> $next (AudioManager fallback)")
    }

    /**
     * Toggles call hold via android.telecom.Call.hold() / unhold().
     * Swallows silently when carrier does not support hold.
     */
    fun toggleHold() {
        val next = !_isOnHold.value
        _isOnHold.value = next
        try {
            if (next) activeCall?.hold() else activeCall?.unhold()
        } catch (e: Exception) {
            Log.w(TAG, "toggleHold: carrier does not support hold: ${e.message}")
        }
        Log.d(TAG, "toggleHold -> $next")
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Matches last 10 digits to handle country-code variations. Returns "" if not found. */
    fun resolveContactName(number: String): String {
        val digits = number.filter { it.isDigit() }.takeLast(10)
        val activeList = _deviceContacts.value.ifEmpty { fallbackContacts }
        return activeList.firstOrNull { c ->
            c.number.filter { it.isDigit() }.takeLast(10) == digits
        }?.name ?: ""
    }

    /** Returns all contacts matching [query]. Priority: number-starts > number-contains > name-contains. */
    fun searchContacts(query: String): List<ContactSuggestion> {
        val useFallback = !hasCallPermission()
        val sourceRecents = if (useFallback) fallbackContacts.take(8) else _recentContacts.value
        val sourceContacts = if (useFallback) fallbackContacts else _deviceContacts.value.ifEmpty { fallbackContacts }

        if (query.isEmpty()) return sourceRecents

        val q = query.lowercase()
        return sourceContacts.filter { c ->
            val num = c.number.filter { it.isDigit() }
            num.startsWith(q) || num.contains(q) || c.name.lowercase().contains(q)
        }.sortedWith(compareByDescending<ContactSuggestion> {
            it.number.filter { ch -> ch.isDigit() }.startsWith(q)
        }.thenByDescending {
            it.number.filter { ch -> ch.isDigit() }.contains(q)
        })
    }

    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }

    private fun startCallTimer() {
        timerJob?.cancel()
        _callDurationSeconds.value = 0
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000); _callDurationSeconds.value++
            }
        }
    }

    private fun resetControls() {
        _callDurationSeconds.value = 0
        _isMuted.value = false; _isSpeakerOn.value = false; _isOnHold.value = false
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