package com.example.lifedots.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Three side-by-side numeric inputs for DD / MM / YYYY. Fully CONTROLLED —
 * caller owns the text state per field and receives every digit-filtered
 * update via the per-field callbacks. No internal `remember`, no surprise
 * state resets across recompositions.
 */
@Composable
fun DateNumberInputs(
    dayText: String,
    monthText: String,
    yearText: String,
    onDayChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DateField(value = dayText, label = "DD", maxLen = 2,
            onValueChange = onDayChange, modifier = Modifier.weight(1f))
        DateField(value = monthText, label = "MM", maxLen = 2,
            onValueChange = onMonthChange, modifier = Modifier.weight(1f))
        DateField(value = yearText, label = "YYYY", maxLen = 4,
            onValueChange = onYearChange, modifier = Modifier.weight(1.4f))
    }
}

@Composable
private fun DateField(
    value: String,
    label: String,
    maxLen: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(maxLen)
            onValueChange(digits)
        },
        label = { Text(label, color = Color(0xB3FFC62E), fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(
            color = Color(0xFFFFC62E),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFFFC62E),
            unfocusedBorderColor = Color(0x66FFC62E),
            cursorColor = Color(0xFFFFC62E),
            focusedContainerColor = Color(0xFF14110B),
            unfocusedContainerColor = Color(0xFF14110B),
        ),
        modifier = modifier,
    )
}
