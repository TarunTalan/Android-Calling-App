package com.example.myapplication

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.navigation.AppNavGraph
import com.example.myapplication.ui.theme.BackgroundDark
import com.example.myapplication.ui.theme.CallingAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION") window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        requestDefaultDialerIfNeeded()

        setContent {
            CallingAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = BackgroundDark
                ) {
                    val viewModel = remember {
                        (application as CallingApp).callViewModel
                    }

                    val navController = rememberNavController()

                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { results ->
                        results.forEach { (permission, granted) ->
                            Log.d(TAG, "Permission $permission → granted=$granted")
                        }
                        // Load device contacts as soon as READ_CONTACTS is granted.
                        // Safe to call even if already loaded — it simply refreshes.
                        if (results[Manifest.permission.READ_CONTACTS] == true) {
                            viewModel.loadDeviceContacts(applicationContext)
                        }
                    }

                    LaunchedEffect(Unit) {
                        val permissions = mutableListOf(
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_CALL_LOG,
                            Manifest.permission.CALL_PHONE,
                            // Request READ_CONTACTS so the dial-pad can suggest real
                            // contacts from the device as the user types.
                            Manifest.permission.READ_CONTACTS,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
                        }
                        permissionLauncher.launch(permissions.toTypedArray())
                    }

                    AppNavGraph(
                        navController = navController, viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDialIntent(intent)
    }

    private fun handleDialIntent(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_CALL) {
            val uri: Uri? = intent.data
            if (uri?.scheme == "tel") {
                val number = uri.schemeSpecificPart.orEmpty()
                if (number.isNotEmpty()) {
                    val vm = (application as CallingApp).callViewModel
                    vm.clearNumber()
                    number.forEach { ch -> vm.appendDigit(ch.toString()) }
                    Log.d(TAG, "Pre-filled dial pad with: $number")
                }
            }
        }
    }

    private fun requestDefaultDialerIfNeeded() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    startActivityForResult(roleIntent, REQUEST_CODE_DEFAULT_DIALER)
                    Log.d(TAG, "Requesting ROLE_DIALER via RoleManager")
                }
            } else {
                val telecom = getSystemService(TelecomManager::class.java)
                if (telecom != null && telecom.defaultDialerPackage != packageName) {
                    val changeIntent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                        putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                    }
                    startActivity(changeIntent)
                    Log.d(TAG, "Requesting default dialer via TelecomManager")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestDefaultDialerIfNeeded failed: ${e.message}")
        }
    }

    @Deprecated("Legacy callback, still needed for API < 29 role request path")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_DEFAULT_DIALER) {
            Log.d(TAG, "Default dialer role result: granted=${resultCode == RESULT_OK}")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_DEFAULT_DIALER = 1001
    }
}