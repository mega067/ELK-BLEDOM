package com.example.elkbledom.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.media.projection.MediaProjection
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.elkbledom.audio.AudioAnalyzer
import com.example.elkbledom.audio.FrequencyData
import com.example.elkbledom.ble.BleManager
import com.example.elkbledom.ble.ConnectionState
import com.example.elkbledom.ble.ELKBledomProtocol
import com.example.elkbledom.ble.LedPattern
import com.example.elkbledom.ble.ScannedDevice
import com.example.elkbledom.screen.ScreenAnalyzer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AudioMode { MIC, PLAYBACK }

/**
 * Selectable colour for each frequency band.
 * OFF means that band produces no output.
 * RGB values are 0–255 and mixed additively across bands.
 */
enum class SyncColor(val r: Int, val g: Int, val b: Int) {
    OFF     (0,   0,   0  ),
    RED     (255, 0,   0  ),
    ORANGE  (255, 90,  0  ),
    YELLOW  (255, 210, 0  ),
    GREEN   (0,   255, 0  ),
    CYAN    (0,   255, 220),
    BLUE    (0,   0,   255),
    VIOLET  (120, 0,   255),
    MAGENTA (255, 0,   180),
    PINK    (255, 60,  120),
    WHITE   (255, 255, 255),
}

