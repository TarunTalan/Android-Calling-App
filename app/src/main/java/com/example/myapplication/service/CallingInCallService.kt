package com.example.myapplication.service

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.example.myapplication.CallingApp
import com.example.myapplication.MainActivity


class CallingInCallService : InCallService() {

    companion object {
        private const val TAG = "CallingInCallService"
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        // Register ourselves on the Application so ViewModel can call setMuted()
        (applicationContext as CallingApp).inCallService = this
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        (applicationContext as CallingApp).inCallService = null
    }

    // ─── Telecom → ViewModel ──────────────────────────────────────────────────

    /**
     * Called by Telecom when a new call arrives (incoming RINGING or outgoing DIALING).
     * We hand the [Call] object to the ViewModel so it can drive the UI and
     * later invoke answer / reject / disconnect on the real call.
     */
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded  state=${call.state}  handle=${call.details?.handle}")

        val vm = (applicationContext as CallingApp).callViewModel
        vm.onTelecomCallAdded(call)

        // For incoming calls: wake the screen and bring our UI to the front.
        // The Activity is declared with showWhenLocked / turnScreenOn in the manifest.
        if (call.state == Call.STATE_RINGING) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(intent)
        }
    }

    /**
     * Called by Telecom when a call is removed (disconnected / rejected).
     * Notify the ViewModel so it can transition to Ended → Idle.
     */
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved  handle=${call.details?.handle}")
        (applicationContext as CallingApp).callViewModel.onTelecomCallRemoved(call)
    }
}