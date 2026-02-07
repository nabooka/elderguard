package com.badblueprint.elderguard.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.SampleDataPoint
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.guava.await
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId
import kotlin.coroutines.resume

data class SensorData(
    val heartRate: Double? = null,
    val steps: Long? = null,
    val calories: Double? = null,
    val distance: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class HealthSensorManager(private val context: Context) {

    private val healthServicesClient = HealthServices.getClient(context)
    private val measureClient = healthServicesClient.measureClient
    private val passiveMonitoringClient = healthServicesClient.passiveMonitoringClient
    private val exerciseClient = healthServicesClient.exerciseClient
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Health Connect may not be available on Wear OS
    private val healthConnectClient: HealthConnectClient? = try {
        HealthConnectClient.getOrCreate(context)
    } catch (e: Exception) {
        Log.w("HealthSensorManager", "Health Connect not available on this device: ${e.message}")
        null
    }

    // Callback для хранения последних данных от PassiveMonitoring
    var onPassiveDataUpdate: ((calories: Double?, distance: Double?) -> Unit)? = null

    suspend fun readSensors(): SensorData {
        Log.d("HealthSensorManager", "=== Starting sensor read ===")
        var heartRate: Double? = null
        var steps: Long? = null

        try {
            // Try SensorManager for heart rate (direct sensor access)
            val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            Log.d("HealthSensorManager", "HR sensor: ${hrSensor?.name ?: "NULL"}")
            if (hrSensor != null) {
                Log.d("HealthSensorManager", "Reading HR via SensorManager...")
                heartRate = readHeartRate(hrSensor)
                Log.d("HealthSensorManager", "HR result: $heartRate")
            }

            // Try to read step counter using SensorManager
            val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            Log.d("HealthSensorManager", "Step sensor: ${stepSensor?.name ?: "NULL"}")
            if (stepSensor != null) {
                Log.d("HealthSensorManager", "Reading steps...")
                steps = readSteps(stepSensor)
                Log.d("HealthSensorManager", "Steps result: $steps")
            }

        } catch (e: Exception) {
            Log.e("HealthSensorManager", "Error reading sensors", e)
        }

        Log.d("HealthSensorManager", "=== Sensor read complete: HR=$heartRate, Steps=$steps ===")
        return SensorData(
            heartRate = heartRate,
            steps = steps,
            calories = null,
            distance = null
        )
    }

    suspend fun readHeartRateOnly(): Double? {
        Log.d("HealthSensorManager", "=== Reading HR only ===")
        val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        return if (hrSensor != null) {
            readHeartRate(hrSensor)
        } else {
            Log.e("HealthSensorManager", "HR sensor not found")
            null
        }
    }

    suspend fun readStepsOnly(): Long? {
        Log.d("HealthSensorManager", "=== Reading steps only ===")
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        return if (stepSensor != null) {
            readSteps(stepSensor)
        } else {
            Log.e("HealthSensorManager", "Step sensor not found")
            null
        }
    }

    private suspend fun readHeartRate(sensor: Sensor): Double? = suspendCancellableCoroutine { continuation ->
        var resumed = false
        var updateCount = 0
        var lastValidHr: Double? = null
        var currentAccuracy = 0

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.isNotEmpty()) {
                    val hr = event.values[0].toDouble()
                    updateCount++
                    Log.d("HealthSensorManager", "HR update #$updateCount: $hr (accuracy: $currentAccuracy)")

                    // Accept first valid (>0) reading with good accuracy (>= 2)
                    if (hr > 0 && currentAccuracy >= 2 && !resumed) {
                        resumed = true
                        lastValidHr = hr
                        sensorManager.unregisterListener(this)
                        Log.d("HealthSensorManager", "HR accepted: $hr")
                        continuation.resume(hr)
                    } else if (hr > 0) {
                        lastValidHr = hr
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                currentAccuracy = accuracy
                Log.d("HealthSensorManager", "HR accuracy changed: $accuracy")
            }
        }

        sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_FASTEST
        )

        continuation.invokeOnCancellation {
            sensorManager.unregisterListener(listener)
        }

        // Timeout after 20 seconds
        kotlinx.coroutines.GlobalScope.launch {
            delay(20000)
            if (!resumed) {
                resumed = true
                sensorManager.unregisterListener(listener)
                Log.d("HealthSensorManager", "HR timeout after $updateCount updates, last valid: $lastValidHr")
                continuation.resume(lastValidHr)
            }
        }
    }

    private suspend fun readSteps(sensor: Sensor): Long? = suspendCancellableCoroutine { continuation ->
        var resumed = false
        var latestSteps: Long? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (!resumed && event != null && event.values.isNotEmpty()) {
                    latestSteps = event.values[0].toLong()
                    Log.d("HealthSensorManager", "Steps instant read: $latestSteps")
                    // Immediately unregister and return to avoid blocking updates
                    resumed = true
                    sensorManager.unregisterListener(this)
                    continuation.resume(latestSteps)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Use SENSOR_DELAY_NORMAL to get cached value quickly without blocking
        sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        continuation.invokeOnCancellation {
            sensorManager.unregisterListener(listener)
        }

        // Short timeout - just get cached value and release
        kotlinx.coroutines.GlobalScope.launch {
            delay(500)
            if (!resumed) {
                resumed = true
                sensorManager.unregisterListener(listener)
                Log.d("HealthSensorManager", "Steps timeout, returning: $latestSteps")
                continuation.resume(latestSteps)
            }
        }
    }

    // Check what capabilities are available for calories and distance
    suspend fun checkCaloriesDistanceCapabilities() {
        Log.d("HealthSensorManager", "=== Checking capabilities ===")

        try {
            // Check MeasureClient capabilities
            val measureCaps = measureClient.getCapabilitiesAsync().await()
            Log.d("HealthSensorManager", "MeasureClient supported: ${measureCaps.supportedDataTypesMeasure}")

            // Check PassiveMonitoringClient capabilities
            val passiveCaps = passiveMonitoringClient.getCapabilitiesAsync().await()
            Log.d("HealthSensorManager", "PassiveMonitoring supported: ${passiveCaps.supportedDataTypesPassiveMonitoring}")
            Log.d("HealthSensorManager", "PassiveGoals supported: ${passiveCaps.supportedDataTypesPassiveGoals}")

            // Check ExerciseClient capabilities
            val exerciseCaps = exerciseClient.getCapabilitiesAsync().await()
            Log.d("HealthSensorManager", "Exercise types: ${exerciseCaps.supportedExerciseTypes}")
            // Note: ExerciseCapabilities doesn't have supportedDataTypes field

        } catch (e: Exception) {
            Log.e("HealthSensorManager", "Error checking capabilities", e)
        }

        Log.d("HealthSensorManager", "=== Capabilities check complete ===")
    }

    // Read calories and distance using Health Connect API (if available) or PassiveMonitoring
    suspend fun refreshCaloriesAndDistance(): Pair<Double?, Double?> {
        Log.d("HealthSensorManager", "=== Refresh calories and distance ===")

        // Check if Health Connect is available
        if (healthConnectClient == null) {
            Log.w("HealthSensorManager", "Health Connect not available, trying PassiveMonitoring...")
            return tryReadFromPassiveMonitoring()
        }

        var totalCalories: Double? = null
        var totalDistance: Double? = null

        try {
            // Get start of today (midnight) and current time
            val now = Instant.now()
            val startOfDay = ZonedDateTime.now(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()

            Log.d("HealthSensorManager", "Time range: $startOfDay to $now")

            // Read calories burned today
            try {
                val caloriesRequest = ReadRecordsRequest(
                    recordType = ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )

                val caloriesResponse = healthConnectClient.readRecords(caloriesRequest)
                Log.d("HealthSensorManager", "Calories records count: ${caloriesResponse.records.size}")

                // Sum all calories for today
                totalCalories = caloriesResponse.records.sumOf {
                    it.energy.inKilocalories
                }

                Log.d("HealthSensorManager", "Total calories: $totalCalories kcal")
            } catch (e: Exception) {
                Log.e("HealthSensorManager", "Error reading calories", e)
            }

            // Read distance traveled today
            try {
                val distanceRequest = ReadRecordsRequest(
                    recordType = DistanceRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )

                val distanceResponse = healthConnectClient.readRecords(distanceRequest)
                Log.d("HealthSensorManager", "Distance records count: ${distanceResponse.records.size}")

                // Sum all distance for today
                totalDistance = distanceResponse.records.sumOf {
                    it.distance.inMeters
                }

                Log.d("HealthSensorManager", "Total distance: $totalDistance m")
            } catch (e: Exception) {
                Log.e("HealthSensorManager", "Error reading distance", e)
            }

        } catch (e: Exception) {
            Log.e("HealthSensorManager", "Error in refreshCaloriesAndDistance", e)
        }

        Log.d("HealthSensorManager", "=== Refresh complete: calories=$totalCalories, distance=$totalDistance ===")
        return Pair(totalCalories, totalDistance)
    }

    // Fallback: try to read from PassiveMonitoring (not implemented yet - returns null)
    private suspend fun tryReadFromPassiveMonitoring(): Pair<Double?, Double?> {
        Log.w("HealthSensorManager", "PassiveMonitoring fallback not implemented yet")
        Log.w("HealthSensorManager", "Calories and distance are only available through:")
        Log.w("HealthSensorManager", "  1. Health Connect API (not available on this device)")
        Log.w("HealthSensorManager", "  2. PassiveMonitoring with foreground service (requires implementation)")
        Log.w("HealthSensorManager", "  3. Samsung Health SDK (Samsung-specific)")
        return Pair(null, null)
    }

    // Explore all available sensors and capabilities
    fun exploreAllCapabilities() {
        Log.d("HealthSensorManager", "==========================================")
        Log.d("HealthSensorManager", "=== EXPLORING ALL DEVICE CAPABILITIES ===")
        Log.d("HealthSensorManager", "==========================================")

        // Get all physical sensors
        val allSensors = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL)
        Log.d("HealthSensorManager", "")
        Log.d("HealthSensorManager", "PHYSICAL SENSORS (${allSensors.size} total):")
        Log.d("HealthSensorManager", "")

        allSensors.forEach { sensor ->
            val typeName = getSensorTypeName(sensor.type)
            Log.d("HealthSensorManager", "• $typeName")
            Log.d("HealthSensorManager", "  Name: ${sensor.name}")
            Log.d("HealthSensorManager", "  Vendor: ${sensor.vendor}")
            Log.d("HealthSensorManager", "  Power: ${sensor.power} mA")
            Log.d("HealthSensorManager", "  Max Range: ${sensor.maximumRange}")
            Log.d("HealthSensorManager", "")
        }

        Log.d("HealthSensorManager", "==========================================")
    }

    private fun getSensorTypeName(type: Int): String = when (type) {
        android.hardware.Sensor.TYPE_ACCELEROMETER -> "ACCELEROMETER"
        android.hardware.Sensor.TYPE_GYROSCOPE -> "GYROSCOPE"
        android.hardware.Sensor.TYPE_LIGHT -> "LIGHT"
        android.hardware.Sensor.TYPE_PRESSURE -> "PRESSURE (Barometer)"
        android.hardware.Sensor.TYPE_PROXIMITY -> "PROXIMITY"
        android.hardware.Sensor.TYPE_GRAVITY -> "GRAVITY"
        android.hardware.Sensor.TYPE_LINEAR_ACCELERATION -> "LINEAR_ACCELERATION"
        android.hardware.Sensor.TYPE_ROTATION_VECTOR -> "ROTATION_VECTOR"
        android.hardware.Sensor.TYPE_RELATIVE_HUMIDITY -> "RELATIVE_HUMIDITY"
        android.hardware.Sensor.TYPE_AMBIENT_TEMPERATURE -> "AMBIENT_TEMPERATURE"
        android.hardware.Sensor.TYPE_MAGNETIC_FIELD -> "MAGNETIC_FIELD (Compass)"
        android.hardware.Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "MAGNETIC_FIELD_UNCALIBRATED"
        android.hardware.Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "GYROSCOPE_UNCALIBRATED"
        android.hardware.Sensor.TYPE_STEP_DETECTOR -> "STEP_DETECTOR"
        android.hardware.Sensor.TYPE_STEP_COUNTER -> "STEP_COUNTER"
        android.hardware.Sensor.TYPE_HEART_RATE -> "HEART_RATE"
        android.hardware.Sensor.TYPE_HEART_BEAT -> "HEART_BEAT"
        android.hardware.Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "OFF_BODY_DETECT"
        android.hardware.Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "ACCELEROMETER_UNCALIBRATED"
        65536 -> "PPG (Samsung Photoplethysmography)"
        65537 -> "SPO2 (Samsung Blood Oxygen)"
        65538 -> "SKIN_TEMPERATURE (Samsung)"
        65539 -> "BIOELECTRICAL_IMPEDANCE (Samsung Body Composition)"
        else -> "TYPE_$type"
    }
}