data class UiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val isPoweredOn: Boolean = true,
    val hue: Float = 0f,
    val saturation: Float = 1f,
    val colorValue: Float = 1f,
    val brightness: Int = 100,
    val selectedPattern: LedPattern = LedPattern.SOLID,
    val patternSpeedMs: Long = 200L,
    val isMusicSync: Boolean = false,
    val audioMode: AudioMode = AudioMode.MIC,
    val freqData: FrequencyData = FrequencyData(0f, 0f, 0f, false),
    val isPlaybackSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    val bassColor: SyncColor = SyncColor.RED,
    val midColor: SyncColor = SyncColor.GREEN,
    val highColor: SyncColor = SyncColor.BLUE,
    val isScreenSync: Boolean = false,
    val screenR: Int = 0,
    val screenG: Int = 0,
    val screenB: Int = 0,
    val isAmbilightSmooth: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val bleManager = BleManager(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _projectionRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val projectionRequest: SharedFlow<Unit> = _projectionRequest.asSharedFlow()

    private var mediaProjection: MediaProjection? = null
    private var connectJob: Job? = null
    private var musicSyncJob: Job? = null
    private var screenSyncJob: Job? = null
    private var patternJob: Job? = null

    // Delay read dynamically each iteration so typing a new value takes effect immediately
    private val holdMs get() = _ui.value.patternSpeedMs.coerceAtLeast(10L)
    private val fadeStepMs get() = _ui.value.patternSpeedMs.coerceAtLeast(10L)

    init {
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                _ui.update { it.copy(connectionState = state) }
                if (state == ConnectionState.CONNECTED) {
                    if (_ui.value.selectedPattern == LedPattern.SOLID) {
                        applyCurrentColor()
                    } else {
                        startPatternLoop()
                    }
                }
            }
        }
        viewModelScope.launch {
            bleManager.scannedDevices.collect { devices ->
                _ui.update { it.copy(scannedDevices = devices) }
            }
        }
    }

    // ── Scanning / Connection ─────────────────────────────────────────────────

    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()

    fun connectTo(device: BluetoothDevice) {
        connectJob?.cancel()
        connectJob = viewModelScope.launch { bleManager.connect(device) }
    }

    fun disconnect() {
        stopPatternLoop()
        stopMusicSync()
        stopScreenSync()
        bleManager.disconnect()
    }

    // ── Power ─────────────────────────────────────────────────────────────────

    fun togglePower() {
        val on = !_ui.value.isPoweredOn
        _ui.update { it.copy(isPoweredOn = on) }
        send(if (on) ELKBledomProtocol.powerOn() else ELKBledomProtocol.powerOff())
    }

    // ── Brightness ────────────────────────────────────────────────────────────

    fun setBrightness(value: Int) {
        _ui.update { it.copy(brightness = value) }
        send(ELKBledomProtocol.setBrightness(value))
    }

    // ── Static color ──────────────────────────────────────────────────────────

    fun setHueSaturation(hue: Float, saturation: Float) {
        _ui.update { it.copy(hue = hue, saturation = saturation) }
        applyCurrentColor()
    }

    fun setColorValue(value: Float) {
        _ui.update { it.copy(colorValue = value) }
        applyCurrentColor()
    }

    private fun applyCurrentColor() {
        if (_ui.value.isMusicSync || _ui.value.isScreenSync || _ui.value.selectedPattern != LedPattern.SOLID) return
        val s = _ui.value
        val (r, g, b) = hsvToRgb(s.hue, s.saturation, s.colorValue)
        send(ELKBledomProtocol.setColor(r, g, b))
    }

    // ── Patterns ──────────────────────────────────────────────────────────────

    fun selectPattern(pattern: LedPattern) {
        _ui.update { it.copy(selectedPattern = pattern) }
        stopPatternLoop()
        if (pattern == LedPattern.SOLID) {
            applyCurrentColor()
        } else {
            startPatternLoop()
        }
    }

    fun setPatternSpeedMs(ms: Long) {
        _ui.update { it.copy(patternSpeedMs = ms.coerceIn(10L, 5000L)) }
    }

    private fun startPatternLoop() {
        patternJob?.cancel()
        val pattern = _ui.value.selectedPattern
        if (pattern == LedPattern.SOLID) return
        patternJob = viewModelScope.launch {
            when (pattern) {
                LedPattern.JUMP_RGB    -> runJump(RGB_3)
                LedPattern.JUMP_ALL    -> runJump(RGB_7)
                LedPattern.FADE_RGB    -> runFade(RGB_3)
                LedPattern.FADE_ALL    -> runFade(RGB_7)
                LedPattern.CROSSFADE_R -> runPulse(255, 0, 0)
                LedPattern.CROSSFADE_GB -> runFade(GB_2)
                LedPattern.CROSSFADE_BO -> runFade(BO_2)
                LedPattern.CROSSFADE_B -> runPulse(0, 0, 255)
                LedPattern.CROSSFADE_W -> runPulse(255, 255, 255)
                LedPattern.FLASH_RGB   -> runJump(FLASH_RGB_SEQ)
                LedPattern.FLASH_ALL   -> runJump(FLASH_ALL_SEQ)
                LedPattern.STROBE_W    -> runStrobe()
                LedPattern.SOLID       -> { }
            }
        }
    }

    private fun stopPatternLoop() {
        patternJob?.cancel()
        patternJob = null
    }

    // Instantly jump to each color in sequence and hold
    private suspend fun runJump(colors: List<Triple<Int, Int, Int>>) {
        var idx = 0
        while (true) {
            val (r, g, b) = colors[idx % colors.size]
            send(ELKBledomProtocol.setColor(r, g, b))
            delay(holdMs)
            idx++
        }
    }

    // Smooth linear interpolation between consecutive colors in a loop
    private suspend fun runFade(colors: List<Triple<Int, Int, Int>>) {
        val steps = 50
        var fromIdx = 0
        while (true) {
            val toIdx = (fromIdx + 1) % colors.size
            val (r1, g1, b1) = colors[fromIdx]
            val (r2, g2, b2) = colors[toIdx]
            for (step in 0..steps) {
                val t = step.toFloat() / steps
                send(ELKBledomProtocol.setColor(
                    (r1 + t * (r2 - r1)).toInt(),
                    (g1 + t * (g2 - g1)).toInt(),
                    (b1 + t * (b2 - b1)).toInt(),
                ))
                delay(fadeStepMs)
            }
            fromIdx = toIdx
        }
    }

    // Fade a single color in from black and back out repeatedly
    private suspend fun runPulse(r: Int, g: Int, b: Int) {
        val steps = 50
        while (true) {
            for (step in 0..steps) {
                val t = step.toFloat() / steps
                send(ELKBledomProtocol.setColor((r * t).toInt(), (g * t).toInt(), (b * t).toInt()))
                delay(fadeStepMs)
            }
            for (step in steps downTo 0) {
                val t = step.toFloat() / steps
                send(ELKBledomProtocol.setColor((r * t).toInt(), (g * t).toInt(), (b * t).toInt()))
                delay(fadeStepMs)
            }
        }
    }

    // White strobe: on/off at the configured delay
    private suspend fun runStrobe() {
        while (true) {
            send(ELKBledomProtocol.setColor(255, 255, 255))
            delay(holdMs)
            send(ELKBledomProtocol.setColor(0, 0, 0))
            delay(holdMs)
        }
    }

    // ── Music sync ────────────────────────────────────────────────────────────

    fun setMusicSync(enabled: Boolean) {
        if (enabled && _ui.value.isScreenSync) {
            stopScreenSync()
            _ui.update { it.copy(isScreenSync = false) }
        }
        _ui.update { it.copy(isMusicSync = enabled) }
        if (enabled) {
            stopPatternLoop()
            startMusicSync()
        } else {
            stopMusicSync()
            if (_ui.value.selectedPattern != LedPattern.SOLID) startPatternLoop()
        }
    }

    // ── Screen sync ───────────────────────────────────────────────────────────

    fun setScreenSync(enabled: Boolean) {
        if (enabled && _ui.value.isMusicSync) {
            stopMusicSync()
            _ui.update { it.copy(isMusicSync = false) }
        }
        _ui.update { it.copy(isScreenSync = enabled) }
        if (enabled) {
            stopPatternLoop()
            startScreenSync()
        } else {
            stopScreenSync()
            if (_ui.value.selectedPattern != LedPattern.SOLID) startPatternLoop()
        }
    }

    private fun startScreenSync() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val mp = mediaProjection
        if (mp == null) {
            _projectionRequest.tryEmit(Unit)
            return
        }
        screenSyncJob?.cancel()
        screenSyncJob = viewModelScope.launch {
            ScreenAnalyzer.stream(mp, getApplication()).collect { color ->
                val s = _ui.value
                val alpha = if (s.isAmbilightSmooth) 0.07f else 0.25f
                val r = (s.screenR + alpha * (color.r - s.screenR)).toInt()
                val g = (s.screenG + alpha * (color.g - s.screenG)).toInt()
                val b = (s.screenB + alpha * (color.b - s.screenB)).toInt()
                _ui.update { it.copy(screenR = r, screenG = g, screenB = b) }
                if (s.connectionState == ConnectionState.CONNECTED) {
                    send(ELKBledomProtocol.setColor(r, g, b))
                }
            }
        }
    }

    private fun stopScreenSync() {
        screenSyncJob?.cancel()
        screenSyncJob = null
        _ui.update { it.copy(screenR = 0, screenG = 0, screenB = 0) }
    }

    fun setAudioMode(mode: AudioMode) {
        _ui.update { it.copy(audioMode = mode) }
        if (mode == AudioMode.PLAYBACK && mediaProjection == null) {
            _projectionRequest.tryEmit(Unit)
        } else if (_ui.value.isMusicSync) {
            restartMusicSync()
        }
    }

    fun setBassColor(color: SyncColor)       = _ui.update { it.copy(bassColor = color) }
    fun setMidColor(color: SyncColor)        = _ui.update { it.copy(midColor  = color) }
    fun setHighColor(color: SyncColor)       = _ui.update { it.copy(highColor = color) }
    fun setAmbilightSmooth(enabled: Boolean) = _ui.update { it.copy(isAmbilightSmooth = enabled) }

    fun onMediaProjection(mp: MediaProjection?, cancelled: Boolean = false) {
        mediaProjection?.stop()
        mediaProjection = mp
        when {
            mp == null && cancelled -> _ui.update { it.copy(audioMode = AudioMode.MIC, isScreenSync = false) }
            mp != null && _ui.value.isMusicSync -> restartMusicSync()
            mp != null && _ui.value.isScreenSync -> startScreenSync()
        }
    }

    fun releaseProjection() {
        stopScreenSync()
        _ui.update { it.copy(isScreenSync = false) }
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun startMusicSync() {
        musicSyncJob?.cancel()
        musicSyncJob = viewModelScope.launch {
            val flow = if (
                _ui.value.audioMode == AudioMode.PLAYBACK &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                mediaProjection != null
            ) {
                AudioAnalyzer.streamPlayback(mediaProjection!!)
            } else {
                AudioAnalyzer.streamMic()
            }

            var sr = 0f; var sg = 0f; var sb = 0f

            flow.collect { data ->
                _ui.update { it.copy(freqData = data) }
                if (_ui.value.connectionState == ConnectionState.CONNECTED) {
                    val s = _ui.value
                    val (br, bg, bb) = bandContrib(data.bass, s.bassColor)
                    val (mr, mg, mb) = bandContrib(data.mid,  s.midColor)
                    val (hr, hg, hb) = bandContrib(data.high, s.highColor)
                    val tr = (br + mr + hr).coerceIn(0, 255).toFloat()
                    val tg = (bg + mg + hg).coerceIn(0, 255).toFloat()
                    val tb = (bb + mb + hb).coerceIn(0, 255).toFloat()
                    val alpha = if (s.isAmbilightSmooth) 0.2f else 1f
                    sr += alpha * (tr - sr)
                    sg += alpha * (tg - sg)
                    sb += alpha * (tb - sb)
                    send(ELKBledomProtocol.setColor(sr.toInt(), sg.toInt(), sb.toInt()))
                }
            }
        }
    }

    private fun stopMusicSync() {
        musicSyncJob?.cancel()
        musicSyncJob = null
        _ui.update { it.copy(freqData = FrequencyData(0f, 0f, 0f, false)) }
    }

    private fun restartMusicSync() { stopMusicSync(); startMusicSync() }

    private fun send(cmd: ByteArray) = bleManager.sendCommand(cmd)

    override fun onCleared() {
        super.onCleared()
        stopPatternLoop()
        mediaProjection?.stop()
        bleManager.disconnect()
    }
}

