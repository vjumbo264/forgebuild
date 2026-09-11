package com.forgebuild.calcom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons

enum class CalcomMode {
    DUAL,
    COMPASS,
    CALCULATOR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalcomScreen() {
    val compassState by rememberCompassState()
    val calculatorEngine = remember { CalculatorEngine() }
    var mode by remember { mutableStateOf(CalcomMode.DUAL) }
    var lockedBearing by remember { mutableStateOf<Float?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = EngineIcons.Explore,
                            contentDescription = "Calcom",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Calcom",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // History button
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(
                            imageVector = EngineIcons.Search,
                            contentDescription = "Calculation History"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Mode Selector Segmented Tabs
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SegmentedButton(
                    selected = mode == CalcomMode.DUAL,
                    onClick = { mode = CalcomMode.DUAL },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = {}
                ) {
                    Text("Dual", style = MaterialTheme.typography.labelMedium)
                }
                SegmentedButton(
                    selected = mode == CalcomMode.COMPASS,
                    onClick = { mode = CalcomMode.COMPASS },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = {}
                ) {
                    Text("Compass", style = MaterialTheme.typography.labelMedium)
                }
                SegmentedButton(
                    selected = mode == CalcomMode.CALCULATOR,
                    onClick = { mode = CalcomMode.CALCULATOR },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = {}
                ) {
                    Text("Calculator", style = MaterialTheme.typography.labelMedium)
                }
            }

            when (mode) {
                CalcomMode.DUAL -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        CompassDial(
                            azimuth = compassState.azimuth,
                            cardinal = compassState.cardinal,
                            lockedBearing = lockedBearing,
                            onToggleLockBearing = {
                                lockedBearing = if (lockedBearing == null) compassState.azimuth else null
                            },
                            onSendToCalculator = { heading ->
                                calculatorEngine.insertHeading(heading)
                            },
                            compact = true,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        CalculatorView(
                            engine = calculatorEngine,
                            currentAzimuth = compassState.azimuth,
                            showTrig = true
                        )
                    }
                }

                CalcomMode.COMPASS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(Modifier.height(8.dp))

                        CompassDial(
                            azimuth = compassState.azimuth,
                            cardinal = compassState.cardinal,
                            lockedBearing = lockedBearing,
                            onToggleLockBearing = {
                                lockedBearing = if (lockedBearing == null) compassState.azimuth else null
                            },
                            onSendToCalculator = { heading ->
                                calculatorEngine.insertHeading(heading)
                                mode = CalcomMode.DUAL
                            },
                            compact = false
                        )

                        // Navigation Telemetry Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Navigational Telemetry",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Azimuth:", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${String.format(java.util.Locale.US, "%.1f", compassState.azimuth)}° (${compassState.cardinal})",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Reciprocal Bearing:", style = MaterialTheme.typography.bodyMedium)
                                    val recip = (compassState.azimuth + 180f) % 360f
                                    Text(
                                        "${String.format(java.util.Locale.US, "%.1f", recip)}°",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Pitch / Roll:", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${compassState.pitch.toInt()}° / ${compassState.roll.toInt()}°",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Sensor Status:", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        if (compassState.hasSensor) "Hardware Active" else "No Magnetometer",
                                        color = if (compassState.hasSensor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Quick bottom banner
                        Button(
                            onClick = {
                                calculatorEngine.insertHeading(compassState.azimuth)
                                mode = CalcomMode.DUAL
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(EngineIcons.Calculate, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Calculate with Current Heading (${compassState.azimuth.toInt()}°)")
                        }
                    }
                }

                CalcomMode.CALCULATOR -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp)
                    ) {
                        // Quick Heading Banner at top of calc
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        EngineIcons.Explore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Compass Heading: ${compassState.azimuth.toInt()}° (${compassState.cardinal})",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                TextButton(
                                    onClick = { calculatorEngine.insertHeading(compassState.azimuth) }
                                ) {
                                    Text("Insert", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        CalculatorView(
                            engine = calculatorEngine,
                            currentAzimuth = compassState.azimuth,
                            showTrig = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Calculation History Dialog
        if (showHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showHistoryDialog = false },
                title = { Text("Calculation History") },
                text = {
                    if (calculatorEngine.history.isEmpty()) {
                        Text(
                            text = "No calculations yet. Enter calculations or insert compass bearings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            calculatorEngine.history.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = item.expression,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "= ${item.result}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (item.note.isNotEmpty()) {
                                            Text(
                                                text = item.note,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHistoryDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
