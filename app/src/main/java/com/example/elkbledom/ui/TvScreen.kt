package com.example.elkbledom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LightbulbCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.elkbledom.R
import com.example.elkbledom.ble.ConnectionState
import com.example.elkbledom.ble.LedPattern
import com.example.elkbledom.ble.ScannedDevice
import com.example.elkbledom.ui.components.PatternSelector

// ── Section enum ──────────────────────────────────────────────────────────────

private enum class TvSection(val label: String, val icon: ImageVector) {
    BLUETOOTH("Bluetooth",   Icons.Default.Bluetooth),
    BRIGHTNESS("Brightness", Icons.Default.BrightnessHigh),
    COLOR("Colour",          Icons.Default.Palette),
    PATTERNS("Patterns",     Icons.Default.AutoAwesome),
    MUSIC_SYNC("Music Sync", Icons.Default.MusicNote),
    SCREEN_SYNC("Screen",    Icons.Default.ScreenShare),
    SETTINGS("Settings",     Icons.Default.Settings),
}

// ── Entry point ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvScreen(vm: MainViewModel, onRequestMediaProjection: () -> Unit = {}) {
    val ui by vm.ui.collectAsState()
    var section by remember { mutableStateOf(TvSection.BLUETOOTH) }

    LaunchedEffect(vm) {
        vm.projectionRequest.collect { onRequestMediaProjection() }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Sidebar ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 20.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // App logo + title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.Unspecified,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "ELK-BLEDOM",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Divider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            )

            TvSection.entries.forEach { s ->
                TvNavItem(s, selected = section == s, onClick = { section = s })
            }

            Spacer(Modifier.weight(1f))

            // BT status badge at the bottom
            val connected = ui.connectionState == ConnectionState.CONNECTED
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    if (connected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    tint = if (connected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (connected) "Connected" else "Disconnected",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Divider ───────────────────────────────────────────────────────────
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        )

        // ── Content ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            when (section) {
                TvSection.BLUETOOTH   -> TvBluetoothSection(ui, vm)
                TvSection.BRIGHTNESS  -> TvBrightnessSection(ui, vm)
                TvSection.COLOR       -> TvColorSection(ui, vm)
                TvSection.PATTERNS    -> TvPatternsSection(ui, vm)
                TvSection.MUSIC_SYNC  -> TvMusicSyncSection(ui, vm)
                TvSection.SCREEN_SYNC -> TvScreenSyncSection(ui, vm)
                TvSection.SETTINGS    -> TvSettingsSection(ui, vm)
            }
        }
    }
}

// ── Sidebar nav item ──────────────────────────────────────────────────────────

@Composable
private fun TvNavItem(section: TvSection, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        focused  -> MaterialTheme.colorScheme.surfaceVariant
        else     -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .clickable(onClick = onClick)
            .background(bg, shape)
            .then(
                if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            section.icon,
            contentDescription = null,
            tint = if (selected || focused) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            section.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected || focused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── "Not connected" placeholder ───────────────────────────────────────────────

@Composable
private fun NotConnectedHint() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.BluetoothDisabled, null,
                Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Connect to a device first",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Go to the Bluetooth section to scan and pair.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Section title ─────────────────────────────────────────────────────────────

@Composable
private fun TvSectionTitle(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
    Divider(
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
    )
}

// ── BLUETOOTH section ─────────────────────────────────────────────────────────

@Composable
private fun TvBluetoothSection(ui: UiState, vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TvSectionTitle("Bluetooth", Icons.Default.Bluetooth)

        when (ui.connectionState) {
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                if (ui.connectionState == ConnectionState.ERROR) {
                    Text("Connection failed. Try again.", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                }
                Button(onClick = { vm.startScan() }, modifier = Modifier.height(52.dp)) {
                    Icon(Icons.Default.BluetoothSearching, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan for Devices", fontSize = 17.sp)
                }
            }

            ConnectionState.SCANNING -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text("Scanning…", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(4.dp))
                    FilledTonalButton(onClick = { vm.stopScan() }) { Text("Stop") }
                }
                if (ui.scannedDevices.isEmpty()) {
                    Text(
                        "Looking for BLE devices…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.scannedDevices.forEach { dev ->
                            TvDeviceCard(dev) { vm.connectTo(dev.device) }
                        }
                    }
                }
            }

            ConnectionState.CONNECTING -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text("Connecting…", style = MaterialTheme.typography.bodyLarge)
                }
            }

            ConnectionState.CONNECTED -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Default.BluetoothConnected, null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        "Connected",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { vm.togglePower() }, modifier = Modifier.height(48.dp)) {
                        Icon(
                            if (ui.isPoweredOn) Icons.Default.Lightbulb else Icons.Default.LightbulbCircle,
                            null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (ui.isPoweredOn) "Turn Off" else "Turn On")
                    }
                    FilledTonalButton(onClick = { vm.disconnect() }, modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Default.BluetoothDisabled, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Disconnect")
                    }
                }
            }
        }
    }
}

