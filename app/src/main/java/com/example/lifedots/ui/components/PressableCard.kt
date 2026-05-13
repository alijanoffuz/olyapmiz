package com.example.lifedots.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A Surface that scales down slightly when pressed and fires a haptic
 * via the provided UxFeedback on each click. Use this for cards,
 * tappable rows, and anything that feels card-shaped.
 *
 * Scale animation: 1.0 -> 0.97 -> 1.0 with a soft spring.
 * Haptic: feedback.click() on each onClick invocation.
 *
 * Defaults to MaterialTheme.colorScheme.surface / onSurface — i.e.
 * brand elevated-black / off-white in this app.
 */
@Composable
fun PressableCard(
    onClick: () -> Unit,
    feedback: UxFeedback,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "PressableCardScale",
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    feedback.click()
                    onClick()
                },
            ),
        shape = shape,
        color = color,
        contentColor = contentColor,
    ) {
        content()
    }
}
