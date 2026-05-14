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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Three side-by-side numeric inputs for DD / MM / YYYY. Matches the Yil
 * settings card aesthetic (dark surface, hairline gold border, AmberGold
 * accents).
 *
 * Accepts nullable Int? so callers can start with empty fields for unset
 * dates. onChange fires with nullable Int? values — callers should treat
 * a null component as "not yet entered".
 */
@Composable
fun DateNumberInputs(
    day: Int?,
    month: Int?,
    year: Int?,
    onChange: (day: Int?, month: Int?, year: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dayText by remember(day) { mutableStateOf(day?.toString()?.padStart(2, '0') ?: "") }
    var monthText by remember(month) { mutableStateOf(month?.toString()?.padStart(2, '0') ?: "") }
    var yearText by remember(year) { mutableStateOf(year?.toString() ?: "") }

    fun emit() {
        val d = dayText.toIntOrNull()
        val m = monthText.toIntOrNull()
        val y = yearText.toIntOrNull()
        onChange(d, m, y)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DateField(
            value = dayText,
            label = "DD",
            maxLen = 2,
            onValueChange = { dayText = it; emit() },
            modifier = Modifier.weight(1f),
        )
        DateField(
            value = monthText,
            label = "MM",
            maxLen = 2,
            onValueChange = { monthText = it; emit() },
            modifier = Modifier.weight(1f),
        )
        DateField(
            value = yearText,
            label = "YYYY",
            maxLen = 4,
            onValueChange = { yearText = it; emit() },
            modifier = Modifier.weight(1.4f),
        )
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
