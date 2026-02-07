package com.badblueprint.elderguard.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
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
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

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
}
