package com.example.myapplication.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.DialKeyBackground
import com.example.myapplication.ui.theme.DialKeyPressedBackground
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextTertiary

/**
 * A single dial-pad key.
 * Vibration is handled internally — the parent does NOT need to manage haptics.
 * - Tap         → HapticFeedbackType.TextHandleMove (short, crisp)
 * - Long-press  → HapticFeedbackType.LongPress      (stronger buzz)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialKey(
    digit: String,
    subText: String = "",
    onPress: (String) -> Unit,
    onLongPress: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 70.dp,
) {
    val haptic = LocalHapticFeedback.current          // ← self-contained
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 650f),
        label = "dialKeyScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isPressed) DialKeyPressedBackground else DialKeyBackground,
        label = "dialKeyBg"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(bgColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = Color.White.copy(alpha = 0.15f)
                ),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPress(digit)
                },
                onLongClick = onLongPress?.let { handler ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        handler(digit)
                    }
                }
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 28.sp
            )
            if (subText.isNotEmpty()) {
                Text(
                    text = subText,
                    color = TextTertiary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp,
                    lineHeight = 10.sp
                )
            }
        }
    }
}