package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.model.CallState
import com.example.myapplication.ui.screen.DialPadScreen
import com.example.myapplication.ui.theme.BackgroundDark
import com.example.myapplication.ui.theme.CallingAppTheme
import com.example.myapplication.viewmodel.CallViewModel

class MainActivity : ComponentActivity() {

    private val callViewModel: CallViewModel by lazy {
        ViewModelProvider(this)[CallViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Let content draw behind system bars
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            CallingAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    val callState by callViewModel.callState.collectAsStateWithLifecycle()

                    when (callState) {
                        is CallState.Idle -> {
                            DialPadScreen(
                                viewModel = callViewModel,
                                context = this@MainActivity
                            )
                        }

                        is CallState.Calling -> {
                            // TODO: Replace with your OutgoingCallScreen
                            DialPadScreen(
                                viewModel = callViewModel,
                                context = this@MainActivity
                            )
                        }

                        is CallState.Ringing -> {
                            // TODO: Replace with your IncomingCallScreen
                            DialPadScreen(
                                viewModel = callViewModel,
                                context = this@MainActivity
                            )
                        }

                        is CallState.Active -> {
                            // TODO: Replace with your ActiveCallScreen
                            DialPadScreen(
                                viewModel = callViewModel,
                                context = this@MainActivity
                            )
                        }

                        is CallState.Ended -> {
                            // TODO: Optionally show a brief call-ended screen,
                            // then reset to Idle
                            callViewModel.resetToIdle()
                        }
                    }
                }
            }
        }
    }
}