package com.mikotrack.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.sqrt

/** The sensors shown by the diagnostics screen. */
enum class SensorKind(val sensorType: Int) {
    ACCELEROMETER(Sensor.TYPE_ACCELEROMETER),
    GRAVITY(Sensor.TYPE_GRAVITY),
    LINEAR_ACCELERATION(Sensor.TYPE_LINEAR_ACCELERATION),
    GYROSCOPE(Sensor.TYPE_GYROSCOPE),
    MAGNETOMETER(Sensor.TYPE_MAGNETIC_FIELD),
    ROTATION_VECTOR(Sensor.TYPE_ROTATION_VECTOR),
    PRESSURE(Sensor.TYPE_PRESSURE),
    LIGHT(Sensor.TYPE_LIGHT),
    AMBIENT_TEMPERATURE(Sensor.TYPE_AMBIENT_TEMPERATURE),
    RELATIVE_HUMIDITY(Sensor.TYPE_RELATIVE_HUMIDITY),
}

/** The fixed characteristics of a hardware sensor. */
data class SensorStaticInfo(
    val name: String,
    val vendor: String,
    val maxRange: Float,
    val resolution: Float,
    val powerMilliAmps: Float,
    val minDelayMicros: Int,
)

/** The last values of a sensor and its accuracy status (`SensorManager.SENSOR_STATUS_*`). */
data class SensorReading(val values: List<Float>, val accuracy: Int) {
    val x: Float get() = values.getOrElse(0) { 0f }
    val y: Float get() = values.getOrElse(1) { 0f }
    val z: Float get() = values.getOrElse(2) { 0f }
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)
}

/** The latest reading of every sensor, plus values derived from them. */
data class SensorSnapshot(
    val readings: Map<SensorKind, SensorReading> = emptyMap(),
    /** The rotation of the display (`Surface.ROTATION_*`), used to orient the compass. */
    val displayRotation: Int = 0,
) {
    operator fun get(kind: SensorKind): SensorReading? = readings[kind]

    /**
     * Heading of the top of the screen, in degrees clockwise from magnetic north, from the
     * rotation vector or, when the device has none, from the accelerometer and magnetometer.
     */
    val headingDegrees: Float?
        get() {
            val matrix = FloatArray(9)
            val rotation = readings[SensorKind.ROTATION_VECTOR]
            val ok = if (rotation != null && rotation.values.size >= 3) {
                // Some devices report five values, which older platform versions reject.
                SensorManager.getRotationMatrixFromVector(matrix, rotation.values.take(4).toFloatArray())
                true
            } else {
                val gravity = readings[SensorKind.GRAVITY] ?: readings[SensorKind.ACCELEROMETER]
                val magnetic = readings[SensorKind.MAGNETOMETER]
                gravity != null && magnetic != null &&
                    SensorManager.getRotationMatrix(
                        matrix,
                        null,
                        gravity.values.take(3).toFloatArray(),
                        magnetic.values.take(3).toFloatArray(),
                    )
            }
            if (!ok) return null
            val orientation = SensorManager.getOrientation(matrix, FloatArray(3))
            val azimuth = Math.toDegrees(orientation[0].toDouble()) + displayRotation * 90.0
            return (((azimuth % 360.0) + 360.0) % 360.0).toFloat()
        }

    /** Gravity in screen coordinates (x to the right of the screen, y to its top). */
    private val tiltSource: SensorReading?
        get() {
            val g = readings[SensorKind.GRAVITY] ?: readings[SensorKind.ACCELEROMETER] ?: return null
            val (x, y) = when (displayRotation) {
                1 -> -g.y to g.x
                2 -> -g.x to -g.y
                3 -> g.y to -g.x
                else -> g.x to g.y
            }
            return SensorReading(listOf(x, y, g.z), g.accuracy)
        }

    /** Forward tilt in degrees: 0 when lying flat, +90 when standing upright in portrait. */
    val pitchDegrees: Float? get() = tiltSource?.let { Inclination.pitch(it.x, it.y, it.z) }

    /** Sideways tilt in degrees: 0 when lying flat, positive when the right edge is lower. */
    val rollDegrees: Float? get() = tiltSource?.let { Inclination.roll(it.x, it.y, it.z) }

    /** Unit vector (x to the right, y up on the screen) where the bubble of a level would sit. */
    val bubbleOffset: Pair<Float, Float>?
        get() = tiltSource?.takeIf { it.magnitude > 0f }?.let { g ->
            // The bubble floats towards the higher side of the phone.
            (g.x / g.magnitude) to (g.y / g.magnitude)
        }

    val magneticFieldMicroTesla: Float? get() = readings[SensorKind.MAGNETOMETER]?.magnitude

    val magnetometerAccuracy: Int? get() = readings[SensorKind.MAGNETOMETER]?.accuracy

    /** True when the field is unusual for the Earth or the system reports a low accuracy. */
    val compassNeedsCalibration: Boolean
        get() {
            val field = magneticFieldMicroTesla ?: return false
            val accuracy = magnetometerAccuracy ?: SensorManager.SENSOR_STATUS_ACCURACY_HIGH
            return field !in EARTH_FIELD_RANGE || accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
        }

    val magneticFieldOutOfRange: Boolean
        get() = magneticFieldMicroTesla?.let { it !in EARTH_FIELD_RANGE } ?: false

    /** Altitude from the barometer with the standard atmosphere, in metres. */
    val barometricAltitudeMeters: Float?
        get() = readings[SensorKind.PRESSURE]?.x?.takeIf { it > 0f }?.let {
            SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it)
        }

    companion object {
        /** The magnitude of the Earth's magnetic field ranges roughly between these values. */
        val EARTH_FIELD_RANGE = 25f..65f
    }
}

