package com.example.lifedots.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Calendar

private enum class PickerStep { YEAR, MONTH, DAY }

@Composable
fun DatePickerDialog(
    initialDate: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initialCalendar = remember {
        Calendar.getInstance().apply { timeInMillis = initialDate }
    }

    var selectedYear by remember { mutableIntStateOf(initialCalendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(initialCalendar.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableIntStateOf(initialCalendar.get(Calendar.DAY_OF_MONTH)) }
    var step by remember { mutableStateOf(PickerStep.YEAR) }

    val monthNamesLong = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val monthNamesShort = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val yearRange = remember { (1900..currentYear + 10).toList() }

    val daysInMonth = remember(selectedYear, selectedMonth) {
        val cal = Calendar.getInstance()
        cal.set(selectedYear, selectedMonth, 1)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val firstDayOfWeek = remember(selectedYear, selectedMonth) {
        val cal = Calendar.getInstance()
        cal.set(selectedYear, selectedMonth, 1)
        cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = when (step) {
                        PickerStep.YEAR -> "Select Year"
                        PickerStep.MONTH -> "Select Month"
                        PickerStep.DAY -> "Select Day"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Breadcrumb of current selection — tap a segment to jump back to that step.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CrumbText(
                        text = selectedYear.toString(),
                        active = step == PickerStep.YEAR,
                        onClick = { step = PickerStep.YEAR }
                    )
                    if (step != PickerStep.YEAR) {
                        Text(" › ", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        CrumbText(
                            text = monthNamesLong[selectedMonth],
                            active = step == PickerStep.MONTH,
                            onClick = { step = PickerStep.MONTH }
                        )
                    }
                    if (step == PickerStep.DAY) {
                        Text(" › ", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        CrumbText(
                            text = selectedDay.toString(),
                            active = true,
                            onClick = { }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (step) {
                    PickerStep.YEAR -> {
                        val initialIndex = (yearRange.indexOf(selectedYear)).coerceAtLeast(0)
                        val gridState = rememberLazyGridState(
                            initialFirstVisibleItemIndex = (initialIndex - 6).coerceAtLeast(0)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(yearRange) { year ->
                                val isSelected = year == selectedYear
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .height(40.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            selectedYear = year
                                            selectedDay = minOf(selectedDay, daysInMonth)
                                            step = PickerStep.MONTH
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = year.toString(),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    PickerStep.MONTH -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(monthNamesShort.size) { idx ->
                                val isSelected = idx == selectedMonth
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .height(48.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            selectedMonth = idx
                                            selectedDay = minOf(selectedDay, daysInMonth)
                                            step = PickerStep.DAY
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = monthNamesShort[idx],
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    PickerStep.DAY -> {
                        // Day of week headers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { day ->
                                Text(
                                    text = day,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.width(36.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val totalCells = firstDayOfWeek + daysInMonth
                        val rows = (totalCells + 6) / 7

                        Column {
                            for (row in 0 until rows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    for (col in 0 until 7) {
                                        val cellIndex = row * 7 + col
                                        val dayNum = cellIndex - firstDayOfWeek + 1

                                        if (dayNum in 1..daysInMonth) {
                                            val isSelected = dayNum == selectedDay
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary
                                                        else Color.Transparent
                                                    )
                                                    .clickable { selectedDay = dayNum },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dayNum.toString(),
                                                    fontSize = 14.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        } else {
                                            Box(modifier = Modifier.size(36.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        enabled = step == PickerStep.DAY,
                        onClick = {
                            val calendar = Calendar.getInstance()
                            calendar.set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            onDateSelected(calendar.timeInMillis)
                        }
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@Composable
private fun CrumbText(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        color = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !active, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
