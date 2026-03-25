package com.example.myapplication.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call

/**
 * A circular action button with:
 * - Spring-based press scale animation
 * - Animated background color change on press
 * - Optional label below
 * - Optional gradient background
 */
@Composable
fun CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    pressedColor: Color = backgroundColor.copy(alpha = 0.75f),
    gradientColors: List<Color>? = null,
    size: Dp = 72.dp,
    icon: ImageVector,
    iconTint: Color = Color.White,
    iconSize: Dp = size * 0.42f,
    label: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 550f),
        label = "circleButtonScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isPressed) pressedColor else backgroundColor,
        label = "circleButtonBg"
    )

    val backgroundModifier = if (gradientColors != null) {
        Modifier.background(
            brush = Brush.radialGradient(gradientColors),
            shape = CircleShape
        )
    } else {
        Modifier.background(color = bgColor, shape = CircleShape)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .scale(scale)
                .clip(CircleShape)
                .then(backgroundModifier)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.25f)),
                    enabled = enabled,
                    onClick = onClick
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
                modifier = Modifier.size(iconSize)
            )
        }

        label?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                letterSpacing = 0.3.sp
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun CircleButtonPreview() {
    // Simple preview on a dark background so the white icon contrasts
    Box(modifier = Modifier.background(Color(0xFF081408)).padding(16.dp)) {
        CircleButton(
            onClick = {},
            backgroundColor = Color(0xFF1EB980),
            gradientColors = null,
            size = 64.dp,
            icon = Icons.Filled.Call,
            label = "Call",
            enabled = true
        )
    }
}

