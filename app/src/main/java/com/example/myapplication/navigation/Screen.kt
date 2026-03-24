package com.example.myapplication.navigation

sealed class Screen(val route: String) {
    object DialPad : Screen("dial_pad")
    object OutgoingCall : Screen("outgoing_call")
    object IncomingCall : Screen("incoming_call")
    object ActiveCall : Screen("active_call")

}
