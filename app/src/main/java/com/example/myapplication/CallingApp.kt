package com.example.myapplication

import android.app.Application
import com.example.myapplication.service.CallingInCallService
import com.example.myapplication.viewmodel.CallViewModel


class CallingApp : Application() {

    val callViewModel: CallViewModel by lazy { CallViewModel(this) }

    @Volatile
    var inCallService: CallingInCallService? = null

    override fun onCreate() {
        super.onCreate()
    }
}