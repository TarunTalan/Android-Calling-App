package com.example.myapplication.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Listens to real telephony events so that the app UI
 * can react to actual SIM calls (incoming / ended).
 *
 * The ViewModel is reached via the Application-level singleton
 * exposed from MainActivity.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state  = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        Log.d(TAG, "Phone state: $state  number: $number")

        // Delegate to the global ViewModel holder in the Application
        val app = context.applicationContext as? com.example.myapplication.CallingApp ?: return
        val vm  = app.callViewModel

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Real incoming call from SIM
                // Only show incoming screen if app is not already in a call
                if (vm.callState.value is com.example.myapplication.model.CallState.Idle) {
                    vm.receiveIncomingCall(number)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended externally - let ViewModel reset if needed
                // (we don't force end here as telephony may be handled elsewhere)
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call answered / active
            }
        }
    }

    companion object {
        private const val TAG = "PhoneStateReceiver"
    }
}
