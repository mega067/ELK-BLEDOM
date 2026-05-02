package com.example.elkbledom.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.elkbledom.ble.LedPattern

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatternSelector(
    selectedPattern: LedPattern,
    patternSpeed: Float,
    onPatternSelected: (LedPattern) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text("Patterns", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LedPattern.entries.forEach { pattern ->
                FilterChip(
                    selected = selectedPattern == pattern,
                    onClick = { onPatternSelected(pattern) },
                    label = { Text(pattern.displayName) },
                )
            }
        }

        AnimatedVisibility(visible = selectedPattern != LedPattern.SOLID) {
            Column {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Speed: ${(patternSpeed * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = patternSpeed,
                    onValueChange = onSpeedChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
