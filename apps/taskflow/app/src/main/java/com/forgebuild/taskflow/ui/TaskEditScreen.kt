package com.forgebuild.taskflow.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.forgebuild.engine.ui.components.ExpressiveLoading
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.data.Recurrence
import com.forgebuild.taskflow.data.RecurrenceEngine
import com.forgebuild.taskflow.data.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Full edit screen: every task property editable, at any nesting depth. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(vm: TaskViewModel, taskId: Long, onClose: () -> Unit) {
    val task by produceState<Task?>(null, taskId) { value = vm.repo.get(taskId) }
    val context = LocalContext.current
    val current = task
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
        return
    }
    var title by remember(current.id) { mutableStateOf(current.title) }
    var info by remember(current.id) { mutableStateOf(current.info) }
    var fixedTime by remember(current.id) { mutableStateOf(current.fixedTime) }
    var recurrence by remember(current.id) { mutableStateOf(current.recurrence) }
    var weekdaysMask by remember(current.id) { mutableIntStateOf(current.weekdaysMask) }
    var recurrenceMenuOpen by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("EEE, MMM d yyyy · HH:mm", Locale.getDefault()) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Edit task", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(EngineIcons.ArrowBack, contentDescription = "Back") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md)) {

            OutlinedTextField(value = title, onValueChange = { title = it },
                label = { Text("Title") }, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium)

            OutlinedTextField(value = info, onValueChange = { info = it },
                label = { Text("Info — how this task should be done") },
                supportingText = { Text("Shown via the eye icon on the list row; never inline.") },
                modifier = Modifier.fillMaxWidth(), minLines = 3, shape = MaterialTheme.shapes.medium)

            // Fixed time
            Text("Fixed time", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val cal = Calendar.getInstance()
                    fixedTime?.let { cal.timeInMillis = it }
                    DatePickerDialog(context, { _, y, m, d ->
                        TimePickerDialog(context, { _, h, min ->
                            val c = Calendar.getInstance()
                            c.set(y, m, d, h, min, 0); c.set(Calendar.MILLISECOND, 0)
                            fixedTime = c.timeInMillis
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }) { Text(if (fixedTime == null) "Set time" else fmt.format(Date(fixedTime!!))) }
                if (fixedTime != null) {
                    OutlinedButton(onClick = { fixedTime = null }) { Text("Clear") }
                }
            }

            // Recurrence
            Text("Repeat", style = MaterialTheme.typography.titleSmall)
            ExposedDropdownMenuBox(expanded = recurrenceMenuOpen,
                onExpandedChange = { recurrenceMenuOpen = it }) {
                OutlinedTextField(
                    value = recurrence.name.lowercase().replaceFirstChar { it.uppercase() },
                    onValueChange = {}, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(recurrenceMenuOpen) },
                    modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                ExposedDropdownMenu(expanded = recurrenceMenuOpen,
                    onDismissRequest = { recurrenceMenuOpen = false }) {
                    Recurrence.entries.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(r.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = { recurrence = r; recurrenceMenuOpen = false })
                    }
                }
            }
            if (recurrence == Recurrence.WEEKLY) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    RecurrenceEngine.WEEKDAY_LABELS.forEachIndexed { i, label ->
                        val bit = 1 shl i
                        FilterChip(selected = (weekdaysMask and bit) != 0,
                            onClick = { weekdaysMask = weekdaysMask xor bit },
                            label = { Text(label) })
                    }
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.md))
            Button(onClick = {
                vm.saveEdit(current.copy(title = title.trim(), info = info.trim(),
                    fixedTime = fixedTime, recurrence = recurrence, weekdaysMask = weekdaysMask))
                onClose()
            }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Text("Save")
            }
        }
    }
}
