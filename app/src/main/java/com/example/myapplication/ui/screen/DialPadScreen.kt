package com.example.myapplication.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.ui.components.DialKey
import com.example.myapplication.ui.theme.BackgroundDark
import com.example.myapplication.ui.theme.CallGreen
import com.example.myapplication.ui.theme.CallGreenDark
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.utils.formatPhoneDisplay
import com.example.myapplication.viewmodel.CallViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

private val DIAL_KEYS = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to ""
)

private fun numberFontSize(length: Int) = when {
    length >= 15 -> 20
    length >= 13 -> 22
    length >= 11 -> 26
    length >= 9  -> 30
    length >= 7  -> 34
    length >= 5  -> 38
    else         -> 42
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun DialPadScreen(
    viewModel: CallViewModel,
    context: Context
) {
    val dialedNumber by viewModel.dialedNumber.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current          // used only for non-key actions
    val callPermission = rememberPermissionState(Manifest.permission.CALL_PHONE)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Header ─────────────────────────────────────────────────────
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Keypad",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.simulateIncomingCall()
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CallGreen),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, CallGreen.copy(alpha = 0.5f)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneCallback,
                        contentDescription = "Simulate incoming call",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(text = "Incoming", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            // ── Push everything to the bottom ──────────────────────────────
            Spacer(modifier = Modifier.weight(1f))

            // ── Number display — always single line ────────────────────────
            val contactName = viewModel.resolveContactName(dialedNumber)
            val fontSize = numberFontSize(dialedNumber.length)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Crossfade(
                    targetState = dialedNumber,
                    animationSpec = tween(durationMillis = 80),
                    label = "numberDisplay"
                ) { number ->
                    Text(
                        text = if (number.isEmpty()) "" else formatPhoneDisplay(number),
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Thin,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        letterSpacing = 3.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                AnimatedVisibility(
                    visible = contactName.isNotEmpty(),
                    enter = fadeIn(tween(180)),
                    exit  = fadeOut(tween(120))
                ) {
                    Text(
                        text = contactName,
                        fontSize = 15.sp,
                        color = CallGreen,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Dial-pad grid ──────────────────────────────────────────────
            // NOTE: DialKey handles its own haptics internally.
            // No haptic calls needed here.
            DIAL_KEYS.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { (digit, sub) ->
                        DialKey(
                            digit = digit,
                            subText = sub,
                            size = 76.dp,
                            onPress = { d ->
                                viewModel.appendDigit(d)
                            },
                            // Long-press "0" → "+" (iOS style). Haptic handled inside DialKey.
                            onLongPress = if (digit == "0") { _ ->
                                viewModel.appendDigit("+")
                            } else null
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Bottom row: placeholder | Call | Backspace ─────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(76.dp))

                // ── Call button ────────────────────────────────────────────
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(CallGreen, CallGreenDark))
                        )
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(
                                bounded = true,
                                color = Color.White.copy(alpha = 0.3f)
                            ),
                            enabled = dialedNumber.isNotEmpty(),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (callPermission.status.isGranted) {
                                    try {
                                        val intent = Intent(
                                            Intent.ACTION_CALL,
                                            Uri.parse("tel:$dialedNumber")
                                        )
                                        context.startActivity(intent)
                                    } catch (e: SecurityException) {
                                        callPermission.launchPermissionRequest()
                                    }
                                } else {
                                    callPermission.launchPermissionRequest()
                                }
                                viewModel.startOutgoingCall()
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // ── Backspace ──────────────────────────────────────────────
                Box(
                    modifier = Modifier.size(76.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = dialedNumber.isNotEmpty(),
                        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f),
                        exit  = fadeOut(tween(100)) + scaleOut(targetScale = 0.8f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(
                                        bounded = true,
                                        color = Color.White.copy(alpha = 0.15f)
                                    ),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.deleteLastDigit()
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.clearNumber()
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}