@Composable
private fun TvDeviceCard(dev: ScannedDevice, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .clickable(onClick = onClick)
            .then(
                if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.BluetoothSearching, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(dev.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    dev.device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${dev.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── BRIGHTNESS section ────────────────────────────────────────────────────────

@Composable
private fun TvBrightnessSection(ui: UiState, vm: MainViewModel) {
    if (ui.connectionState != ConnectionState.CONNECTED) { NotConnectedHint(); return }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSectionTitle("Brightness", Icons.Default.BrightnessHigh)
        Text(
            "${ui.brightness}%",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = ui.brightness.toFloat(),
            onValueChange = { vm.setBrightness(it.toInt()) },
            valueRange = 1f..100f,
            modifier = Modifier.fillMaxWidth(0.8f),
        )
    }
}

// ── COLOUR section (H/S/V sliders — replaces the touch-only colour wheel) ─────

@Composable
private fun TvColorSection(ui: UiState, vm: MainViewModel) {
    if (ui.connectionState != ConnectionState.CONNECTED) { NotConnectedHint(); return }
    val (r, g, b) = hsvToRgb(ui.hue, ui.saturation, ui.colorValue)

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TvSectionTitle("Colour", Icons.Default.Palette)

        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            // Live colour preview
            Box(
                modifier = Modifier
                    .size(180.dp, 140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(r / 255f, g / 255f, b / 255f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp),
                    ),
            )

            // H / S / V sliders
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TvSliderRow(
                    label = "Hue",
                    valueText = "${ui.hue.toInt()}°",
                    value = ui.hue,
                    range = 0f..360f,
                    onValueChange = { vm.setHueSaturation(it, ui.saturation) },
                )
                TvSliderRow(
                    label = "Saturation",
                    valueText = "${(ui.saturation * 100).toInt()}%",
                    value = ui.saturation,
                    range = 0f..1f,
                    onValueChange = { vm.setHueSaturation(ui.hue, it) },
                )
                TvSliderRow(
                    label = "Brightness",
                    valueText = "${(ui.colorValue * 100).toInt()}%",
                    value = ui.colorValue,
                    range = 0f..1f,
                    onValueChange = { vm.setColorValue(it) },
                )
            }
        }
    }
}

@Composable
private fun TvSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(110.dp))
            Text(
                valueText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── PATTERNS section ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TvPatternsSection(ui: UiState, vm: MainViewModel) {
    if (ui.connectionState != ConnectionState.CONNECTED) { NotConnectedHint(); return }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TvSectionTitle("Patterns", Icons.Default.AutoAwesome)
        PatternSelector(
            selectedPattern = ui.selectedPattern,
            patternSpeed = ui.patternSpeed,
            onPatternSelected = vm::selectPattern,
            onSpeedChanged = vm::setPatternSpeed,
            modifier = Modifier.fillMaxWidth(0.8f),
        )
    }
}

