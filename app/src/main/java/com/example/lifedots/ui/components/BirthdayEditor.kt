package com.example.lifedots.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifedots.ui.theme.BrandColors
import java.util.Calendar

/**
 * Inline birthday editor — three number fields + Save button.
 *
 * Validation: day 1..31 (per-month aware on Save), month 1..12,
 * year 1900..2099. Save is disabled until all fields parse to a
 * real calendar date. On valid Save, calls onSave(epochMs); on
 * invalid Save attempt, shows a specific error message.
 *
 * Auto-advance: typing the 2nd digit in Day jumps to Month;
 * 2nd digit in Month jumps to Year.
 */
@Composable
fun BirthdayEditor(
    initialEpochMs: Long,
    onSave: (epochMs: Long) -> Unit,
    feedback: UxFeedback,
    modifier: Modifier = Modifier,
) {
    val initialCal = remember {
        Calendar.getInstance().apply { timeInMillis = initialEpochMs.coerceAtLeast(1L) }
    }

    var day by remember {
        mutableStateOf(if (initialEpochMs > 0L) initialCal.get(Calendar.DAY_OF_MONTH).toString() else "")
    }
    var month by remember {
        mutableStateOf(if (initialEpochMs > 0L) (initialCal.get(Calendar.MONTH) + 1).toString() else "")
    }
    var year by remember {
        mutableStateOf(if (initialEpochMs > 0L) initialCal.get(Calendar.YEAR).toString() else "")
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val monthFocus = remember { FocusRequester() }
    val yearFocus = remember { FocusRequester() }
    val dayFocus = remember { FocusRequester() }

    val parsedMs = remember(day, month, year) { parseAndValidate(day, month, year) }
    val isValid = parsedMs != null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NumberCell(
                value = day,
                placeholder = "DD",
                label = "Day",
                onValueChange = { new ->
                    val filtered = new.filter { it.isDigit() }.take(2)
                    day = filtered
                    if (filtered.length == 2) monthFocus.requestFocus()
                },
                focusRequester = dayFocus,
                modifier = Modifier.weight(1f),
                isError = day.isNotEmpty() && (day.toIntOrNull()?.let { it !in 1..31 } ?: false),
            )
            NumberCell(
                value = month,
                placeholder = "MM",
                label = "Month",
                onValueChange = { new ->
                    val filtered = new.filter { it.isDigit() }.take(2)
                    month = filtered
                    if (filtered.length == 2) yearFocus.requestFocus()
                },
                focusRequester = monthFocus,
                modifier = Modifier.weight(1f),
                isError = month.isNotEmpty() && (month.toIntOrNull()?.let { it !in 1..12 } ?: false),
            )
            NumberCell(
                value = year,
                placeholder = "YYYY",
                label = "Year",
                onValueChange = { new ->
                    year = new.filter { it.isDigit() }.take(4)
                },
                focusRequester = yearFocus,
                modifier = Modifier.weight(1.4f),
                isError = year.length == 4 && (year.toIntOrNull()?.let { it !in 1900..2099 } ?: false),
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage ?: "",
                color = Color(0xFFE53935),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val ms = parseAndValidate(day, month, year)
                if (ms != null) {
                    errorMessage = null
                    feedback.confirm()
                    onSave(ms)
                } else {
                    errorMessage = errorFor(day, month, year)
                }
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColors.AmberGold,
                contentColor = BrandColors.InkBlack,
                disabledContainerColor = BrandColors.AmberGoldWash,
                disabledContentColor = BrandColors.GoldenMuted,
            ),
        ) {
            Text(
                "Save",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun NumberCell(
    value: String,
    placeholder: String,
    label: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, textAlign = TextAlign.Center) },
        label = { Text(label) },
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = BrandColors.OffWhite,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.focusRequester(focusRequester),
        isError = isError,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = BrandColors.InkBlackElevated,
            unfocusedContainerColor = BrandColors.InkBlackElevated,
            focusedIndicatorColor = BrandColors.AmberGold,
            unfocusedIndicatorColor = BrandColors.HairlineGold,
            errorIndicatorColor = Color(0xFFE53935),
            focusedLabelColor = BrandColors.AmberGold,
            unfocusedLabelColor = BrandColors.GoldenMuted,
            cursorColor = BrandColors.AmberGold,
        ),
    )
}

private fun parseAndValidate(day: String, month: String, year: String): Long? {
    val d = day.toIntOrNull() ?: return null
    val m = month.toIntOrNull() ?: return null
    val y = year.toIntOrNull() ?: return null
    if (y !in 1900..2099) return null
    if (m !in 1..12) return null
    val cal = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, y)
        set(Calendar.MONTH, m - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    if (d !in 1..maxDay) return null
    cal.set(Calendar.DAY_OF_MONTH, d)
    return cal.timeInMillis
}

private fun errorFor(day: String, month: String, year: String): String? {
    val d = day.toIntOrNull()
    val m = month.toIntOrNull()
    val y = year.toIntOrNull()
    return when {
        d == null || m == null || y == null -> "Fill all three fields"
        y !in 1900..2099 -> "Year must be 1900–2099"
        m !in 1..12 -> "Month must be 1–12"
        d !in 1..31 -> "Day must be 1–31"
        else -> {
            val cal = Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, y); set(Calendar.MONTH, m - 1); set(Calendar.DAY_OF_MONTH, 1)
            }
            "Invalid date — ${monthName(m)} $y has only ${cal.getActualMaximum(Calendar.DAY_OF_MONTH)} days"
        }
    }
}

private fun monthName(month: Int): String = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)[(month - 1).coerceIn(0, 11)]
