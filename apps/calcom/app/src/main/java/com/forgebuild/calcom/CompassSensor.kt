package com.forgebuild.calcom

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

data class CompassState(
    val azimuth: Float = 0f,
    val cardinal: String = "N",
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
    val hasSensor: Boolean = true,
    val isSimulated: Boolean = false
)

fun degreesToCardinal(deg: Float): String {
    val normalized = (deg % 360f + 360f) % 360f
    return when {
        normalized >= 337.5f || normalized < 22.5f -> "N"
        normalized < 67.5f -> "NE"
        normalized < 112.5f -> "E"
        normalized < 157.5f -> "SE"
        normalized < 202.5f -> "S"
        normalized < 247.5f -> "SW"
        normalized < 292.5f -> "W"
        else -> "NW"
    }
}

/**
 * Calculates the shortest angular delta to prevent spinning wildly
 * across the 0/360 degree boundary when animating.
 */
fun shortestAngleDelta(target: Float, current: Float): Float {
    var diff = (target - current) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return diff
}

@Composable
fun rememberCompassState(): State<CompassState> {
    val context = LocalContext.current
    val compassState = remember { mutableStateOf(CompassState()) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            compassState.value = CompassState(hasSensor = false)
            return@DisposableEffect onDispose {}
        }

        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationVectorSensor == null && (accelerometer == null || magnetometer == null)) {
            // Hardware sensor unavailable (e.g. desktop emulator without simulated sensor)
            compassState.value = CompassState(hasSensor = false)
            return@DisposableEffect onDispose {}
        }

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                    if (azimuthDeg < 0) azimuthDeg += 360f
                    val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                    val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                    compassState.value = CompassState(
                        azimuth = azimuthDeg,
                        cardinal = degreesToCardinal(azimuthDeg),
                        pitch = pitchDeg,
                        roll = rollDeg,
                        accuracy = event.accuracy,
                        hasSensor = true,
                        isSimulated = false
                    )
                } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                    hasGravity = true
                    computeFromAccMag()
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                    hasGeomagnetic = true
                    computeFromAccMag()
                }
            }

            private fun computeFromAccMag() {
                if (hasGravity && hasGeomagnetic) {
                    val r = FloatArray(9)
                    val i = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                        SensorManager.getOrientation(r, orientationAngles)
                        var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        if (azimuthDeg < 0) azimuthDeg += 360f
                        val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                        val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                        compassState.value = CompassState(
                            azimuth = azimuthDeg,
                            cardinal = degreesToCardinal(azimuthDeg),
                            pitch = pitchDeg,
                            roll = rollDeg,
                            accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                            hasSensor = true,
                            isSimulated = false
                        )
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                compassState.value = compassState.value.copy(accuracy = accuracy)
            }
        }

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return compassState
}