/** Tilt angles from a gravity vector expressed in device coordinates. */
object Inclination {
    fun pitch(x: Float, y: Float, z: Float): Float = angle(y, x, y, z)

    fun roll(x: Float, y: Float, z: Float): Float = angle(-x, x, y, z)

    private fun angle(component: Float, x: Float, y: Float, z: Float): Float {
        val g = sqrt(x * x + y * y + z * z)
        if (g == 0f) return 0f
        return Math.toDegrees(asin((component / g).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }
}

/** Live readings of the motion, position and environment sensors. */
class SensorMonitor(context: Context) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager?

    private fun sensor(kind: SensorKind): Sensor? = sensorManager?.getDefaultSensor(kind.sensorType)

    /** The characteristics of every sensor, or null for the ones the device lacks. */
    fun sensorInfo(): Map<SensorKind, SensorStaticInfo?> = SensorKind.entries.associateWith { kind ->
        sensor(kind)?.let {
            SensorStaticInfo(
                name = it.name.orEmpty(),
                vendor = it.vendor.orEmpty(),
                maxRange = it.maximumRange,
                resolution = it.resolution,
                powerMilliAmps = it.power,
                minDelayMicros = it.minDelay,
            )
        }
    }

    /**
     * Emits the latest values of all the sensors at most once every [periodMillis]. The
     * listeners are registered while the flow is collected and removed when it is cancelled.
     */
    fun readings(
        displayRotation: () -> Int = { 0 },
        periodMillis: Long = 200L,
    ): Flow<SensorSnapshot> = callbackFlow {
        val manager = sensorManager
        if (manager == null) {
            trySend(SensorSnapshot())
            close()
            return@callbackFlow
        }
        val lock = Any()
        val latest = HashMap<SensorKind, SensorReading>()
        var dirty = true
        val kindsByType = SensorKind.entries.associateBy { it.sensorType }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val kind = kindsByType[event.sensor.type] ?: return
                synchronized(lock) {
                    latest[kind] = SensorReading(event.values.toList(), event.accuracy)
                    dirty = true
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                val kind = kindsByType[sensor.type] ?: return
                synchronized(lock) {
                    latest[kind]?.let { latest[kind] = it.copy(accuracy = accuracy) }
                    dirty = true
                }
            }
        }

        SensorKind.entries.mapNotNull { sensor(it) }.forEach {
            manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }

        // The sensors report far more often than a screen can show, so the values are
        // published at a fixed, slower rate.
        val ticker = launch {
            while (isActive) {
                val snapshot = synchronized(lock) {
                    if (dirty) {
                        dirty = false
                        SensorSnapshot(HashMap(latest), runCatching(displayRotation).getOrDefault(0))
                    } else {
                        null
                    }
                }
                snapshot?.let { send(it) }
                delay(periodMillis)
            }
        }

        awaitClose {
            ticker.cancel()
            manager.unregisterListener(listener)
        }
    }
}
