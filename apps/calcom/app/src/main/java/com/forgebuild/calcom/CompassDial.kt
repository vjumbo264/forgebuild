package com.forgebuild.calcom

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.engine.ui.icons.EngineIcons
import kotlin.math.*

@Composable
fun CompassDial(
    azimuth: Float,
    cardinal: String,
    lockedBearing: Float?,
    onToggleLockBearing: () -> Unit,
    onSendToCalculator: (Float) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    // Smooth angle interpolation without full spins over the 0/360 boundary
    var accumulatedRotation by remember { mutableFloatStateOf(0f) }
    var lastRawAzimuth by remember { mutableFloatStateOf(azimuth) }

    LaunchedEffect(azimuth) {
        val delta = shortestAngleDelta(azimuth, lastRawAzimuth)
        accumulatedRotation += delta
        lastRawAzimuth = azimuth
    }

    val animatedRotation by animateFloatAsState(
        targetValue = -accumulatedRotation,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "compassRotation"
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    val dialSize = if (compact) 180.dp else 240.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Heading Readout Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Text(
                text = "${azimuth.roundToInt()}°",
                style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = primaryContainer,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = cardinal,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
            if (lockedBearing != null) {
                val diff = shortestAngleDelta(azimuth, lockedBearing)
                val diffStr = if (diff >= 0) "+${diff.roundToInt()}°" else "${diff.roundToInt()}°"
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "Δ $diffStr",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Compass Rose Canvas Container
        Box(
            modifier = Modifier
                .size(dialSize)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            // Rotating Compass Rose & Graduations
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(animatedRotation)
            ) {
                drawCompassRose(
                    center = center,
                    radius = size.minDimension / 2f - 4f,
                    onSurface = onSurface,
                    onSurfaceVariant = onSurfaceVariant,
                    primaryColor = primaryColor,
                    errorColor = errorColor,
                    outlineVariant = outlineVariant,
                    lockedBearing = lockedBearing
                )
            }

            // Fixed Top Index / Reticle Triangle (Points to current heading)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val tipY = 8f
                val baseLeft = Offset(center.x - 10f, tipY + 16f)
                val baseRight = Offset(center.x + 10f, tipY + 16f)
                val tip = Offset(center.x, tipY)

                val indexReticle = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(baseRight.x, baseRight.y)
                    lineTo(baseLeft.x, baseLeft.y)
                    close()
                }
                drawPath(indexReticle, color = primaryColor, style = Fill)
            }

            // Center Reticle
            Box(
                modifier = Modifier
                    .size(if (compact) 18.dp else 24.dp)
                    .clip(CircleShape)
                    .background(surfaceColor)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 8.dp else 10.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                        .align(Alignment.Center)
                )
            }
        }

        // Quick Compass Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lock Bearing Button
            FilterChip(
                selected = lockedBearing != null,
                onClick = onToggleLockBearing,
                label = {
                    Text(
                        if (lockedBearing != null) "Lock ${lockedBearing.roundToInt()}°" else "Lock Heading",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = EngineIcons.Lock,
                        contentDescription = "Lock Heading",
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.padding(end = 8.dp)
            )

            // Send to Calculator Button
            FilledTonalButton(
                onClick = { onSendToCalculator(azimuth) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = EngineIcons.Calculate,
                    contentDescription = "Use in Calc",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Use in Calc",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun DrawScope.drawCompassRose(
    center: Offset,
    radius: Float,
    onSurface: Color,
    onSurfaceVariant: Color,
    primaryColor: Color,
    errorColor: Color,
    outlineVariant: Color,
    lockedBearing: Float?
) {
    // Outer dial circle
    drawCircle(
        color = outlineVariant.copy(alpha = 0.6f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.5f)
    )

    // Inner dial circle
    drawCircle(
        color = outlineVariant.copy(alpha = 0.3f),
        radius = radius * 0.72f,
        center = center,
        style = Stroke(width = 1.5f)
    )

    // Graduations & Numbers
    for (deg in 0 until 360 step 5) {
        val rad = Math.toRadians(deg.toDouble()).toFloat()
        val cosVal = sin(rad)
        val sinVal = -cos(rad)

        val isMajor = deg % 90 == 0
        val isMedium = deg % 30 == 0
        val isMinor = deg % 15 == 0

        val tickLength = when {
            isMajor -> radius * 0.16f
            isMedium -> radius * 0.12f
            isMinor -> radius * 0.08f
            else -> radius * 0.04f
        }

        val strokeWidth = when {
            isMajor -> 3.5f
            isMedium -> 2.5f
            else -> 1.5f
        }

        val tickColor = when {
            deg == 0 -> errorColor // North tick
            isMajor -> primaryColor
            isMedium -> onSurface
            else -> onSurfaceVariant.copy(alpha = 0.5f)
        }

        val start = Offset(
            center.x + (radius - tickLength) * cosVal,
            center.y + (radius - tickLength) * sinVal
        )
        val end = Offset(
            center.x + radius * cosVal,
            center.y + radius * sinVal
        )

        drawLine(
            color = tickColor,
            start = start,
            end = end,
            strokeWidth = strokeWidth
        )

        // Draw Cardinal labels (N, E, S, W) on major ticks
        if (isMajor) {
            val label = when (deg) {
                0 -> "N"
                90 -> "E"
                180 -> "S"
                270 -> "W"
                else -> ""
            }
            val textPaint = android.graphics.Paint().apply {
                color = if (deg == 0) errorColor.hashCode() else onSurface.hashCode()
                textSize = radius * 0.18f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                isAntiAlias = true
            }
            val textRadius = radius * 0.56f
            val textX = center.x + textRadius * cosVal
            val textY = center.y + textRadius * sinVal + (textPaint.textSize / 3f)

            drawContext.canvas.nativeCanvas.drawText(label, textX, textY, textPaint)
        }
    }

    // Locked bearing indicator on the rotating card
    if (lockedBearing != null) {
        val rad = Math.toRadians(lockedBearing.toDouble()).toFloat()
        val cosVal = sin(rad)
        val sinVal = -cos(rad)
        val markerCenter = Offset(
            center.x + radius * 0.92f * cosVal,
            center.y + radius * 0.92f * sinVal
        )
        drawCircle(
            color = primaryColor,
            radius = radius * 0.06f,
            center = markerCenter,
            style = Fill
        )
    }

    // Four-point compass star / diamond needle
    val needleLen = radius * 0.40f
    val needleWidth = radius * 0.08f

    // North arrow (North is 0 rad -> straight up)
    val northTip = Offset(center.x, center.y - needleLen)
    val northLeft = Offset(center.x - needleWidth, center.y)
    val northRight = Offset(center.x + needleWidth, center.y)

    val nPath1 = Path().apply {
        moveTo(center.x, center.y)
        lineTo(northTip.x, northTip.y)
        lineTo(northRight.x, northRight.y)
        close()
    }
    val nPath2 = Path().apply {
        moveTo(center.x, center.y)
        lineTo(northTip.x, northTip.y)
        lineTo(northLeft.x, northLeft.y)
        close()
    }
    drawPath(nPath1, color = errorColor)
    drawPath(nPath2, color = errorColor.copy(alpha = 0.8f))

    // South arrow
    val southTip = Offset(center.x, center.y + needleLen)
    val sPath1 = Path().apply {
        moveTo(center.x, center.y)
        lineTo(southTip.x, southTip.y)
        lineTo(northRight.x, northRight.y)
        close()
    }
    val sPath2 = Path().apply {
        moveTo(center.x, center.y)
        lineTo(southTip.x, southTip.y)
        lineTo(northLeft.x, northLeft.y)
        close()
    }
    drawPath(sPath1, color = onSurfaceVariant.copy(alpha = 0.6f))
    drawPath(sPath2, color = onSurfaceVariant.copy(alpha = 0.4f))
}
