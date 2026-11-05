package com.motionsalt.ccstudio.ui.tune

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motionsalt.ccstudio.core.EffectParam
import java.util.Locale
import kotlin.math.round

/**
 * Professional Material 3 Slider control for effect parameters.
 * - Accurate thumb positioning from min (0 / left) to max (right) without offset.
 * - Live uniform updates on every drag movement.
 * - Tap value badge to type exact numeric value.
 * - Step snapping and fine-nudge controls.
 * - Reset to default value on reset button or dialog.
 */
@Composable
fun ParameterScrubber(
    param: EffectParam,
    externalValue: Float,
    onValue: (Float) -> Unit
) {
    val step = param.step.coerceAtLeast(0.0001f)
    val minVal = param.min
    val maxVal = if (param.max > param.min) param.max else param.min + 1.0f

    var localValue by remember(param.key) { mutableFloatStateOf(externalValue.coerceIn(minVal, maxVal)) }
    val emit by rememberUpdatedState(onValue)

    LaunchedEffect(externalValue) {
        val clamped = externalValue.coerceIn(minVal, maxVal)
        if (clamped != localValue) {
            localValue = clamped
        }
    }

    var typing by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }

    val defaultVal = (param.defaultValue as? Number)?.toFloat() ?: minVal

    fun applySnapped(raw: Float) {
        val stepsCount = round((raw - minVal) / step)
        val snapped = (minVal + stepsCount * step).coerceIn(minVal, maxVal)
        localValue = snapped
        emit(snapped)
    }

    val formattedValue = remember(localValue, step) {
        when {
            step >= 1.0f -> String.format(Locale.US, "%.0f", localValue)
            step >= 0.01f -> String.format(Locale.US, "%.2f", localValue)
            step >= 0.001f -> String.format(Locale.US, "%.3f", localValue)
            else -> String.format(Locale.US, "%.4f", localValue)
        }
    }

    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = param.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface
            )

            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = colors.surfaceContainerHighest,
                modifier = Modifier.clickable {
                    textInput = formattedValue
                    typing = true
                }
            ) {
                Text(
                    text = formattedValue,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(
                onClick = { applySnapped(localValue - step) },
                enabled = localValue > minVal,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(28.dp)
                    .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
            ) {
                Text(
                    text = "−",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (localValue > minVal) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }

            Slider(
                value = localValue.coerceIn(minVal, maxVal),
                onValueChange = { newVal ->
                    localValue = newVal
                    applySnapped(newVal)
                },
                valueRange = minVal..maxVal,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                colors = SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.surfaceContainerHighest
                )
            )

            TextButton(
                onClick = { applySnapped(localValue + step) },
                enabled = localValue < maxVal,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(28.dp)
                    .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
            ) {
                Text(
                    text = "+",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (localValue < maxVal) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }

    if (typing) {
        AlertDialog(
            onDismissRequest = { typing = false },
            title = { Text(param.label) },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("Exact value") },
                    supportingText = { Text("Allowed range: $minVal to $maxVal (default: $defaultVal)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        textInput.toFloatOrNull()?.let { entered ->
                            applySnapped(entered)
                            typing = false
                        }
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            applySnapped(defaultVal)
                            typing = false
                        }
                    ) {
                        Text("Reset ($defaultVal)")
                    }
                    TextButton(onClick = { typing = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
