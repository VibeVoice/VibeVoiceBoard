// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.KeyboardAccent
import helium314.keyboard.latin.settings.KeyboardPreset
import helium314.keyboard.latin.settings.KeyboardPresets
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity

private fun accentLabel(accent: KeyboardAccent) = when (accent) {
    KeyboardAccent.VIBEVOICE -> R.string.keyboard_accent_vibevoice
    KeyboardAccent.VIOLET -> R.string.keyboard_accent_violet
    KeyboardAccent.GREEN -> R.string.keyboard_accent_green
    KeyboardAccent.ORANGE -> R.string.keyboard_accent_orange
    KeyboardAccent.PINK -> R.string.keyboard_accent_pink
    KeyboardAccent.GRAPHITE -> R.string.keyboard_accent_graphite
    KeyboardAccent.CUSTOM -> R.string.keyboard_accent_custom
}

private val rainbow = listOf(
    Color(0xFFFF3B30), Color(0xFFFFD60A), Color(0xFF34C759), Color(0xFF30D5C8), Color(0xFF8B5CF6), Color(0xFFFF2D92), Color(0xFFFF3B30)
)

/**
 * A row of one dot per [KeyboardAccent.PALETTE] color, then a rainbow dot for a custom color. Choosing one stores
 * it and restyles the keyboard at once; the rainbow dot opens [AccentColorDialog].
 * Without a stored choice, Gboard and Samsung show violet as selected and Own shows none (it keeps its theme color).
 */
@Composable
fun AccentDots(modifier: Modifier = Modifier, ringColor: Color = MaterialTheme.colorScheme.onSurface) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    // keyed on the stored values, so a preset switch (which decides what "no choice" shows) refreshes the dots
    val preset = prefs.getString(Settings.PREF_KEYBOARD_PRESET, null)
    val stored = prefs.getString(Settings.PREF_KEYBOARD_ACCENT, null)
    val storedCustom = KeyboardPresets.readCustomColor(prefs)
    var selected by remember(preset, stored, storedCustom) {
        mutableStateOf(
            KeyboardPresets.readAccent(prefs)
                ?: if (Settings.readKeyboardPreset(prefs) == KeyboardPreset.OWN) null else KeyboardAccent.VIOLET
        )
    }
    var customColor by remember(storedCustom) { mutableStateOf(storedCustom) }
    var showPicker by remember { mutableStateOf(false) }

    fun restyle() {
        (ctx.getActivity() as? SettingsActivity)?.prefChanged()
        KeyboardSwitcher.getInstance().reloadKeyboard()
    }

    @Composable
    fun Dot(isSelected: Boolean, label: String, fill: Brush, onClick: () -> Unit) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(BorderStroke(if (isSelected) 2.dp else 0.dp, if (isSelected) ringColor else Color.Transparent), CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .background(fill)
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label }
        )
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        KeyboardAccent.PALETTE.forEach { accent ->
            Dot(selected == accent, stringResource(accentLabel(accent)), Brush.linearGradient(listOf(Color(accent.color), Color(accent.color)))) {
                selected = accent
                KeyboardPresets.setAccent(ctx, accent)
                restyle()
            }
        }
        val custom = customColor
        Dot(
            selected == KeyboardAccent.CUSTOM, stringResource(R.string.keyboard_accent_custom),
            if (selected == KeyboardAccent.CUSTOM && custom != null) Brush.linearGradient(listOf(Color(custom), Color(custom)))
            else Brush.sweepGradient(rainbow)
        ) { showPicker = true }
    }

    if (showPicker) {
        AccentColorDialog(
            initial = customColor ?: KeyboardPresets.readAccentColor(prefs) ?: KeyboardAccent.VIOLET.color,
            onDismiss = { showPicker = false },
            onConfirm = { color ->
                showPicker = false
                customColor = color or 0xFF000000.toInt()
                selected = KeyboardAccent.CUSTOM
                KeyboardPresets.setCustomAccent(ctx, color)
                restyle()
            }
        )
    }
}

/** Hue, saturation and brightness sliders with a live swatch and the hex value. */
@Composable
fun AccentColorDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val hsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember(initial) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(initial) { mutableFloatStateOf(hsv[1]) }
    var value by remember(initial) { mutableFloatStateOf(hsv[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    @Composable
    fun GradientSlider(v: Float, range: ClosedFloatingPointRange<Float>, brush: Brush, label: String, onChange: (Float) -> Unit) {
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(10.dp).padding(horizontal = 10.dp).clip(RoundedCornerShape(5.dp)).background(brush))
            Slider(
                value = v, onValueChange = onChange, valueRange = range,
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                modifier = Modifier.semantics { contentDescription = label }
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keyboard_accent_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(argb)))
                    Text("#%06X".format(argb and 0xFFFFFF), style = MaterialTheme.typography.titleMedium)
                }
                GradientSlider(hue, 0f..360f, Brush.horizontalGradient(rainbow), stringResource(R.string.keyboard_accent_hue)) { hue = it }
                GradientSlider(sat, 0f..1f, Brush.horizontalGradient(listOf(Color.White, hueColor)), stringResource(R.string.keyboard_accent_saturation)) { sat = it }
                GradientSlider(value, 0f..1f, Brush.horizontalGradient(listOf(Color.Black, hueColor)), stringResource(R.string.keyboard_accent_brightness)) { value = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(argb) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}
