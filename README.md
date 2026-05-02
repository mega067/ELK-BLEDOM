# ELK-BLEDOM LED Controller

An Android app built with **Kotlin + Jetpack Compose** for controlling ELK-BLEDOM Bluetooth LE LED strips. Includes a colour picker, brightness control, built-in light patterns, and a real-time **music sync mode** that maps frequency bands to LED colours using live FFT analysis.

---

## Features

- **Bluetooth LE scanning** — discovers nearby ELK-BLEDOM devices and lists them by signal strength
- **Colour picker** — HSV colour wheel with brightness slider and 10 preset swatches
- **Brightness control** — global brightness slider (1–100 %)
- **Pattern selection** — 11 built-in LED effects (Jump RGB, Fade, Flash, Strobe, Crossfade, and more) with adjustable speed
- **Music sync** — real-time 4096-point FFT drives the LEDs from audio:
  - **Microphone mode** — picks up room sound; works with any audio source
  - **Phone Audio mode** *(Android 10+)* — captures internal playback directly; works with headphones
  - **Per-band colour selection** — assign any of 11 colours (or Off) independently to Bass, Mids, and Highs
  - **Additive colour mixing** — all three bands blend simultaneously on the strip
  - **Beat detection** — energy-threshold algorithm highlights kick drums and transients

---

## Screenshots

> *(Add screenshots here)*

---

## Requirements

| Requirement | Detail |
|---|---|
| Android version | 8.0 Oreo (API 26) or higher |
| Phone Audio mode | Android 10 (API 29) or higher |
| Device | Physical Android device — BLE and microphone do not work on emulators |
| LED controller | ELK-BLEDOM or compatible BLE LED strip (BLEDOM, LEDBLE, etc.) |

---

## Build instructions

### 1 — Install the JDK

Download and install **JDK 21** (Temurin recommended):
```
https://adoptium.net/temurin/releases/?version=21
```
Verify:
```bash
java -version   # should print 21.x
```

### 2 — Install the Android SDK (no Android Studio needed)

Download **command-line tools only** from:
```
https://developer.android.com/studio#command-line-tools-only
```

Extract to `C:\Android\cmdline-tools\latest\`, then install SDK components:
```bash
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Set environment variables:
```
ANDROID_HOME = C:\Android
PATH        += C:\Android\platform-tools
PATH        += C:\Android\cmdline-tools\latest\bin
```

### 3 — Configure the project

Create `local.properties` in the project root:
```properties
sdk.dir=C\:\\Android
```

### 4 — Get the Gradle wrapper

Download the wrapper JAR and place it at `gradle/wrapper/gradle-wrapper.jar`:
```
https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar
```

Or if you have Gradle installed locally:
```bash
gradle wrapper --gradle-version=8.9
```

### 5 — Build

```bash
./gradlew assembleDebug
```

The APK is output to:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 6 — Install on device

Enable **USB Debugging** on your phone (*Settings → Developer options*), then:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

> **Out of memory during build?**
> Edit `gradle.properties` and raise the heap: `-Xmx4g` (or `-Xmx6g` on machines with ≥ 12 GB RAM).

---

## Usage

### Connecting to the LED strip

1. Power on your LED strip
2. Open the app and tap **Scan for devices**
3. Tap your device in the list (typically named `ELK-BLEDOM`)
4. The status icon turns green when connected

### Static colour

- Drag the **colour wheel** to set hue and saturation
- Use the **brightness slider** under the wheel to set value
- Tap a **preset swatch** for quick colour changes
- The global **Brightness** slider at the top controls overall LED intensity independently

### Patterns

- Select any pattern chip (Jump RGB, Fade, Flash, etc.)
- Adjust **Speed** with the slider that appears
- Select **Solid** to return to static colour mode

### Music Sync

1. Toggle **Music Sync** on
2. Choose an audio source:
   - **Microphone** — listens to the room; works everywhere
   - **Phone Audio** — captures app playback directly; requires Android 10+ and a one-time system consent prompt. Note: DRM-protected apps (Spotify, Netflix) block internal capture — use microphone mode for those.
