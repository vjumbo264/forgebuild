package com.motionsalt.ccstudio.ui.tune

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.engine.ui.icons.EngineIcons
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ColorPickerDialog(
    initialColorHex: String,
    paramLabel: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit,
    onActivateEyedropper: (() -> Unit)? = null
) {
    var hsv by remember {
        val parsed = parseHexToHsv(initialColorHex)
        mutableStateOf(parsed) // FloatArray(3): [0]=Hue(0..360), [1]=Sat(0..1), [2]=Val(0..1)
    }

    var hexText by remember {
        mutableStateOf(hsvToHex(hsv[0], hsv[1], hsv[2]))
    }

    val currentColor = remember(hsv) {
        val argb = android.graphics.Color.HSVToColor(hsv)
        Color(argb)
    }

    val quickSwatches = listOf(
        "#0A1428", "#D48B47", "#FFF2D9", "#FF8820",
        "#000000", "#FFFFFF", "#FF2255", "#00E5FF",
        "#4CAF50", "#FFEB3B", "#9C27B0", "#FF5722"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(paramLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (onActivateEyedropper != null) {
                    IconButton(onClick = {
                        onActivateEyedropper()
                        onDismiss()
                    }) {
                        Icon(EngineIcons.Colorize, contentDescription = "Sample Color Eyedropper")
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Color Preview & Hex Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { input ->
                            hexText = input
                            val clean = if (input.startsWith("#")) input else "#$input"
                            if (clean.length == 7) {
                                try {
                                    val parsedHsv = parseHexToHsv(clean)
                                    hsv = parsedHsv
                                } catch (e: Exception) {
                                    // ignore invalid hex
                                }
                            }
                        },
                        label = { Text("Hex Color") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // 2D Saturation & Value Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.White, Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))))
                            )
                        )
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black)
                            )
                        )
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val sat = (offset.x / size.width).coerceIn(0f, 1f)
                                val v = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                                hsv = floatArrayOf(hsv[0], sat, v)
                                hexText = hsvToHex(hsv[0], sat, v)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                val v = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                                hsv = floatArrayOf(hsv[0], sat, v)
                                hexText = hsvToHex(hsv[0], sat, v)
                            }
                        }
                )

                // Hue Slider (0..360)
                Column {
                    Text("Hue", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = hsv[0],
                        onValueChange = { newHue ->
                            hsv = floatArrayOf(newHue, hsv[1], hsv[2])
                            hexText = hsvToHex(newHue, hsv[1], hsv[2])
                        },
                        valueRange = 0f..360f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Quick Palette Swatches
                Text("Swatches", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(72.dp)
                ) {
                    items(quickSwatches) { hex ->
                        val swatchColor = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable {
                                    val parsedHsv = parseHexToHsv(hex)
                                    hsv = parsedHsv
                                    hexText = hex
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onColorSelected(hsvToHex(hsv[0], hsv[1], hsv[2]))
                onDismiss()
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun parseHexToHsv(hex: String): FloatArray {
    val clean = if (hex.startsWith("#")) hex else "#$hex"
    val colorInt = try {
        android.graphics.Color.parseColor(clean)
    } catch (e: Exception) {
        android.graphics.Color.WHITE
    }
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(colorInt, hsv)
    return hsv
}

private fun hsvToHex(h: Float, s: Float, v: Float): String {
    val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    val r = (colorInt shr 16) and 0xFF
    val g = (colorInt shr 8) and 0xFF
    val b = colorInt and 0xFF
    return String.format(Locale.US, "#%02X%02X%02X", r, g, b)
}
