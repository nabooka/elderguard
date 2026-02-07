# ElderGuard Implementation Plan

## Overview
Phased implementation plan for ElderGuard health monitoring features on Samsung Galaxy Watch8.
Each phase builds on the previous one, prioritizing quick wins and battery efficiency.

---

## ✅ Completed (Phase 0)

**Status:** 100% complete
**Timeframe:** Completed

### Features
- ❤️ **Heart Rate Monitoring** - Real-time pulse reading (TYPE_HEART_RATE with accuracy validation)
- 👣 **Step Counter** - Instant step count reading (TYPE_STEP_COUNTER with instant read pattern)
- 🖥️ **Sensor Exploration** - Documented all 41 physical sensors available on device
- 📱 **Basic UI** - Results screen with inline refresh buttons for each metric

### Technical Achievements
- Solved Samsung Watch blocking issue (instant read pattern)
- Implemented accuracy validation for heart rate (~1-2 second read time)
- Created comprehensive sensor documentation (SENSORS_CAPABILITIES.md)
- Working demo app with smooth animations

---

## 📋 Phase 1: Basic Sensors & Quick Wins

**Priority:** HIGH
**Complexity:** LOW (1-2 weeks)
**Battery Impact:** MINIMAL (~2-5% per day)

### Features

#### 1.1 Temperature Monitoring
- **Sensor:** TYPE_69686 (Samsung Skin Temperature)
- **Accuracy:** ~±0.3°C for skin temp, ±1-2°C for ambient estimation
- **Use Case:** Fever detection, thermal comfort monitoring
- **Implementation:**
  ```kotlin
  - Read from TYPE_AMBIENT_TEMPERATURE (if available)
  - Read from TYPE_69686 (Samsung skin temp)
  - Estimate core temperature (+1-2°C from skin)
  - Log trends for fever detection
  ```
- **UI:** Add "🌡️ Температура" row with refresh button

#### 1.2 Off-Body Detection
- **Sensor:** TYPE_LOW_LATENCY_OFFBODY_DETECT
- **Accuracy:** ~95% (hardware-level detection)
- **Use Case:** Detect if user removed watch, pause monitoring to save battery
- **Implementation:**
  ```kotlin
  - Register listener for TYPE_LOW_LATENCY_OFFBODY_DETECT
  - Pause heart rate monitoring when off-body
  - Resume when watch is worn again
  - Log wearing patterns
  ```
- **UI:** Status indicator "⌚ На руке" / "⌚ Снято"

#### 1.3 Floors Climbed
- **Sensor:** TYPE_PRESSURE (barometer-based)
- **Accuracy:** ~85% (3m altitude change ≈ 1 floor)
- **Use Case:** Activity intensity tracking, mobility monitoring
- **Implementation:**
  ```kotlin
  - Monitor TYPE_PRESSURE changes
  - Calculate altitude delta (1 hPa ≈ 8.5m altitude)
  - Count floors when delta >= 3 meters upward
  - Filter noise with time-based thresholds
  ```
- **UI:** Add "🪜 Этажи" row

#### 1.4 Ambient Light Monitoring
- **Sensor:** TYPE_LIGHT
- **Accuracy:** Good for day/night detection
- **Use Case:** Circadian rhythm tracking, sleep/wake detection
- **Implementation:**
  ```kotlin
  - Sample TYPE_LIGHT periodically
  - Detect day (>100 lux) vs night (<10 lux)
  - Log light exposure patterns
  - Use for sleep detection context
  ```

### Deliverables
- [ ] Temperature reading (skin + ambient)
- [ ] Off-body detection with auto-pause
- [ ] Floors climbed counter
- [ ] Ambient light monitoring
- [ ] Updated UI with new metrics
- [ ] Battery optimization (pause when off-body)

### Testing Criteria
- Temperature reads within ±0.5°C of reference thermometer
- Off-body detection triggers within 2 seconds
- Floors counted matches actual climbed (±1 floor tolerance)
- Battery drain < 5% per day with hourly readings

---

## 🎯 Phase 2: Advanced Monitoring

**Priority:** MEDIUM
**Complexity:** MEDIUM (2-3 weeks)
**Battery Impact:** MODERATE (~5-10% per day)

### Features