// ── MUSIC SYNC section ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvMusicSyncSection(ui: UiState, vm: MainViewModel) {
    if (ui.connectionState != ConnectionState.CONNECTED) { NotConnectedHint(); return }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvSectionTitle("Music Sync", Icons.Default.MusicNote)

        TvToggleRow("Enabled", ui.isMusicSync) { vm.setMusicSync(it) }

        AnimatedVisibility(visible = ui.isMusicSync) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Audio source
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = ui.audioMode == AudioMode.MIC,
                        onClick = { vm.setAudioMode(AudioMode.MIC) },
                        label = { Text("Microphone", fontSize = 15.sp) },
                        leadingIcon = { Icon(Icons.Default.Mic, null, Modifier.size(18.dp)) },
                        modifier = Modifier.height(44.dp),
                    )
                    FilterChip(
                        selected = ui.audioMode == AudioMode.PLAYBACK,
                        onClick = { if (ui.isPlaybackSupported) vm.setAudioMode(AudioMode.PLAYBACK) },
                        enabled = ui.isPlaybackSupported,
                        label = { Text("Phone Audio", fontSize = 15.sp) },
                        leadingIcon = { Icon(Icons.Default.PhoneAndroid, null, Modifier.size(18.dp)) },
                        modifier = Modifier.height(44.dp),
                    )
                }

                // Per-band colour pickers (reuses mobile composables)
                Text("Band Colours", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BandColorRow("Bass",  ui.bassColor,  vm::setBassColor)
                BandColorRow("Mids",  ui.midColor,   vm::setMidColor)
                BandColorRow("Highs", ui.highColor,  vm::setHighColor)

                // Frequency bars visualiser
                FreqBars(
                    bass = ui.freqData.bass, mid = ui.freqData.mid, high = ui.freqData.high,
                    isBeat = ui.freqData.isBeat,
                    bassColor  = ui.bassColor.toComposeColor(),
                    midColor   = ui.midColor.toComposeColor(),
                    highColor  = ui.highColor.toComposeColor(),
                    bassActive = ui.bassColor != SyncColor.OFF,
                    midActive  = ui.midColor  != SyncColor.OFF,
                    highActive = ui.highColor != SyncColor.OFF,
                )
            }
        }
    }
}

// ── SCREEN SYNC section ───────────────────────────────────────────────────────

@Composable
private fun TvScreenSyncSection(ui: UiState, vm: MainViewModel) {
    if (ui.connectionState != ConnectionState.CONNECTED) { NotConnectedHint(); return }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSectionTitle("Screen Sync", Icons.Default.ScreenShare)

        if (!ui.isPlaybackSupported) {
            Text("Screen Sync requires Android 10+", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        TvToggleRow("Enabled", ui.isScreenSync, enabled = ui.isPlaybackSupported) {
            vm.setScreenSync(it)
        }

        AnimatedVisibility(visible = ui.isScreenSync) {
            val previewColor = Color(ui.screenR / 255f, ui.screenG / 255f, ui.screenB / 255f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Box(
                    Modifier
                        .size(140.dp, 100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                )
                Column {
                    Text("Dominant colour", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "#%02X%02X%02X".format(ui.screenR, ui.screenG, ui.screenB),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── SETTINGS section ──────────────────────────────────────────────────────────

@Composable
private fun TvSettingsSection(ui: UiState, vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSectionTitle("Settings", Icons.Default.Settings)
        TvCheckboxRow(
            label = "Ambilight smooth",
            description = "Blends colour changes gradually — easier on the eyes during sync modes.",
            checked = ui.isAmbilightSmooth,
            onCheckedChange = { vm.setAmbilightSmooth(it) },
        )
    }
}

// ── Reusable TV control rows ──────────────────────────────────────────────────

@Composable
private fun TvToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(100.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun TvCheckboxRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
