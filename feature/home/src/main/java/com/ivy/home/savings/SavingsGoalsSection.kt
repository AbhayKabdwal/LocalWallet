package com.ivy.home.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.format
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Ivy

/**
 * The savings goals block in the home menu.
 *
 * This replaces the single "savings goal" figure, which could only express one
 * number for the whole wallet. Someone saving for a phone and a monitor at the
 * same time could not tell from it how far along either was - the two were
 * added together into a total that described neither.
 */
@Composable
fun ColumnScope.SavingsGoalsSection(
    state: SavingsGoalsState,
    onEvent: (SavingsGoalEvent) -> Unit,
) {
    var editing by remember { mutableStateOf<SavingsGoalUi?>(null) }
    var adding by remember { mutableStateOf(false) }
    var contributingTo by remember { mutableStateOf<SavingsGoalUi?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag("savings_goals_header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Savings goals",
            style = UI.typo.b1.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { adding = true }) { Text("Add") }
    }

    if (state.goals.isEmpty()) {
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = "Nothing yet. Add what you are saving up for - a phone, a " +
                "monitor - and track them separately.",
            style = UI.typo.c.style(color = Gray),
        )
    }

    state.goals.forEach { goal ->
        GoalRow(
            goal = goal,
            onTopUp = { contributingTo = goal },
            onEdit = { editing = goal },
        )
    }

    if (adding) {
        GoalDialog(
            title = "New savings goal",
            initialName = "",
            initialTarget = "",
            initialSaved = "",
            confirmLabel = "Create",
            onDismiss = { adding = false },
            onConfirm = { name, target, saved ->
                onEvent(SavingsGoalEvent.Add(name, target, saved))
                adding = false
            },
        )
    }

    editing?.let { goal ->
        GoalDialog(
            title = "Edit goal",
            initialName = goal.name,
            initialTarget = goal.target.toString(),
            initialSaved = goal.saved.toString(),
            confirmLabel = "Save",
            onDelete = {
                onEvent(SavingsGoalEvent.Delete(goal.id))
                editing = null
            },
            onDismiss = { editing = null },
            onConfirm = { name, target, saved ->
                onEvent(SavingsGoalEvent.Edit(goal.id, name, target, saved))
                editing = null
            },
        )
    }

    contributingTo?.let { goal ->
        ContributeDialog(
            goal = goal,
            onDismiss = { contributingTo = null },
            onConfirm = { amount ->
                onEvent(SavingsGoalEvent.Contribute(goal.id, amount))
                contributingTo = null
            },
        )
    }
}

@Composable
private fun GoalRow(
    goal: SavingsGoalUi,
    onTopUp: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(UI.colors.medium)
            .clickable(onClick = onEdit)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = goal.name,
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${goal.percent}%",
                style = UI.typo.b2.style(
                    color = if (goal.reached) Green else UI.colors.pureInverse,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
        }

        LinearProgressIndicator(
            progress = { goal.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            // The theme's own accent, like the rest of the app - a bar in
            // flat black would be the one element ignoring the palette.
            color = if (goal.reached) Green else Ivy,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${goal.saved.format(goal.currency)} of " +
                    "${goal.target.format(goal.currency)} ${goal.currency}",
                style = UI.typo.c.style(color = Gray),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (goal.reached) {
                    "Reached"
                } else {
                    "${goal.remaining.format(goal.currency)} ${goal.currency} to go"
                },
                style = UI.typo.c.style(color = Gray),
            )
        }

        Row {
            TextButton(onClick = onTopUp) { Text("Put money aside") }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun GoalDialog(
    title: String,
    initialName: String,
    initialTarget: String,
    initialSaved: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, target: Double, saved: Double) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var target by remember { mutableStateOf(initialTarget) }
    var saved by remember { mutableStateOf(initialSaved) }

    val targetValue = target.toDoubleOrNull()
    val savedValue = saved.toDoubleOrNull() ?: 0.0
    val valid = name.isNotBlank() && targetValue != null && targetValue > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("What are you saving for?") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Target amount") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = saved,
                    onValueChange = { saved = it },
                    label = { Text("Already put aside") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(name, targetValue ?: 0.0, savedValue) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ContributeDialog(
    goal: SavingsGoalUi,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    val value = amount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Put money aside for ${goal.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${goal.saved.format(goal.currency)} of " +
                        "${goal.target.format(goal.currency)} ${goal.currency} so far.",
                    style = UI.typo.c.style(color = Gray),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount to add") },
                    singleLine = true,
                )
                Text(
                    text = "This records what you have set aside. It does not " +
                        "move money between accounts.",
                    style = UI.typo.c.style(color = Gray),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = value != null && value != 0.0,
                onClick = { onConfirm(value ?: 0.0) },
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

