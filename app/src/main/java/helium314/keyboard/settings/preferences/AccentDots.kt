// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.KeyboardAccent
import helium314.keyboard.latin.settings.KeyboardPreset
import helium314.keyboard.latin.settings.KeyboardPresets
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.settings.SettingsActivity

private fun accentLabel(accent: KeyboardAccent) = when (accent) {
    KeyboardAccent.VIOLET -> R.string.keyboard_accent_violet
    KeyboardAccent.GREEN -> R.string.keyboard_accent_green
    KeyboardAccent.ORANGE -> R.string.keyboard_accent_orange
    KeyboardAccent.PINK -> R.string.keyboard_accent_pink
    KeyboardAccent.GRAPHITE -> R.string.keyboard_accent_graphite
}

/**
 * A row of one dot per [KeyboardAccent]. Choosing one stores it and restyles the keyboard at once.
 * Without a stored choice, Gboard and Samsung show violet as selected and Own shows none (it keeps its theme color).
 */
@Composable
fun AccentDots(modifier: Modifier = Modifier, ringColor: Color = MaterialTheme.colorScheme.onSurface) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    // keyed on the stored values, so a preset switch (which decides what "no choice" shows) refreshes the dots
    var selected by remember(prefs.getString(Settings.PREF_KEYBOARD_PRESET, null), prefs.getString(Settings.PREF_KEYBOARD_ACCENT, null)) {
        mutableStateOf(
            KeyboardPresets.readAccent(prefs)
                ?: if (Settings.readKeyboardPreset(prefs) == KeyboardPreset.OWN) null else KeyboardAccent.VIOLET
        )
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        KeyboardAccent.entries.forEach { accent ->
            val isSelected = selected == accent
            val label = stringResource(accentLabel(accent))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(if (isSelected) 2.dp else 0.dp, if (isSelected) ringColor else Color.Transparent), CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color(accent.color))
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = {
                        selected = accent
                        KeyboardPresets.setAccent(ctx, accent)
                        (ctx.getActivity() as? SettingsActivity)?.prefChanged()
                        KeyboardSwitcher.getInstance().reloadKeyboard()
                    })
                    .semantics { contentDescription = label }
            )
        }
    }
}