/** Scale a SyncColor by [level] (0–1). Returns (0,0,0) when color is OFF. */
private fun bandContrib(level: Float, color: SyncColor): Triple<Int, Int, Int> {
    if (color == SyncColor.OFF) return Triple(0, 0, 0)
    return Triple(
        (color.r * level).toInt(),
        (color.g * level).toInt(),
        (color.b * level).toInt(),
    )
}

fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Int, Int, Int> {
    val hh = ((h % 360f + 360f) % 360f) / 60f
    val i = hh.toInt()
    val ff = hh - i
    val p = (v * (1f - s) * 255f).toInt().coerceIn(0, 255)
    val q = (v * (1f - s * ff) * 255f).toInt().coerceIn(0, 255)
    val t = (v * (1f - s * (1f - ff)) * 255f).toInt().coerceIn(0, 255)
    val vv = (v * 255f).toInt().coerceIn(0, 255)
    return when (i % 6) {
        0 -> Triple(vv, t, p)
        1 -> Triple(q, vv, p)
        2 -> Triple(p, vv, t)
        3 -> Triple(p, q, vv)
        4 -> Triple(t, p, vv)
        else -> Triple(vv, p, q)
    }
}

// ── Pattern color tables ──────────────────────────────────────────────────────

private val RGB_3 = listOf(
    Triple(255, 0, 0),
    Triple(0, 255, 0),
    Triple(0, 0, 255),
)

private val RGB_7 = listOf(
    Triple(255, 0,   0),
    Triple(255, 128, 0),
    Triple(255, 255, 0),
    Triple(0,   255, 0),
    Triple(0,   255, 255),
    Triple(0,   0,   255),
    Triple(255, 0,   255),
)

private val GB_2 = listOf(
    Triple(0,255,0),
    Triple(0,0,255),
)

private val BO_2 = listOf(
    Triple(0,0,255),
    Triple(255,165,0),
)

private val FLASH_RGB_SEQ = listOf(
    Triple(255, 0, 0), Triple(0, 0, 0),
    Triple(0, 255, 0), Triple(0, 0, 0),
    Triple(0, 0, 255), Triple(0, 0, 0),
)

private val FLASH_ALL_SEQ = listOf(
    Triple(255, 0,   0),   Triple(0, 0, 0),
    Triple(255, 128, 0),   Triple(0, 0, 0),
    Triple(255, 255, 0),   Triple(0, 0, 0),
    Triple(0,   255, 0),   Triple(0, 0, 0),
    Triple(0,   255, 255), Triple(0, 0, 0),
    Triple(0,   0,   255), Triple(0, 0, 0),
    Triple(255, 0,   255), Triple(0, 0, 0),
)