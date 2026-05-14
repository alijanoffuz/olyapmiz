package com.example.lifedots.ui.components

import androidx.compose.runtime.Composable
import com.example.lifedots.preferences.Event
import com.example.lifedots.preferences.Goal

/**
 * Thin wrapper around [GoalEditorDialog] for the Umr-only Event flow.
 * Reuses the entire parchment dialog and 3-part date input — the only
 * differences (titles, save-button label, hint text) are toggled by
 * passing isEvent = true.
 *
 * Events and Goals are structurally identical (id, title, targetDate,
 * color); we convert between them at the boundary so the dialog
 * implementation stays a single file.
 */
@Composable
fun EventEditorDialog(
    event: Event? = null,
    onSave: (Event) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val asGoal = event?.let {
        Goal(id = it.id, title = it.title, targetDate = it.targetDate, color = it.color)
    }
    GoalEditorDialog(
        goal = asGoal,
        onSave = { goal ->
            onSave(
                Event(
                    id = goal.id,
                    title = goal.title,
                    targetDate = goal.targetDate,
                    color = goal.color,
                )
            )
        },
        onDelete = onDelete,
        onDismiss = onDismiss,
        isEvent = true,
    )
}
