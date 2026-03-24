package com.example.myapplication.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.myapplication.model.CallState
import com.example.myapplication.ui.components.CircleButton
import com.example.myapplication.ui.theme.BackgroundDark
import com.example.myapplication.ui.theme.CallGreen
import com.example.myapplication.ui.theme.CallGreenDark
import com.example.myapplication.ui.theme.CallRed
import com.example.myapplication.ui.theme.CallRedDark
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.utils.formatPhoneDisplay
import com.example.myapplication.viewmodel.CallViewModel

@Composable
fun IncomingCallScreen(viewModel: CallViewModel) {
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val state = callState as? CallState.Ringing

    val callerName   = state?.callerName ?: ""
    val callerNumber = state?.callerNumber ?: ""

    // ── Ripple ring animations ─────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "incomingPulse")

    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "r1s"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.30f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "r1a"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "r2s"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.18f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "r2a"
    )
    val ring3Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "r3s"
    )
    val ring3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.10f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "r3a"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A18), BackgroundDark, Color(0xFF0A0A18)),
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.55f))

            // ── "Incoming call" label ─────────────────────────────────────
            Text(
                text = "Incoming Call",
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                color = TextSecondary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Avatar with layered pulse rings ───────────────────────────
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(ring3Scale)
                        .clip(CircleShape)
                        .background(CallGreen.copy(alpha = ring3Alpha))
                )
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(ring2Scale)
                        .clip(CircleShape)
                        .background(CallGreen.copy(alpha = ring2Alpha))
                )
                Box(
                    modifier = Modifier
                        .size(155.dp)
                        .scale(ring1Scale)
                        .clip(CircleShape)
                        .background(CallGreen.copy(alpha = ring1Alpha))
                )
                // Avatar circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(118.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF1A1A30), Color(0xFF10101F))
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color(0xFF9090FF).copy(alpha = 0.85f),
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Caller name ───────────────────────────────────────────────
            Text(
                text = callerName,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                letterSpacing = 0.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Caller number ─────────────────────────────────────────────
            Text(
                text = formatPhoneDisplay(callerNumber),
                fontSize = 18.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Accept / Reject row ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .padding(bottom = 80.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reject
                CircleButton(
                    onClick = { viewModel.rejectCall() },
                    backgroundColor = CallRed,
                    pressedColor = CallRedDark,
                    size = 80.dp,
                    icon = Icons.Filled.CallEnd,
                    label = "Decline"
                )

                // Accept
                CircleButton(
                    onClick = { viewModel.acceptCall() },
                    gradientColors = listOf(CallGreen, CallGreenDark),
                    backgroundColor = CallGreen,
                    pressedColor = CallGreenDark,
                    size = 80.dp,
                    icon = Icons.Filled.Call,
                    label = "Accept"
                )
            }
        }
    }
}
