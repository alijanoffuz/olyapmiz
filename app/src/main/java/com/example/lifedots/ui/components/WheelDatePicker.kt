package com.example.lifedots.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

private val InkBlack = Color(0xFF0A0906)
private val InkBlackElevated = Color(0xFF14110B)
private val HairlineGold = Color(0x4DFFC62E)
private val AmberGold = Color(0xFFFFC62E)
private val OffWhite = Color(0xFFEDE8DE)

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private const val ROW_HEIGHT_DP = 64
private const val VISIBLE_ROWS = 5
private val COLUMN_HEIGHT_DP = (ROW_HEIGHT_DP * VISIBLE_ROWS).dp

@Composable
fun WheelDatePicker(
    initialDay: Int,
    initialMonth: Int,
    initialYear: Int,
    onChange: (day: Int, month: Int, year: Int) -> Unit,
    modifier: Modifier = Modifier,
    minYear: Int = LocalDate.now().year - 120,
    maxYear: Int = LocalDate.now().year,
) {
    val years = remember(minYear, maxYear) { (minYear..maxYear).toList() }
    val months = remember { (1..12).toList() }

    val yearState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialYear - minYear).coerceAtLeast(0)
    )
    val monthState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialMonth - 1).coerceAtLeast(0)
    )
    val dayState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialDay - 1).coerceAtLeast(0)
    )

    val selectedYear by remember {
        derivedStateOf { years.getOrNull(yearState.firstVisibleItemIndex) ?: initialYear }
    }
    val selectedMonth by remember {
        derivedStateOf { months.getOrNull(monthState.firstVisibleItemIndex) ?: initialMonth }
    }
    val dayCount by remember {
        derivedStateOf {
            runCatching { YearMonth.of(selectedYear, selectedMonth).lengthOfMonth() }.getOrDefault(31)
        }
    }
    val days = remember(dayCount) { (1..dayCount).toList() }
    val selectedDay by remember {
        derivedStateOf {
            val idx = dayState.firstVisibleItemIndex.coerceAtMost(days.size - 1)
            days.getOrNull(idx) ?: initialDay
        }
    }

    // Emit changes whenever any selection settles.
    LaunchedEffect(yearState, monthState, dayState) {
        snapshotFlow { Triple(selectedDay, selectedMonth, selectedYear) }
            .collectLatest { (d, m, y) -> onChange(d, m, y) }
    }

    Column(modifier = modifier.wrapContentHeight()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelColumn(
                items = days,
                state = dayState,
                formatter = { it.toString().padStart(2, '0') },
                modifier = Modifier.weight(1f),
            )
            ColonSeparator()
            WheelColumn(
                items = months,
                state = monthState,
                formatter = { MONTH_NAMES[it - 1] },
                modifier = Modifier.weight(1.4f),
            )
            ColonSeparator()
            WheelColumn(
                items = years,
                state = yearState,
                formatter = { it.toString() },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ColumnLabel("DD", Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            ColumnLabel("MM", Modifier.weight(1.4f))
            Spacer(modifier = Modifier.width(8.dp))
            ColumnLabel("YYYY", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColonSeparator() {
    Text(
        text = ":",
        color = AmberGold.copy(alpha = 0.85f),
        fontSize = 22.sp,
        fontWeight = FontWeight.Light,
    )
}

@Composable
private fun ColumnLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        color = AmberGold.copy(alpha = 0.7f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun <T> WheelColumn(
    items: List<T>,
    state: LazyListState,
    formatter: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    Surface(
        modifier = modifier.height(COLUMN_HEIGHT_DP),
        shape = RoundedCornerShape(20.dp),
        color = InkBlack,
        border = BorderStroke(1.dp, HairlineGold),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(InkBlackElevated, InkBlack, InkBlackElevated)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Centre selection chip overlay — drawn behind the list rows.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT_DP.dp)
                    .padding(horizontal = 8.dp)
                    .background(
                        color = Color(0x14FFFFFF),
                        shape = RoundedCornerShape(14.dp),
                    )
            )
            LazyColumn(
                state = state,
                flingBehavior = fling,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val pad = (VISIBLE_ROWS / 2)
                items(pad) { Spacer(modifier = Modifier.height(ROW_HEIGHT_DP.dp)) }
                items(items) { item ->
                    val idx = items.indexOf(item)
                    val delta = remember(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
                        abs(idx - state.firstVisibleItemIndex) +
                            state.firstVisibleItemScrollOffset / ROW_HEIGHT_DP.toFloat()
                    }
                    val rowAlpha = when {
                        delta < 0.5f -> 1f
                        delta < 1.5f -> 0.78f
                        delta < 2.5f -> 0.50f
                        else -> 0f
                    }
                    val rowSize = when {
                        delta < 0.5f -> 28.sp
                        delta < 1.5f -> 18.sp
                        else -> 16.sp
                    }
                    val rowColor = if (delta < 0.5f) AmberGold else OffWhite
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT_DP.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = formatter(item),
                            color = rowColor.copy(alpha = rowAlpha),
                            fontSize = rowSize,
                            fontWeight = if (delta < 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                items(pad) { Spacer(modifier = Modifier.height(ROW_HEIGHT_DP.dp)) }
            }
        }
    }
}
