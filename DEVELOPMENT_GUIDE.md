# ElderGuard Development Guide

## Quick Reference

### ADB Commands
```bash
# Full path to adb.exe
ADB="C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"

# Device connection
$ADB devices
# Expected: 192.168.1.85:36293

# Wireless pairing (first time only)
$ADB pair 192.168.1.85:38669 999970
$ADB connect 192.168.1.85:36293

# Install & Launch
$ADB -s 192.168.1.85:36293 install -r "app/build/outputs/apk/debug/app-debug.apk"
$ADB -s 192.168.1.85:36293 shell monkey -p com.badblueprint.elderguard -c android.intent.category.LAUNCHER 1

# Debug
$ADB -s 192.168.1.85:36293 logcat | findstr elderguard
$ADB -s 192.168.1.85:36293 shell pm list packages | findstr elderguard
```

## Environment Setup

### Android Studio Configuration
- **Java Home**: Set in `gradle.properties`
  ```properties
  org.gradle.java.home=C:\\Program Files\\Android\\Android Studio\\jbr
  ```
- **SDK Location**: `C:\Users\user\AppData\Local\Android\Sdk`
- **Target**: Wear OS API 30+ (Galaxy Watch8)

### Project Structure
```
ElderGuard/
├── app/
│   ├── src/main/
│   │   ├── assets/          # GIF, audio files
│   │   ├── java/.../presentation/
│   │   │   └── MainActivity.kt
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle.properties        # CRITICAL: Java home config
├── settings.gradle.kts      # Maven repos
└── build.gradle.kts
```

## Common Issues & Solutions

### 1. TLS/SSL Handshake Errors
**Symptom**: `Remote host terminated the handshake`
**Solution**:
- Try toggling VPN connection
- Verify Maven Central / Google Maven access in browser
- Add fallback repository in `settings.gradle.kts`:
  ```kotlin
  maven { url = uri("https://jitpack.io") }
  ```

### 2. Java Version Mismatch
**Symptom**: `Dependency requires at least JVM runtime version 11`
**Solution**: Add to `gradle.properties`:
```properties
org.gradle.java.home=C:\\Program Files\\Android\\Android Studio\\jbr
```

### 3. ADB Connection Failures
**Symptom**: `cannot connect to 192.168.1.85:XXXXX`
**Solution**:
1. First time: Use pairing port (38669) with code
2. After pairing: Use connection port (36293)
3. Verify watch IP hasn't changed

### 4. App Not Launching
**Issue**: Wrong package name
**Solution**:
```bash
# Find actual package name
adb shell pm list packages | findstr elderguard
# Use exact package name: com.badblueprint.elderguard
```

## Wear OS Best Practices

### Keep Screen On
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    // ...
}
```

### Progress Animations
Use `animateFloatAsState` for smooth filling animations:
```kotlin
var progress by remember { mutableStateOf(0f) }
val animatedProgress by animateFloatAsState(
    targetValue = progress,
    animationSpec = tween(5000, easing = LinearEasing)
)
LaunchedEffect(Unit) { progress = 1f }
```

### GIF Support (Coil)
```kotlin
dependencies {
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")
}
```

```kotlin
AsyncImage(
    model = ImageRequest.Builder(context)
        .data("file:///android_asset/animation.gif")
        .decoderFactory(
            if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoderDecoder.Factory()
            } else {
                GifDecoder.Factory()
            }
        ).build(),
    contentDescription = "Animation"
)
```

### Audio Playback
```kotlin
LaunchedEffect(Unit) {
    repeat(3) {
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(context.assets.openFd("health.mp3"))
            prepare()
            start()
        }
        delay(mediaPlayer.duration.toLong())
        mediaPlayer.release()
        if (it < 2) delay(500)
    }
}
```

## Development Workflow

### 1. Make Changes
Edit code in Android Studio with hot reload support

### 2. Build
```bash
cd ElderGuard
./gradlew assembleDebug
# BUILD SUCCESSFUL in Xs
```

### 3. Install & Test
```bash
$ADB -s 192.168.1.85:36293 install -r "app/build/outputs/apk/debug/app-debug.apk"
$ADB -s 192.168.1.85:36293 shell monkey -p com.badblueprint.elderguard -c android.intent.category.LAUNCHER 1
```

### 4. Debug
```bash
# Real-time logs
$ADB -s 192.168.1.85:36293 logcat | findstr "elderguard"

# Filter by priority
$ADB -s 192.168.1.85:36293 logcat *:E | findstr "elderguard"  # Errors only
```

## VIBE_CODER_CODEX Alignment

### Observability
- Always check logs before guessing: `adb logcat`
- Verify build output: look for "BUILD SUCCESSFUL"
- Test on real hardware, not just emulator

### Honesty
- When Maven fails, try VPN toggle - admit network issues
- If unsure about package name, use `pm list packages`
- Progress over perfection: start with working demo, iterate

### Partnership
- Present options (e.g., animation styles A/B/C)
- Explain trade-offs (e.g., battery impact of keep-screen-on)
- Let user choose approach

## Hardware: Galaxy Watch8 40mm LTE
- **Screen**: Round, ~1.3" AMOLED
- **API Level**: 30+ (Android 11+)
- **Battery**: ~300mAh
- **Connection**: Wi-Fi 192.168.1.85, ports 36293 (connect), 38669 (pair)

## Next Steps
1. Implement actual health monitoring (Heart rate, SpO2)
2. Add voice interaction with local LLM
3. Server integration with JWT auth
4. Battery optimization testing
5. Real delirium detection logic

---

**Last Updated**: 2026-02-07
**Status**: Demo working ✅
**Device**: Galaxy Watch8 (192.168.1.85:36293)
