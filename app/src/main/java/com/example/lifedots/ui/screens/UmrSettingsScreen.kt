package com.example.lifedots.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.UmrVisualMode
import com.example.lifedots.preferences.WallpaperSettings
import com.example.lifedots.ui.components.WhoTab
import com.example.lifedots.ui.components.WhoTabs
import com.example.lifedots.ui.components.WheelDatePicker
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
fun UmrSettingsScreen(
    settings: WallpaperSettings,
    preferences: LifeDotsPreferences,
    modifier: Modifier = Modifier,
) {
    var showEditor by remember { mutableStateOf(false) }
    var editingWho by remember { mutableStateOf(WhoTab.ME) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LifeDataCard(
            meMs = settings.umrSettings.birthdayEpochMs,
            dadMs = settings.umrSettings.dadBirthdayEpochMs,
            momMs = settings.umrSettings.momBirthdayEpochMs,
            onEdit = { showEditor = true; editingWho = WhoTab.ME },
        )

        VisualizationToggleSection(
            current = settings.umrSettings.visualMode,
            onChange = { preferences.setUmrVisualMode(it) },
        )

        TransparencySection(
            livedAlpha = settings.umrSettings.livedAlpha,
            emptyAlpha = settings.umrSettings.emptyAlpha,
            onLivedChange = { preferences.setUmrLivedAlpha(it) },
            onEmptyChange = { preferences.setUmrEmptyAlpha(it) },
        )

        // Theme / background / position / animation reuse Yil's section
        // composables — they drive shared WallpaperSettings fields.
        // For now the user can switch back to Yil mode at the top to
        // adjust those. A future PR can pull them in here.
    }

    if (showEditor) {
        LifeDataEditorSheet(
            settings = settings,
            preferences = preferences,
            initialWho = editingWho,
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun LifeDataCard(
    meMs: Long,
    dadMs: Long,
    momMs: Long,
    onEdit: () -> Unit,
) {
    val fmt = remember { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF14110B),
        border = BorderStroke(1.dp, Color(0x33FFC62E)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Life Data",
                color = Color(0xFFFFC62E),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            DobRow("Me", meMs, fmt)
            DobRow("Dad", dadMs, fmt)
            DobRow("Mom", momMs, fmt)
        }
    }
}

@Composable
private fun DobRow(label: String, ms: Long, fmt: SimpleDateFormat) {
    Row {
        Text("$label  ", color = Color(0x99FFFFFF), fontSize = 14.sp)
        Text(
            if (ms > 0L) fmt.format(Date(ms)) else "Not set",
            color = Color(0xFFEDE8DE),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun VisualizationToggleSection(
    current: UmrVisualMode,
    onChange: (UmrVisualMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "VISUALIZATION",
            color = Color(0xFFFFC62E),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0x14FFFFFF),
            border = BorderStroke(1.dp, Color(0x33FFC62E)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                listOf(UmrVisualMode.DOTS to "Dots", UmrVisualMode.X_MARKS to "X-marks").forEach { (mode, label) ->
                    val isActive = mode == current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (isActive) Color(0xFF1B1A16) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onChange(mode) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (isActive) Color(0xFFFFC62E) else Color(0xFFC7A35F),
                            fontSize = 14.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransparencySection(
    livedAlpha: Float,
    emptyAlpha: Float,
    onLivedChange: (Float) -> Unit,
    onEmptyChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "TRANSPARENCY",
            color = Color(0xFFFFC62E),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF14110B),
            border = BorderStroke(1.dp, Color(0x33FFC62E)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lived", color = Color(0xFFEDE8DE), modifier = Modifier.weight(1f))
                    Text("${(livedAlpha * 100).toInt()}%", color = Color(0xFFFFC62E))
                }
                Slider(value = livedAlpha, onValueChange = onLivedChange, valueRange = 0f..1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Empty", color = Color(0xFFEDE8DE), modifier = Modifier.weight(1f))
                    Text("${(emptyAlpha * 100).toInt()}%", color = Color(0xFFFFC62E))
                }
                Slider(value = emptyAlpha, onValueChange = onEmptyChange, valueRange = 0f..1f)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LifeDataEditorSheet(
    settings: WallpaperSettings,
    preferences: LifeDotsPreferences,
    initialWho: WhoTab,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initialWho) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = LocalDate.now()

    fun msToLocal(ms: Long): LocalDate =
        if (ms <= 0L) today
        else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun localToMs(d: LocalDate): Long =
        d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val current = when (selected) {
        WhoTab.ME  -> msToLocal(settings.umrSettings.birthdayEpochMs)
        WhoTab.DAD -> msToLocal(settings.umrSettings.dadBirthdayEpochMs)
        WhoTab.MOM -> msToLocal(settings.umrSettings.momBirthdayEpochMs)
    }
    var picked by remember(selected) { mutableStateOf(current) }
    val isFuture = picked.isAfter(today)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0A0906),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Enter your life data",
                    color = Color(0xFFEDE8DE),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val ms = localToMs(picked)
                        when (selected) {
                            WhoTab.ME  -> preferences.setUmrBirthday(ms)
                            WhoTab.DAD -> preferences.setUmrDadBirthday(ms)
                            WhoTab.MOM -> preferences.setUmrMomBirthday(ms)
                        }
                        onDismiss()
                    },
                    enabled = !isFuture,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFC62E)),
                ) { Text("Done") }
            }
            WhoTabs(selected = selected, onSelected = { selected = it })
            Text(
                text = "Date of Birth",
                color = Color(0xFFFFC62E),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            WheelDatePicker(
                initialDay = picked.dayOfMonth,
                initialMonth = picked.monthValue,
                initialYear = picked.year,
                onChange = { d, m, y ->
                    picked = runCatching { LocalDate.of(y, m, d.coerceAtMost(28)) }
                        .getOrElse { LocalDate.of(y, m, 1) }
                },
            )
            if (isFuture) {
                Text(
                    text = "Date can't be in the future.",
                    color = Color(0xFFE53935),
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
