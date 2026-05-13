package com.example.lifedots.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.example.lifedots.ui.theme.BrandColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ModeTogglePill(
    leftLabel: String,
    rightLabel: String,
    isLeftSelected: Boolean,
    onSelect: (left: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(27.dp),
        color = BrandColors.InkBlackElevated,    // raised black pill background
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillSide(
                label = leftLabel,
                selected = isLeftSelected,
                onClick = { onSelect(true) },
                modifier = Modifier.weight(1f),
            )
            PillSide(
                label = rightLabel,
                selected = !isLeftSelected,
                onClick = { onSelect(false) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PillSide(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val bg = if (selected) BrandColors.AmberGold else Color.Transparent
    val textColor = if (selected) BrandColors.InkBlack else BrandColors.GoldenMuted
    val textWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal

    Box(
        modifier = modifier
            .padding(4.dp)
            .height(46.dp)
            .clip(shape)
            .then(if (selected) Modifier.shadow(elevation = 2.dp, shape = shape) else Modifier)
            .background(color = bg, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = textWeight),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ModeTogglePillPreview() {
    var leftSelected by remember { mutableStateOf(false) }
    ModeTogglePill(
        leftLabel = "Avtomatik",
        rightLabel = "Bir martalik",
        isLeftSelected = leftSelected,
        onSelect = { leftSelected = it },
        modifier = Modifier.padding(16.dp),
    )
}
