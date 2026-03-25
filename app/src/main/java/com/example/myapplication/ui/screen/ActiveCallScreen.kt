package com.example.myapplication.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.myapplication.ui.theme.CallRed
import com.example.myapplication.ui.theme.CallRedDark
import com.example.myapplication.ui.theme.CardBackground
import com.example.myapplication.ui.theme.SurfaceElevated
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.utils.formatPhoneDisplay
import com.example.myapplication.viewmodel.CallViewModel


@Composable
fun ActiveCallScreen(viewModel: CallViewModel) {
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val duration by viewModel.callDurationSeconds.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val isOnHold by viewModel.isOnHold.collectAsStateWithLifecycle()

    val activeState = callState as? CallState.Active
    val number = activeState?.number ?: ""
    val callerName = activeState?.callerName ?: ""
    val isIncoming = activeState?.isIncoming ?: false

    val isEnded = callState is CallState.Ended

    // Animated hold overlay tint
    val holdOverlay by animateColorAsState(
        targetValue = if (isOnHold) Color.Black.copy(alpha = 0.35f) else Color.Transparent,
        animationSpec = tween(400),
        label = "holdOverlay"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF081408), BackgroundDark, BackgroundDark),
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            // ── "On Hold" / direction badge ────────────────────────────────
            AnimatedContent(
                targetState = when {
                    isEnded -> "ended"
                    isOnHold -> "hold"
                    else -> if (isIncoming) "incoming" else "outgoing"
                },
                transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) },
                label = "badge"
            ) { badge ->
                val (text, bg) = when (badge) {
                    "hold" -> "On Hold" to Color(0xFF332200)
                    "ended" -> "Call Ended" to Color(0xFF330011)
                    "incoming" -> "Incoming Call" to Color(0xFF0D2A0D)
                    else -> "Outgoing Call" to Color(0xFF0D2A0D)
                }
                val textColor = when (badge) {
                    "hold" -> Color(0xFFFFBB33)
                    "ended" -> CallRed.copy(alpha = 0.9f)
                    else -> CallGreen
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(text = text, color = textColor, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Avatar ─────────────────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFF1C2E1C), Color(0xFF0D180D)))
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = CallGreen.copy(alpha = 0.7f),
                    modifier = Modifier.size(62.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Name ──────────────────────────────────────────────────────
            if (callerName.isNotEmpty()) {
                Text(
                    text = callerName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatPhoneDisplay(number),
                    fontSize = 16.sp,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            } else {
                Text(
                    text = formatPhoneDisplay(number),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    color = TextPrimary,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Live Call Timer ────────────────────────────────────────────
            AnimatedContent(
                targetState = viewModel.formatDuration(duration),
                transitionSpec = {
                    fadeIn(tween(200)).togetherWith(fadeOut(tween(150)))
                },
                label = "timer"
            ) { time ->
                Text(
                    text = if (isEnded) viewModel.formatDuration(
                        (callState as? CallState.Ended)?.durationSeconds ?: duration
                    ) else time,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light,
                    color = if (isOnHold) Color(0xFFFFBB33) else CallGreen,
                    letterSpacing = 3.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Control row: Mute · Hold · Speaker ────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ── Mute ──────────────────────────────────────────────
                    val muteColor by animateColorAsState(
                        targetValue = if (isMuted) Color(0xFFFF6B6B) else SurfaceElevated,
                        label = "muteColor"
                    )
                    CircleButton(
                        onClick = { viewModel.toggleMute() },
                        backgroundColor = muteColor,
                        size = 64.dp,
                        icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        iconTint = if (isMuted) Color.White else TextSecondary,
                        label = if (isMuted) "Unmute" else "Mute"
                    )

                    // ── Hold ──────────────────────────────────────────────
                    val holdColor by animateColorAsState(
                        targetValue = if (isOnHold) Color(0xFF664400) else SurfaceElevated,
                        label = "holdColor"
                    )
                    CircleButton(
                        onClick = { viewModel.toggleHold() },
                        backgroundColor = holdColor,
                        size = 64.dp,
                        icon = if (isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        iconTint = if (isOnHold) Color(0xFFFFBB33) else TextSecondary,
                        label = if (isOnHold) "Resume" else "Hold"
                    )

                    // ── Speaker ───────────────────────────────────────────
                    val speakerColor by animateColorAsState(
                        targetValue = if (isSpeakerOn) Color(0xFF1A4A2A) else SurfaceElevated,
                        label = "speakerColor"
                    )
                    CircleButton(
                        onClick = { viewModel.toggleSpeaker() },
                        backgroundColor = speakerColor,
                        size = 64.dp,
                        icon = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        iconTint = if (isSpeakerOn) CallGreen else TextSecondary,
                        label = if (isSpeakerOn) "Speaker" else "Speaker"
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── End Call button ────────────────────────────────────────────
            CircleButton(
                onClick = { viewModel.endCall() },
                backgroundColor = CallRed,
                pressedColor = CallRedDark,
                size = 80.dp,
                icon = Icons.Filled.CallEnd,
                label = "End Call",
                enabled = !isEnded
            )

            Spacer(modifier = Modifier.height(60.dp))
        }

        // ── Translucent hold overlay ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(holdOverlay)
        )
    }
}
