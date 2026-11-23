// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.LanguageOption
import helium314.keyboard.latin.settings.PreviousKeyboard
import helium314.keyboard.latin.settings.SwitcherHelper
import helium314.keyboard.latin.utils.BackButton
import helium314.keyboard.latin.utils.getActivity
import java.util.Locale

/**
 * The language step: "Coming from another keyboard?" and the typing languages, in one screen.
 *
 * Every answer has a default, so the step can be passed with a single tap: no previous keyboard
 * selected, the phone's language as main language, no second language. Nothing is applied here;
 * [onContinue] hands over the main and second locale and the caller applies them, once, so that
 * browsing the list does not enable every language that was tapped on the way.
 *
 * The layout fills its parent: header and question at the top, the list taking the remaining
 * height, the button pinned at the bottom. While the keyboard is open for the search field the top
 * part folds away, so the list keeps room. The caller supplies the insets.
 *
 * @param previous the previous-keyboard answer, or null when nothing is selected
 * @param mainTag language tag of the chosen main language, null for the phone's language
 * @param secondTag language tag of the chosen second language, null for none
 * @param header what to show above the question, e.g. the wizard's progress and title
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageStep(
    previous: PreviousKeyboard?,
    onPrevious: (PreviousKeyboard) -> Unit,
    mainTag: String?,
    secondTag: String?,
    onMain: (String) -> Unit,
    onSecond: (String?) -> Unit,
    onContinue: (main: Locale, second: Locale?) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {}
) {
    val ctx = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = Brand.accent(dark)
    val textColor = Brand.text(dark)
    val textColorDim = Brand.textDim(dark)
    val fill = if (dark) Color(0x17FFFFFF) else Color(0x0F000000)
    val border = Brand.cardBorder(dark)
    val shape = RoundedCornerShape(6.dp)

    val options = remember { SwitcherHelper.getLanguageOptions(ctx) }
    val defaultMain = remember(options) { SwitcherHelper.getDefaultMainOption(ctx, options) }
    val mainOption = mainTag?.let { SwitcherHelper.findLanguageOption(options, Locale.forLanguageTag(it)) } ?: defaultMain
    val secondOption = secondTag
        ?.let { SwitcherHelper.findLanguageOption(options, Locale.forLanguageTag(it)) }
        ?.takeIf { it != mainOption }

    // 0 = the list picks the main language, 1 = it picks the second one
    var slot by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    val rows = remember(options, query, slot, mainOption) {
        options.filter { it.matches(query) && (slot == 0 || it != mainOption) }
    }
    val showNone = slot == 1 && query.isBlank()
    val selected = if (slot == 0) mainOption else secondOption
    val listState = rememberLazyListState()
    LaunchedEffect(slot) {
        val index = rows.indexOf(selected)
        if (index > 0) listState.scrollToItem(index + if (showNone) 1 else 0) else listState.scrollToItem(0)
    }

    @Composable
    fun Choice(label: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        Row(
            modifier
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(if (isSelected) accent.copy(alpha = if (dark) 0.20f else 0.14f) else fill)
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                modifier = Modifier.padding(start = 10.dp, end = 6.dp),
                colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = textColorDim)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.merge(color = textColor),
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    fun SlotChip(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
        Box(
            modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (active) accent.copy(alpha = if (dark) 0.20f else 0.14f) else fill)
                .border(
                    BorderStroke(if (active) 1.5.dp else 1.dp, if (active) accent else border),
                    RoundedCornerShape(20.dp)
                )
                .selectable(selected = active, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.merge(color = textColor),
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        AnimatedVisibility(visible = !WindowInsets.isImeVisible) {
            Column(Modifier.fillMaxWidth()) {
                header()
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.setup_prev_question),
                    style = MaterialTheme.typography.titleSmall.merge(color = textColor),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                val answers = listOf(
                    PreviousKeyboard.GBOARD to stringResource(R.string.setup_keyboard_gboard),
                    PreviousKeyboard.SAMSUNG to stringResource(R.string.setup_keyboard_samsung),
                    PreviousKeyboard.OTHER to stringResource(R.string.setup_prev_other),
                    PreviousKeyboard.FIRST to stringResource(R.string.setup_prev_first)
                )
                answers.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { (answer, label) ->
                            Choice(label, previous == answer, Modifier.weight(1f)) { onPrevious(answer) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SlotChip(
                stringResource(R.string.setup_lang_chip_main, mainOption?.label.orEmpty()),
                active = slot == 0,
                modifier = Modifier.weight(1f)
            ) { slot = 0; query = "" }
            SlotChip(
                stringResource(R.string.setup_lang_chip_second, secondOption?.label ?: stringResource(R.string.setup_lang_none)),
                active = slot == 1,
                modifier = Modifier.weight(1f)
            ) { slot = 1; query = "" }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.setup_lang_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.merge(color = textColor),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = border,
                cursorColor = accent,
                focusedContainerColor = fill,
                unfocusedContainerColor = fill
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (slot == 0) R.string.setup_lang_pick_main else R.string.setup_lang_pick_second),
            style = MaterialTheme.typography.labelMedium.merge(color = textColorDim)
        )
        Spacer(Modifier.height(4.dp))

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            if (showNone) {
                item(key = "none") {
                    Choice(
                        stringResource(R.string.setup_lang_none),
                        secondOption == null,
                        Modifier.fillMaxWidth()
                    ) { onSecond(null) }
                }
            }
            items(rows, key = { it.locale.toLanguageTag() }) { option ->
                Choice(option.label, option == selected, Modifier.fillMaxWidth()) {
                    if (slot == 0) {
                        onMain(option.locale.toLanguageTag())
                        if (secondOption == option) onSecond(null)
                    } else {
                        onSecond(option.locale.toLanguageTag())
                    }
                }
            }
            if (rows.isEmpty() && !showNone) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.setup_lang_no_results),
                        style = MaterialTheme.typography.bodyMedium.merge(color = textColorDim),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { mainOption?.let { onContinue(it.locale, secondOption?.locale) } },
            enabled = mainOption != null,
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Text(
                stringResource(R.string.setup_next_action),
                fontFamily = BrandFont,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * Settings > "Switching from another keyboard?": the wizard's language step on its own.
 *
 * Same step, same logic. Unlike in the wizard nothing is pre-selected for the previous keyboard,
 * and the keyboard style is only changed when an answer is given, so opening the screen and
 * pressing Next leaves a hand-tuned layout alone.
 */
@Composable
fun SwitcherScreen(
    onClickBack: () -> Unit
) {
    val ctx = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val textColor = Brand.text(dark)

    var previousName by rememberSaveable { mutableStateOf<String?>(null) }
    var mainTag by rememberSaveable { mutableStateOf<String?>(null) }
    var secondTag by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        BrandBackground(dark)
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(onClick = onClickBack)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.switcher_settings_title),
                        style = MaterialTheme.typography.titleMedium.merge(color = textColor),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        ) { innerPadding ->
            LanguageStep(
                previous = PreviousKeyboard.fromName(previousName),
                onPrevious = { previousName = it.name },
                mainTag = mainTag,
                secondTag = secondTag,
                onMain = { mainTag = it },
                onSecond = { secondTag = it },
                onContinue = { main, second ->
                    SwitcherHelper.applyAnswers(
                        context = ctx,
                        preset = PreviousKeyboard.fromName(previousName)?.let { SwitcherHelper.presetFor(it) },
                        mainLocale = main,
                        secondLocale = second
                    )
                    (ctx.getActivity() as? SettingsActivity)?.prefChanged()
                    onClickBack()
                },
                modifier = Modifier
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}