#### 2.1 Fall Detection
- **Sensors:** ACCELEROMETER + GYROSCOPE + ROTATION_VECTOR
- **Accuracy:** ~75-85% (good for hard falls, may miss soft falls)
- **Use Case:** Emergency alert for elderly users
- **Implementation:**
  ```kotlin
  - Monitor ACCELEROMETER for sudden spikes (>2.5g)
  - Check GYROSCOPE for rotation (falling motion)
  - Verify with ROTATION_VECTOR (orientation change)
  - Confirm with impact (sudden deceleration)
  - Alert if: high_accel + rotation + impact + stillness
  ```
- **Algorithm:**
  1. Detect freefall: total acceleration < 0.6g for >150ms
  2. Detect impact: acceleration spike >2.5g
  3. Verify rotation: gyroscope change >200°/s
  4. Check post-fall: stillness for >10 seconds
- **UI:** Background monitoring with alert popup
- **Battery:** Use sensor batching (collect every 100ms in bursts)

#### 2.2 Stress Level Estimation
- **Sensors:** HEART_RATE + ACCELEROMETER (activity context)
- **Accuracy:** ~70% (HRV would be better but needs ECG)
- **Use Case:** Anxiety detection, agitation monitoring for dementia patients
- **Implementation:**
  ```kotlin
  - Read resting heart rate baseline over 7 days
  - Monitor current HR vs baseline
  - Factor in activity level (ACCELEROMETER)
  - Stress score: (currentHR - baselineHR) / activity_factor
  - High stress if: HR elevated >15 bpm without activity
  ```
- **Limitations:** Without HRV (needs ECG), this is rough estimation
- **UI:** "😰 Стресс" indicator (Low/Medium/High)

#### 2.3 Gait Analysis (Походка)
- **Sensors:** ACCELEROMETER + GYROSCOPE + STEP_DETECTOR
- **Accuracy:** Cadence ~90%, Shakiness ~70%, Asymmetry ~50-60%
- **Use Case:** Mobility decline detection, Parkinson's monitoring
- **Implementation:**
  ```kotlin
  // Cadence (steps per minute)
  - Use STEP_DETECTOR for precise step timing
  - Calculate steps/minute over 1-minute window
  - Normal: 100-120 spm, Slow: <90 spm

  // Shakiness
  - Monitor ACCELEROMETER variance while walking
  - High variance = shaky gait (Parkinson's, weakness)

  // Regularity
  - Measure time between steps
  - High variance = irregular gait (ataxia, weakness)
  ```
- **Limitations:**
  - Asymmetry detection unreliable (need both legs, we only have one wrist)
  - Best during active walking, not standing/sitting
- **UI:** "🚶 Походка" with cadence + shakiness score
- **Battery:** Only analyze during walking (use STEP_DETECTOR to trigger)

#### 2.4 Activity Classification
- **Sensors:** ACCELEROMETER + GYROSCOPE + STEP_DETECTOR
- **Accuracy:** ~80-90% for basic activities
- **Use Case:** Daily activity tracking, sedentary time monitoring
- **Implementation:**
  ```kotlin
  - Walking: STEP_DETECTOR active + moderate acceleration
  - Running: STEP_DETECTOR + high cadence (>140 spm)
  - Cycling: regular motion without steps
  - Sedentary: low acceleration for >30 minutes
  - Sleep: stillness + off-wrist angle + dark (LIGHT sensor)
  ```
- **UI:** "🏃 Активность" with current activity label

### Deliverables
- [ ] Fall detection algorithm with emergency alert
- [ ] Stress level estimation (HR-based)
- [ ] Gait analysis (cadence + shakiness)
- [ ] Activity classification (walk/run/cycle/sedentary)
- [ ] Background monitoring service
- [ ] Alert system for falls
- [ ] Data logging for trend analysis

### Testing Criteria
- Fall detection: >75% accuracy on staged falls
- Stress detection: correlates with self-reported stress
- Gait cadence: ±5 spm vs manual count
- Activity classification: >80% accuracy over 1-week test
- Battery drain < 10% per day

---

## 🔬 Phase 3: Advanced Features

**Priority:** LOW
**Complexity:** HIGH (1 month+)
**Battery Impact:** HIGH (~15-20% per day)

### Features

#### 3.1 ECG & Heart Rate Variability
- **Sensor:** TYPE_69669 (Samsung ECG)
- **Accuracy:** Medical-grade for supported features
- **Use Case:** Atrial fibrillation detection, HRV for stress/recovery
- **Challenges:**
  - ⚠️ **Samsung Health SDK Required** (not public API)
  - ⚠️ **Regulatory compliance** (medical device certification)
  - ⚠️ **User must touch bezel** for 30 seconds
