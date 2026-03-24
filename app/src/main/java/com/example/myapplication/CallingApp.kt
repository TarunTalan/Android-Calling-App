package com.example.myapplication

import android.app.Application
import com.example.myapplication.viewmodel.CallViewModel

/**
 * Application singleton that holds the shared [CallViewModel]
 * so both the Activity and BroadcastReceiver can access it.
 */
class CallingApp : Application() {

    val callViewModel: CallViewModel by lazy { CallViewModel() }

    override fun onCreate() {
        super.onCreate()
    }
}
