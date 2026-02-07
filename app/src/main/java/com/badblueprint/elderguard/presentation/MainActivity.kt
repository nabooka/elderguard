package com.badblueprint.elderguard.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.badblueprint.elderguard.data.HealthSensorManager
import com.badblueprint.elderguard.data.SensorData
import com.badblueprint.elderguard.presentation.theme.ElderGuardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setTheme(android.R.style.Theme_DeviceDefault)

        // Request permissions
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION,
                "android.permission.health.READ_HEART_RATE"
            )
        )

        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    ElderGuardTheme {
        var sensorData by remember { mutableStateOf(SensorData()) }
        var isReadingHR by remember { mutableStateOf(false) }
        var isReadingSteps by remember { mutableStateOf(false) }
        var showHome by remember { mutableStateOf(true) }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val readSensors = {
            showHome = false
            if (!isReadingHR && !isReadingSteps) {
                isReadingHR = true
                isReadingSteps = true
                scope.launch {
                    try {
                        val manager = HealthSensorManager(context)
                        val data = manager.readSensors()
                        sensorData = data
                    } catch (e: Exception) {
                        android.util.Log.e("WearApp", "Error reading sensors", e)
                    } finally {
                        isReadingHR = false
                        isReadingSteps = false
                    }
                }
            }
        }

        val readHeartRateOnly = {
            if (!isReadingHR) {
                isReadingHR = true
                scope.launch {
                    try {
                        val manager = HealthSensorManager(context)
                        val hr = manager.readHeartRateOnly()
                        sensorData = sensorData.copy(heartRate = hr)
                    } catch (e: Exception) {
                        android.util.Log.e("WearApp", "Error reading HR", e)
                    } finally {
                        isReadingHR = false
                    }
                }
            }
        }

        val readStepsOnly = {
            if (!isReadingSteps) {
                isReadingSteps = true
                scope.launch {
                    try {
                        val manager = HealthSensorManager(context)
                        val steps = manager.readStepsOnly()
                        sensorData = sensorData.copy(steps = steps)
                    } catch (e: Exception) {
                        android.util.Log.e("WearApp", "Error reading steps", e)
                    } finally {
                        isReadingSteps = false
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (showHome) {
                SensorHomeScreen(onReadSensors = readSensors)
            } else {
                SensorResultsScreen(
                    data = sensorData,
                    onReadHeartRate = readHeartRateOnly,
                    onReadSteps = readStepsOnly,
                    isReadingHR = isReadingHR,
                    isReadingSteps = isReadingSteps
                )
            }
        }
    }
}

@Composable
fun SensorHomeScreen(onReadSensors: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "ELDERGUARD",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = onReadSensors,
            modifier = Modifier.size(120.dp)
        ) {
            Text(
                text = "СЧИТАТЬ\nДАТЧИКИ",
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ReadingSensorsScreen() {
    var progress by remember { mutableStateOf(0f) }
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(3000, easing = LinearEasing),
        label = "progress"
    )

    LaunchedEffect(Unit) {
        progress = 1f
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.wear.compose.material.CircularProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier.size(60.dp),
            indicatorColor = MaterialTheme.colors.primary,
            trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "ЧТЕНИЕ\nДАТЧИКОВ...",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun SensorResultsScreen(
    data: SensorData,
    onReadHeartRate: () -> Unit,
    onReadSteps: () -> Unit,
    isReadingHR: Boolean,
    isReadingSteps: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "РЕЗУЛЬТАТЫ",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        SensorRowWithLoading("❤️ Пульс", data.heartRate?.toInt()?.toString() ?: "N/A", "bpm", isReadingHR)
        SensorRowWithLoading("👣 Шаги", data.steps?.toString() ?: "N/A", "", isReadingSteps)
        SensorRow("🔥 Калории", data.calories?.toInt()?.toString() ?: "N/A", "kcal")
        SensorRow("📏 Дистанция", data.distance?.toInt()?.toString() ?: "N/A", "m")

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onReadHeartRate,
                enabled = !isReadingHR,
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
            ) {
                Text(
                    text = "❤️ ПУЛЬС",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onReadSteps,
                enabled = !isReadingSteps,
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
            ) {
                Text(
                    text = "👣 ШАГИ",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SensorRowWithLoading(label: String, value: String, unit: String, isLoading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp
        )

        if (isLoading) {
            androidx.wear.compose.material.CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                indicatorColor = MaterialTheme.colors.primary,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "$value $unit",
                color = MaterialTheme.colors.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SensorRow(label: String, value: String, unit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp
        )
        Text(
            text = "$value $unit",
            color = MaterialTheme.colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AnalysisScreen() {
    var progress by remember { mutableStateOf(0f) }
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(5000, easing = LinearEasing),
        label = "progress"
    )

    LaunchedEffect(Unit) {
        progress = 1f
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Circular progress bar that fills up
        androidx.wear.compose.material.CircularProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier.size(80.dp),
            indicatorColor = MaterialTheme.colors.primary,
            trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "АНАЛИЗИРУЮ\nВАШЕ СОСТОЯНИЕ...",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun ResultScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "ПОЗДРАВЛЯЮ!\nУ ВАС ДЕЛИРИЙ!!!",
            color = Color.Red,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("file:///android_asset/animation.gif")
                .decoderFactory(
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        ImageDecoderDecoder.Factory()
                    } else {
                        GifDecoder.Factory()
                    }
                )
                .build(),
            contentDescription = "Animation",
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentScale = ContentScale.Fit
        )
    }
}