- **Implementation:**
  ```kotlin
  // Option A: Samsung Health SDK (proprietary)
  - Requires partnership with Samsung
  - Access to HealthDataStore
  - May need medical device certification

  // Option B: RAW sensor data (risky)
  - Read TYPE_69669 raw values
  - Process ECG signal manually
  - Risk: unvalidated medical data
  ```
- **Recommendation:** Research Samsung Health SDK access first
- **UI:** "💓 ECG" with AFib detection status

#### 3.2 Blood Oxygen (SpO2)
- **Sensor:** TYPE_65537 (Samsung SpO2) or PPG-based
- **Accuracy:** ±2% (medical devices are ±2-3%)
- **Use Case:** Respiratory issues, sleep apnea screening
- **Challenges:**
  - Samsung Health SDK likely required
  - Needs proper finger placement (not continuous)
  - Battery intensive (LED + photodiode)
- **Implementation:**
  ```kotlin
  - Access TYPE_65537 if available
  - Or estimate from PPG (TYPE_65536) signal
  - Requires calibration against pulse oximeter
  - On-demand reading (not continuous)
  ```
- **UI:** "💉 SpO2" with manual trigger button

#### 3.3 Sleep Monitoring
- **Sensors:** ACCELEROMETER + HEART_RATE + LIGHT + OFF_BODY
- **Accuracy:** ~85% for sleep stages with good algorithm
- **Use Case:** Sleep quality tracking, insomnia detection
- **Implementation:**
  ```kotlin
  // Sleep detection
  - Low activity (ACCELEROMETER) for >30 min
  - Dark environment (LIGHT < 10 lux)
  - Watch worn (OFF_BODY_DETECT)
  - Evening/night time

  // Sleep stage estimation (rough)
  - Deep sleep: very low movement + low HR
  - Light sleep: some movement + normal HR
  - REM: low movement + elevated HR + irregular
  - Awake: movement + higher HR
  ```
- **Limitations:** Without EEG, sleep staging is estimation
- **Battery:** Optimize with sensor batching (every 1-2 minutes)
- **UI:** "😴 Сон" with total sleep time + stages

#### 3.4 Tremor Detection
- **Sensors:** ACCELEROMETER + GYROSCOPE (high-frequency sampling)
- **Accuracy:** ~75% for frequency detection
- **Use Case:** Parkinson's monitoring, essential tremor tracking
- **Implementation:**
  ```kotlin
  - Sample ACCELEROMETER at 100Hz (high frequency)
  - Apply FFT (Fast Fourier Transform)
  - Detect tremor frequency (4-12 Hz typical)
  - Parkinson's: 4-6 Hz resting tremor
  - Essential tremor: 8-12 Hz action tremor
  - Measure tremor amplitude and regularity
  ```
- **Battery:** High drain (100Hz sampling), use triggered mode
- **UI:** "🤚 Тремор" with frequency + amplitude

#### 3.5 Calories & Distance (Passive Monitoring)
- **Approach:** Health Services PassiveMonitoring API
- **Accuracy:** ~70-80% for calories, ~85% for distance
- **Use Case:** Daily activity summary, fitness tracking
- **Challenges:**
  - Requires foreground service (battery drain)
  - Health Connect not available on Wear OS
  - May conflict with Samsung Health
- **Implementation:**
  ```kotlin
  - Create foreground service
  - Register PassiveMonitoringClient callbacks
  - Collect DAILY_STEPS, CALORIES, DISTANCE
  - Sync data periodically
  - Store in local database
  ```
- **Battery:** ~5-8% per day for passive monitoring
- **UI:** Update existing calories/distance rows with real data

### Deliverables
- [ ] ECG reading (if SDK accessible)
- [ ] SpO2 monitoring (Samsung Health SDK or PPG estimation)
- [ ] Sleep tracking with stage estimation
- [ ] Tremor detection with frequency analysis
- [ ] Passive monitoring service for calories/distance
- [ ] Foreground service implementation
- [ ] Data export/sync functionality

### Testing Criteria
- ECG: Validated against medical ECG if possible
- SpO2: ±2% vs pulse oximeter
- Sleep: >80% agreement with sleep diary
- Tremor: Frequency detection ±0.5 Hz
- Passive monitoring: <10% battery drain per day

---

## 🚀 Phase 4: Integration & AI Features

**Priority:** LOW
**Complexity:** VERY HIGH (2-3 months)
**Battery Impact:** VARIABLE

