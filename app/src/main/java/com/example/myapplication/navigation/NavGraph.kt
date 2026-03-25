package com.example.myapplication.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myapplication.model.CallState
import com.example.myapplication.ui.screen.ActiveCallScreen
import com.example.myapplication.ui.screen.DialpadScreen
import com.example.myapplication.ui.screen.IncomingCallScreen
import com.example.myapplication.ui.screen.OutgoingCallScreen
import com.example.myapplication.viewmodel.CallViewModel
import kotlinx.coroutines.delay

@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: CallViewModel = viewModel()
) {
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(callState) {
        when (callState) {
            is CallState.Idle -> { /* DialPad is start destination, nothing to do */
            }

            is CallState.Calling -> {
                navController.navigate(Screen.OutgoingCall.route) {
                    launchSingleTop = true
                    popUpTo(Screen.DialPad.route) { saveState = false }
                }
            }

            is CallState.Ringing -> {
                navController.navigate(Screen.IncomingCall.route) {
                    launchSingleTop = true
                    popUpTo(Screen.DialPad.route) { saveState = false }
                }
            }

            is CallState.Active -> {
                val current = navController.currentDestination?.route
                // Only transition to ActiveCall from the outgoing or incoming screens.
                // If we are somehow still on DialPad it means the state jumped
                // directly from Idle → Active (race condition / fast device).
                // In that case navigate via OutgoingCall so the stack is correct.
                when (current) {
                    Screen.OutgoingCall.route, Screen.IncomingCall.route -> {
                        navController.navigate(Screen.ActiveCall.route) {
                            launchSingleTop = true
                            popUpTo(Screen.DialPad.route) { saveState = false }
                        }
                    }

                    Screen.ActiveCall.route -> { /* already there */
                    }

                    else -> {
                        // Landed on Active without going through Calling/Ringing —
                        // push OutgoingCall briefly then Active so back-stack is sane
                        navController.navigate(Screen.OutgoingCall.route) {
                            launchSingleTop = true
                            popUpTo(Screen.DialPad.route) { saveState = false }
                        }
                        delay(350)
                        navController.navigate(Screen.ActiveCall.route) {
                            launchSingleTop = true
                            popUpTo(Screen.DialPad.route) { saveState = false }
                        }
                    }
                }
            }

            is CallState.Ended -> {
                // Brief pause so user sees end-of-call state on current screen
                delay(1200)
                navController.navigate(Screen.DialPad.route) {
                    popUpTo(0) { inclusive = true }
                }
                viewModel.resetToIdle()
            }
        }
    }

    // ── Route definitions with animated transitions ────────────────────────
    NavHost(
        navController = navController,
        startDestination = Screen.DialPad.route
    ) {
        composable(
            route = Screen.DialPad.route,
            enterTransition = {
                if (initialState.destination.route == null) {
                    EnterTransition.None
                } else {
                    fadeIn(tween(300, easing = LinearOutSlowInEasing)) + scaleIn(tween(300, easing = FastOutSlowInEasing), initialScale = 0.96f)
                }
            },
            exitTransition = {
                fadeOut(tween(250, easing = LinearEasing)) + scaleOut(tween(250, easing = FastOutLinearInEasing), targetScale = 0.96f)
            }
        ) {
            DialpadScreen(viewModel = viewModel)
        }

        composable(
            route = Screen.OutgoingCall.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    tween(350, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(350, easing = LinearEasing))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    tween(300, easing = FastOutLinearInEasing)
                ) + fadeOut(tween(300, easing = LinearEasing))
            }
        ) {
            OutgoingCallScreen(viewModel = viewModel)
        }

        composable(
            route = Screen.IncomingCall.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    tween(350, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(350, easing = LinearEasing))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    tween(300, easing = FastOutLinearInEasing)
                ) + fadeOut(tween(300, easing = LinearEasing))
            }
        ) {
            IncomingCallScreen(viewModel = viewModel)
        }

        composable(
            route = Screen.ActiveCall.route,
            enterTransition = {
                fadeIn(tween(350, easing = LinearOutSlowInEasing)) + scaleIn(tween(350, easing = FastOutSlowInEasing), initialScale = 0.98f)
            },
            exitTransition = {
                fadeOut(tween(250, easing = LinearEasing)) + scaleOut(tween(250, easing = FastOutLinearInEasing), targetScale = 0.98f)
            }
        ) {
            ActiveCallScreen(viewModel = viewModel)
        }
    }
}