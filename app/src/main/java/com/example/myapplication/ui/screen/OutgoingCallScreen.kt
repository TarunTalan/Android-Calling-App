package com.example.myapplication.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.ui.components.CircleButton
import com.example.myapplication.ui.theme.BackgroundDark
import com.example.myapplication.ui.theme.CallGreen
import com.example.myapplication.ui.theme.CallRed
import com.example.myapplication.ui.theme.CallRedDark
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.utils.formatPhoneDisplay
import com.example.myapplication.model.CallState
import com.example.myapplication.viewmodel.CallViewModel
import kotlinx.coroutines.delay

@Composable
fun OutgoingCallScreen(viewModel: CallViewModel) {
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val number = (callState as? CallState.Calling)?.number
        ?: (callState as? CallState.Ended)?.let { "" }
        ?: ""

    val isEnded = callState is CallState.Ended

    // ── Animated "Calling..." dots ────────────────────────────────────────
    var dotCount by remember { mutableStateOf(1) }
    LaunchedEffect(isEnded) {
        if (!isEnded) {
            while (true) {
                delay(500)
                dotCount = if (dotCount >= 3) 1 else dotCount + 1
            }
        }
    }

    // ── Simulate remote side answering after 7 seconds ────────────────────
    LaunchedEffect(Unit) {
        delay(7000)
        if (viewModel.callState.value is CallState.Calling) {
            viewModel.acceptCall()
        }
    }

    // ── Pulse ring animation ──────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "outgoingPulse")
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring1"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring1Alpha"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring2"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring2Alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF061A0A), BackgroundDark, BackgroundDark),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.6f))

            // ── Avatar with pulse rings ────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                // Outer ring
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(ring2Scale)
                        .clip(CircleShape)
                        .background(CallGreen.copy(alpha = ring2Alpha))
                )
                // Inner ring
                Box(
                    modifier = Modifier
                        .size(155.dp)
                        .scale(ring1Scale)
                        .clip(CircleShape)
                        .background(CallGreen.copy(alpha = ring1Alpha))
                )
                // Avatar
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF1A3A20), Color(0xFF0D2010))
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = CallGreen.copy(alpha = 0.85f),
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Number ────────────────────────────────────────────────────
            Text(
                text = formatPhoneDisplay(number),
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                color = TextPrimary,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── "Calling..." with animated dots ───────────────────────────
            AnimatedContent(
                targetState = if (isEnded) "Call ended" else "Calling" + "•".repeat(dotCount),
                transitionSpec = {
                    fadeIn(tween(300)).togetherWith(fadeOut(tween(200)))
                },
                label = "callingStatus"
            ) { status ->
                Text(
                    text = status,
                    fontSize = 18.sp,
                    color = if (isEnded) CallRed.copy(alpha = 0.85f) else TextSecondary,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── End Call Button ───────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 72.dp)
            ) {
                CircleButton(
                    onClick = { viewModel.endCall() },
                    backgroundColor = CallRed,
                    pressedColor = CallRedDark,
                    size = 76.dp,
                    icon = Icons.Filled.CallEnd,
                    label = "End Call"
                )
            }
        }
    }
}
