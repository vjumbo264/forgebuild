package com.forgebuild.calcom

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.engine.ui.icons.EngineIcons

@Composable
fun CalculatorView(
    engine: CalculatorEngine,
    currentAzimuth: Float,
    modifier: Modifier = Modifier,
    showTrig: Boolean = true
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Calculator Screen Display Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Expression Row (with horizontal scroll if long)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = engine.expression.ifEmpty { " " },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Result Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (engine.errorMessage != null) {
                        Text(
                            text = engine.errorMessage ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    Text(
                        text = engine.displayResult,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        // Compass & Navigation Integration Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Live Heading Insert Button
            FilledTonalButton(
                onClick = { engine.insertHeading(currentAzimuth) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "HDG ${currentAzimuth.toInt()}°",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Reciprocal Bearing (+180°)
            FilledTonalButton(
                onClick = { engine.addReciprocalBearing(currentAzimuth) },
                modifier = Modifier.weight(0.9f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "+180°",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Trig Buttons: sin, cos, tan
            if (showTrig) {
                OutlinedButton(
                    onClick = { engine.inputFunction("sin") },
                    modifier = Modifier.weight(0.7f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                ) {
                    Text("sin", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { engine.inputFunction("cos") },
                    modifier = Modifier.weight(0.7f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                ) {
                    Text("cos", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { engine.inputFunction("tan") },
                    modifier = Modifier.weight(0.7f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                ) {
                    Text("tan", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Keypad Grid
        val buttonSpacing = 6.dp

        // Row 1: AC, ( ), %, ÷
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = buttonSpacing),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            CalcKey(
                text = "AC",
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) { engine.clearAll() }

            CalcKey(
                text = "(",
                modifier = Modifier.weight(0.8f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) { engine.inputParenthesis("(") }

            CalcKey(
                text = ")",
                modifier = Modifier.weight(0.8f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) { engine.inputParenthesis(")") }

            CalcKey(
                text = "DEL",
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                icon = EngineIcons.Backspace
            ) { engine.deleteLast() }

            CalcKey(
                text = "÷",
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { engine.inputOperator("÷") }
        }

        // Row 2: 7, 8, 9, ×
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = buttonSpacing),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            CalcKey(text = "7", modifier = Modifier.weight(1f)) { engine.inputDigit("7") }
            CalcKey(text = "8", modifier = Modifier.weight(1f)) { engine.inputDigit("8") }
            CalcKey(text = "9", modifier = Modifier.weight(1f)) { engine.inputDigit("9") }
            CalcKey(
                text = "×",
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { engine.inputOperator("×") }
        }

        // Row 3: 4, 5, 6, −
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = buttonSpacing),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            CalcKey(text = "4", modifier = Modifier.weight(1f)) { engine.inputDigit("4") }
            CalcKey(text = "5", modifier = Modifier.weight(1f)) { engine.inputDigit("5") }
            CalcKey(text = "6", modifier = Modifier.weight(1f)) { engine.inputDigit("6") }
            CalcKey(
                text = "−",
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { engine.inputOperator("-") }
        }

        // Row 4: 1, 2, 3, +
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = buttonSpacing),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            CalcKey(text = "1", modifier = Modifier.weight(1f)) { engine.inputDigit("1") }
            CalcKey(text = "2", modifier = Modifier.weight(1f)) { engine.inputDigit("2") }
            CalcKey(text = "3", modifier = Modifier.weight(1f)) { engine.inputDigit("3") }
            CalcKey(
                text = "+",
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { engine.inputOperator("+") }
        }

        // Row 5: %, 0, ., =
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            CalcKey(
                text = "%",
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) { engine.inputOperator("%") }

            CalcKey(text = "0", modifier = Modifier.weight(1f)) { engine.inputDigit("0") }
            CalcKey(text = ".", modifier = Modifier.weight(1f)) { engine.inputDecimal() }

            CalcKey(
                text = "=",
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { engine.calculateEquals() }
        }
    }
}

@Composable
private fun CalcKey(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