3. Set a colour for each frequency band by tapping swatches in the **Band Colours** rows:
   - **Bass** (60–250 Hz) — defaults to Red
   - **Mids** (250–4000 Hz) — defaults to Green
   - **Highs** (4000–16000 Hz) — defaults to Blue
   - Tap **✕** to mute a band entirely
4. All active bands are **mixed additively** in real time on the LED strip

---

## Technical details

### BLE protocol (ELK-BLEDOM)

| Command | Bytes |
|---|---|
| Power on | `7E 04 04 F0 00 01 FF 00 EF` |
| Power off | `7E 04 04 00 00 00 FF 00 EF` |
| Set colour (R, G, B) | `7E 07 05 03 RR GG BB 10 EF` |
| Set brightness (0–100) | `7E 04 01 BR 00 00 00 00 EF` |
| Set effect (code, speed) | `7E 05 03 EF SP 03 00 00 EF` |

All writes use `WRITE_TYPE_NO_RESPONSE` for low latency. The app tries the primary service/characteristic UUID pair (`FFF0`/`FFF3`) and falls back to the alternate pair (`FFE5`/`FFE9`) automatically.

### Audio analysis

- **Sample rate:** 44 100 Hz
- **FFT size:** 4096 points (Cooley-Tukey radix-2, in-place)
- **Window:** Hann, applied before each transform
- **Frequency resolution:** ≈ 10.8 Hz per bin
- **Update rate:** ≈ 10 Hz (one FFT per filled buffer)
- **Dynamic normalisation:** per-band rolling peak with 0.5 % per-frame decay keeps bars full-range regardless of playback volume
- **Beat detection:** current bass-weighted energy vs. 4-second rolling average; fires when ratio exceeds 1.4×

### Colour mixing

Each band's energy (0–1) scales its assigned RGB colour, and all three contributions are summed:

```
R = clamp(bassLevel × bassColor.r  +  midLevel × midColor.r  +  highLevel × highColor.r,  0, 255)
G = clamp(bassLevel × bassColor.g  +  midLevel × midColor.g  +  highLevel × highColor.g,  0, 255)
B = clamp(bassLevel × bassColor.b  +  midLevel × midColor.b  +  highLevel × highColor.b,  0, 255)
```

---

## Project structure

```
app/src/main/java/com/example/elkbledom/
├── MainActivity.kt                 # Permission flow, BT enable, MediaProjection launcher
├── MediaProjectionService.kt       # Foreground service required for internal audio capture
├── ble/
│   ├── BleManager.kt               # Scan, GATT connect, WRITE_NO_RESPONSE command sender
│   └── ELKBledomProtocol.kt        # Pure byte-frame builders + LedPattern enum
├── audio/
│   └── AudioAnalyzer.kt            # AudioRecord → Hann window → FFT → band energy → Flow
└── ui/
    ├── MainViewModel.kt            # State holder, sync logic, SyncColor enum
    ├── MainScreen.kt               # Top-level Compose screen
    ├── components/
    │   ├── ColorPicker.kt          # HSV colour wheel (Canvas bitmap) + sliders
    │   └── PatternSelector.kt      # Pattern chips + speed slider
    └── theme/
        └── Theme.kt                # Dark Material3 colour scheme
```

---

## Permissions

| Permission | Why |
|---|---|
| `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` | BLE device discovery and connection (Android 12+) |
| `ACCESS_FINE_LOCATION` | Required for BLE scanning on Android 11 and below |
| `RECORD_AUDIO` | Microphone input for music sync |
| `FOREGROUND_SERVICE` | Keeps the audio capture service alive |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Required to start a `mediaProjection`-type foreground service (Android 14+) |

---

## Known limitations

- **DRM content** (Spotify, Apple Music, Netflix, etc.) cannot be captured in Phone Audio mode — use Microphone mode instead
- **BLE command rate** is limited by the device firmware; the app sends one colour frame per FFT buffer (~10 Hz), which is within safe limits for all tested controllers
- **First build** takes several minutes to download Gradle and compile the Compose compiler; subsequent builds are incremental and fast

---

## Toolchain

| Tool | Version |
|---|---|
| Kotlin | 2.1.0 |
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.9 |
| Compose BOM | 2024.12.01 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| JDK | 21 |