### Features

#### 4.1 LLM Integration
- **Connection:** WebSocket to local server (Llama 3.1 8B)
- **Use Case:** Conversational health assistant, cognitive games
- **Implementation:**
  - Self-written WebSocket protocol
  - JWT authentication
  - Periodic conversations (hourly)
  - Cognitive assessment through chat
- **Battery:** Minimize network usage, batch data uploads

#### 4.2 Anomaly Detection
- **Data:** All sensor readings over time
- **Approach:** Local pattern recognition + server-side AI
- **Use Case:** Early warning for health decline
- **Examples:**
  - Heart rate trending higher over weeks
  - Decreased mobility (fewer steps, slower gait)
  - Sleep pattern disruption
  - Increased sedentary time
- **Implementation:**
  - Local baseline calculation
  - Anomaly scoring
  - Server sends alerts if multiple anomalies detected

#### 4.3 Medication Reminders
- **Integration:** Watch alerts + LLM confirmation
- **Use Case:** Ensure medication compliance
- **Features:**
  - Scheduled vibration alerts
  - Voice confirmation via LLM
  - Missed dose tracking

#### 4.4 Emergency Contact System
- **Triggers:** Fall detection, unusual vital signs, no response to check-in
- **Action:** Alert caregiver via server
- **Implementation:**
  - Watch detects emergency
  - Sends data to local server
  - Server notifies caregiver (SMS, call, app notification)

### Deliverables
- [ ] WebSocket client on watch
- [ ] JWT authentication
- [ ] Periodic health data sync
- [ ] LLM conversation interface
- [ ] Anomaly detection baseline
- [ ] Medication reminder system
- [ ] Emergency alert pipeline

---

## 📊 Summary & Priorities

### Recommended Order
1. **Phase 1** (1-2 weeks) - Quick wins, minimal battery impact
   - Temperature, off-body, floors, light
2. **Phase 2** (2-3 weeks) - Core health monitoring
   - Fall detection, stress, gait analysis, activity classification
3. **Phase 4** (parallel) - Server integration
   - Can develop server-side while working on Phase 2
4. **Phase 3** (1+ month) - Advanced features
   - ECG, SpO2, sleep, tremor (research Samsung SDK first)

### Battery Budget
- **Phase 1:** ~2-5% per day
- **Phase 2:** ~5-10% per day
- **Phase 3:** ~15-20% per day (if all features enabled)
- **Target:** Keep total < 15-20% for 24/7 operation on 300mAh battery

### Technical Risks
1. **Samsung Health SDK Access** - May require partnership for ECG/SpO2
2. **Battery Life** - 300mAh is limited, need aggressive optimization
3. **Sensor Accuracy** - Some features (gait asymmetry) not reliable with single wrist sensor
4. **Regulatory** - ECG features may need medical certification
5. **Network Reliability** - Wi-Fi connection stability for server communication

### Mitigation Strategies
- Use sensor batching to reduce wake-ups
- Implement off-body detection to pause monitoring
- Prioritize features by battery/accuracy tradeoff
- Research Samsung Health SDK access early
- Develop fallback algorithms for unavailable sensors

---

## 📝 Notes

### Samsung-Specific Sensors (Proprietary)
These sensors exist on Galaxy Watch8 but may require Samsung Health SDK:
- **TYPE_69669** - ECG (electrocardiogram)
- **TYPE_69680** - BIA (bioelectrical impedance / body composition)
- **TYPE_69686** - Skin temperature
- **TYPE_69702** - EDA (electrodermal activity / galvanic skin response)
- **TYPE_65536** - PPG (photoplethysmography)
- **TYPE_65537** - SpO2 (blood oxygen)

**Action:** Research Samsung Health SDK documentation and partnership requirements

### Health Services API Capabilities
Confirmed available on Galaxy Watch8:
- MeasureClient: Heart rate (working)
- PassiveMonitoringClient: Steps, calories, distance (needs foreground service)
- ExerciseClient: Workout tracking (high battery usage)

### Unreliable/Impossible Features
- **Gait Asymmetry** (~50% accuracy) - Needs sensors on both legs, not just wrist
- **Blood Pressure** - Not possible without cuff or validated PPG algorithm
- **Blood Glucose** - Not possible with current sensors (no NIR spectroscopy)
- **Hydration** - Unreliable without bioimpedance validation

---

**Document Version:** 1.0
**Last Updated:** 2026-02-07
**Author:** ElderGuard Development Team
