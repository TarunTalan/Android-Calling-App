package com.example.myapplication.navigation

import androidx.compose.animation.*
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
import com.example.myapplication.ui.screen.ActiveCallScreen
import com.example.myapplication.model.CallState
import com.example.myapplication.ui.screen.DialPadScreen
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
                if (current != Screen.ActiveCall.route) {
                    navController.navigate(Screen.ActiveCall.route) {
                        launchSingleTop = true
                        popUpTo(Screen.DialPad.route) { saveState = false }
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
                fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.95f)
            },
            exitTransition = {
                fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.95f)
            }
        ) {
            DialPadScreen(viewModel = viewModel, context = context)
        }

        composable(
            route = Screen.OutgoingCall.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    tween(450)
                ) + fadeIn(tween(450))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    tween(350)
                ) + fadeOut(tween(350))
            }
        ) {
            OutgoingCallScreen(viewModel = viewModel)
        }

        composable(
            route = Screen.IncomingCall.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    tween(450)
                ) + fadeIn(tween(450))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    tween(350)
                ) + fadeOut(tween(350))
            }
        ) {
            IncomingCallScreen(viewModel = viewModel)
        }

        composable(
            route = Screen.ActiveCall.route,
            enterTransition = {
                fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.96f)
            },
            exitTransition = {
                fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.96f)
            }
        ) {
            ActiveCallScreen(viewModel = viewModel)
        }
    }
}