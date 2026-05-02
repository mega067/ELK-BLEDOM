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
import kotlinx.coroutines.Job
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
    val patternSpeed: Float = 0.5f,
    val isMusicSync: Boolean = false,
    val audioMode: AudioMode = AudioMode.MIC,
    val freqData: FrequencyData = FrequencyData(0f, 0f, 0f, false),
    val isPlaybackSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    // Per-band colour — OFF disables that band's contribution
    val bassColor: SyncColor = SyncColor.RED,
    val midColor: SyncColor = SyncColor.GREEN,
    val highColor: SyncColor = SyncColor.BLUE,
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

    init {
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                _ui.update { it.copy(connectionState = state) }
                if (state == ConnectionState.CONNECTED) applyCurrentColor()
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
        stopMusicSync()
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
        if (_ui.value.isMusicSync || _ui.value.selectedPattern != LedPattern.SOLID) return
        val s = _ui.value
        val (r, g, b) = hsvToRgb(s.hue, s.saturation, s.colorValue)
        send(ELKBledomProtocol.setColor(r, g, b))
    }

    // ── Patterns ──────────────────────────────────────────────────────────────

    fun selectPattern(pattern: LedPattern) {
        _ui.update { it.copy(selectedPattern = pattern) }
        if (pattern == LedPattern.SOLID) {
            applyCurrentColor()
        } else {
            val speed = (_ui.value.patternSpeed * 255).toInt().coerceIn(1, 255)
            send(ELKBledomProtocol.setEffect(pattern.effectCode, speed))
        }
    }

    fun setPatternSpeed(speed: Float) {
        _ui.update { it.copy(patternSpeed = speed) }
        val s = _ui.value
        if (s.selectedPattern != LedPattern.SOLID) {
            send(ELKBledomProtocol.setEffect(s.selectedPattern.effectCode, (speed * 255).toInt().coerceIn(1, 255)))
        }
    }

    // ── Music sync ────────────────────────────────────────────────────────────

    fun setMusicSync(enabled: Boolean) {
        _ui.update { it.copy(isMusicSync = enabled) }
        if (enabled) startMusicSync() else stopMusicSync()
    }

    fun setAudioMode(mode: AudioMode) {
        _ui.update { it.copy(audioMode = mode) }
        if (mode == AudioMode.PLAYBACK && mediaProjection == null) {
            _projectionRequest.tryEmit(Unit)
        } else if (_ui.value.isMusicSync) {
            restartMusicSync()
        }
    }

    fun setBassColor(color: SyncColor)  = _ui.update { it.copy(bassColor = color) }
    fun setMidColor(color: SyncColor)   = _ui.update { it.copy(midColor  = color) }
    fun setHighColor(color: SyncColor)  = _ui.update { it.copy(highColor = color) }

    fun onMediaProjection(mp: MediaProjection?, cancelled: Boolean = false) {
        mediaProjection?.stop()
        mediaProjection = mp
        when {
            mp == null && cancelled -> _ui.update { it.copy(audioMode = AudioMode.MIC) }
            mp != null && _ui.value.isMusicSync -> restartMusicSync()
        }
    }

    fun releaseProjection() {
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

            flow.collect { data ->
                _ui.update { it.copy(freqData = data) }
                if (_ui.value.connectionState == ConnectionState.CONNECTED) {
                    val s = _ui.value
                    // Each band contributes its chosen colour scaled by energy level.
                    // Colours are mixed additively, clamped to 0–255.
                    val (br, bg, bb) = bandContrib(data.bass, s.bassColor)
                    val (mr, mg, mb) = bandContrib(data.mid,  s.midColor)
                    val (hr, hg, hb) = bandContrib(data.high, s.highColor)
                    send(ELKBledomProtocol.setColor(
                        (br + mr + hr).coerceIn(0, 255),
                        (bg + mg + hg).coerceIn(0, 255),
                        (bb + mb + hb).coerceIn(0, 255),
                    ))
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
