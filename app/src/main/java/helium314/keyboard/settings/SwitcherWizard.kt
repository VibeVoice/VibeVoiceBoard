// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.KeyboardPreset
import helium314.keyboard.latin.settings.SwitcherHelper
import helium314.keyboard.latin.utils.BackButton
import java.util.Locale

/**
 * The 3-question switcher flow that adapts keyboard presets and languages.
 * Reusable both inside the WelcomeWizard (onboarding) and from SettingsActivity.
 */
@Composable
fun SwitcherFlow(
    onFinished: () -> Unit,
    onSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // 0: Previous keyboard, 1: Primary language, 2: Secondary language
    var questionIndex by rememberSaveable { mutableIntStateOf(0) }

    var selectedPreset by rememberSaveable { mutableStateOf<KeyboardPreset?>(null) }
    var selectedMainLocaleTag by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSecondLocaleTag by rememberSaveable { mutableStateOf<String?>(null) }

    val systemLocale = remember { SwitcherHelper.getSystemSubtypeLocale(ctx) }
    val availableLocales = remember { SwitcherHelper.getSortedAvailableLocales(ctx) }

    BackHandler {
        if (questionIndex > 0) {
            questionIndex--
        } else {
            onBack()
        }
    }

    val completeSwitcher: (Locale?) -> Unit = { secondLoc ->
        selectedSecondLocaleTag = secondLoc?.toLanguageTag()
        val mainLocale = selectedMainLocaleTag?.let { Locale.forLanguageTag(it) }
        SwitcherHelper.applyAnswers(
            context = ctx,
            preset = selectedPreset,
            mainLocale = mainLocale,
            secondLocale = secondLoc
        )
        onFinished()
    }

    val cardShape = RoundedCornerShape(6.dp)
    val stepBackgroundColor = if (dark) Color(0x17FFFFFF) else Color(0x0F000000)
    val stepBorderColor = Brand.cardBorder(dark)
    val textColor = Brand.text(dark)
    val textColorDim = Brand.textDim(dark)

    @Composable
    fun QuestionProgress(current: Int) {
        val total = 3
        Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(
                stringResource(R.string.switcher_step_progress, current, total).uppercase(),
                fontFamily = BrandFont,
                fontSize = 12.sp,
                letterSpacing = 0.16.em,
                fontWeight = FontWeight.SemiBold,
                color = textColorDim
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..total).forEach { i ->
                    val target = when {
                        i < current -> Brand.accent(dark).copy(alpha = 0.5f)
                        i == current -> Brand.accent(dark)
                        else -> stepBorderColor
                    }
                    val color by animateColorAsState(target, tween(durationMillis = 350), label = "qStep$i")
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                }
            }
        }
    }

    @Composable
    fun QuestionHeader(stepNum: Int, title: String, instruction: String) {
        QuestionProgress(stepNum)
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.merge(color = textColor),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(instruction, style = MaterialTheme.typography.bodyLarge.merge(color = textColorDim))
        }
    }

    @Composable
    fun SwitcherOptionRow(
        title: String,
        subtitle: String? = null,
        badge: String? = null,
        icon: Int = R.drawable.ic_setup_select,
        highlight: Boolean = false,
        onClick: () -> Unit
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(
                    if (highlight) Brand.accent(dark).copy(alpha = if (dark) 0.22f else 0.14f)
                    else stepBackgroundColor
                )
                .clickable { onClick() }
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (highlight) Brand.accent(dark) else textColorDim
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge.merge(color = textColor),
                        fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Brand.accent(dark).copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = Brand.accent(dark),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall.merge(color = textColorDim)
                    )
                }
            }
        }
    }

    @Composable
    fun SkipAction(onSkip: () -> Unit) {
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clickable { onSkip() }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.switcher_skip),
                style = MaterialTheme.typography.bodyMedium.merge(color = textColorDim),
                textAlign = TextAlign.Center
            )
        }
    }

    when (questionIndex) {
        0 -> {
            // Question 1: Previous Keyboard
            Column(Modifier.fillMaxWidth()) {
                QuestionHeader(
                    stepNum = 1,
                    title = stringResource(R.string.switcher_q1_title),
                    instruction = stringResource(R.string.switcher_q1_instruction)
                )

                SwitcherOptionRow(
                    title = stringResource(R.string.switcher_keyboard_gboard),
                    icon = R.drawable.ic_ime_switcher,
                    onClick = {
                        selectedPreset = KeyboardPreset.GBOARD
                        questionIndex = 1
                    }
                )
                Spacer(Modifier.height(8.dp))
                SwitcherOptionRow(
                    title = stringResource(R.string.switcher_keyboard_samsung),
                    icon = R.drawable.ic_ime_switcher,
                    onClick = {
                        selectedPreset = KeyboardPreset.SAMSUNG
                        questionIndex = 1
                    }
                )
                Spacer(Modifier.height(8.dp))
                SwitcherOptionRow(
                    title = stringResource(R.string.switcher_keyboard_other),
                    subtitle = stringResource(R.string.switcher_keyboard_other_desc),
                    icon = R.drawable.ic_setup_check,
                    onClick = {
                        selectedPreset = KeyboardPreset.OWN
                        questionIndex = 1
                    }
                )

                SkipAction {
                    selectedPreset = null
                    questionIndex = 1
                }
            }
        }
        1 -> {
            // Question 2: Primary Language
            var searchQuery by rememberSaveable { mutableStateOf("") }
            val filteredLocales = remember(searchQuery, availableLocales) {
                if (searchQuery.isBlank()) availableLocales
                else availableLocales.filter { loc ->
                    val displayName = SwitcherHelper.getLocaleDisplayName(ctx, loc)
                    displayName.contains(searchQuery, ignoreCase = true)
                            || loc.toLanguageTag().contains(searchQuery, ignoreCase = true)
                }
            }

            Column(Modifier.fillMaxWidth()) {
                QuestionHeader(
                    stepNum = 2,
                    title = stringResource(R.string.switcher_q2_title),
                    instruction = stringResource(R.string.switcher_q2_instruction)
                )

                // Preselected System Language (prominent top row)
                val systemName = SwitcherHelper.getLocaleDisplayName(ctx, systemLocale)
                SwitcherOptionRow(
                    title = systemName,
                    badge = stringResource(R.string.switcher_system_language_badge),
                    icon = R.drawable.ic_setup_check,
                    highlight = true,
                    onClick = {
                        selectedMainLocaleTag = systemLocale.toLanguageTag()
                        questionIndex = 2
                    }
                )

                Spacer(Modifier.height(12.dp))

                // Search field for other languages
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.button_select_language)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Brand.accent(dark),
                        unfocusedBorderColor = stepBorderColor,
                        cursorColor = Brand.accent(dark),
                        focusedContainerColor = stepBackgroundColor,
                        unfocusedContainerColor = stepBackgroundColor
                    )
                )

                Spacer(Modifier.height(8.dp))

                // Scrollable container with available languages
                val listScrollState = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(listScrollState)
                ) {
                    filteredLocales.filter { it != systemLocale }.forEach { locale ->
                        SwitcherOptionRow(
                            title = SwitcherHelper.getLocaleDisplayName(ctx, locale),
                            icon = R.drawable.ic_setup_select,
                            onClick = {
                                selectedMainLocaleTag = locale.toLanguageTag()
                                questionIndex = 2
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                SkipAction {
                    // Skipping language selection leaves current subtypes unchanged and finishes switcher
                    selectedMainLocaleTag = null
                    SwitcherHelper.applyAnswers(
                        context = ctx,
                        preset = selectedPreset,
                        mainLocale = null,
                        secondLocale = null
                    )
                    onSkipped()
                }
            }
        }
        2 -> {
            // Question 3: Secondary Language (Multilingual typing)
            val mainLocale = selectedMainLocaleTag?.let { Locale.forLanguageTag(it) } ?: systemLocale
            var searchQuery by rememberSaveable { mutableStateOf("") }
            val otherLocales = remember(availableLocales, mainLocale) {
                availableLocales.filter { it != mainLocale }
            }
            val filteredLocales = remember(searchQuery, otherLocales) {
                if (searchQuery.isBlank()) otherLocales
                else otherLocales.filter { loc ->
                    val displayName = SwitcherHelper.getLocaleDisplayName(ctx, loc)
                    displayName.contains(searchQuery, ignoreCase = true)
                            || loc.toLanguageTag().contains(searchQuery, ignoreCase = true)
                }
            }

            Column(Modifier.fillMaxWidth()) {
                QuestionHeader(
                    stepNum = 3,
                    title = stringResource(R.string.switcher_q3_title),
                    instruction = stringResource(R.string.switcher_q3_instruction)
                )

                // Top option: No (default)
                SwitcherOptionRow(
                    title = stringResource(R.string.switcher_second_lang_no),
                    subtitle = stringResource(R.string.switcher_second_lang_no_desc),
                    icon = R.drawable.ic_setup_check,
                    highlight = true,
                    onClick = {
                        completeSwitcher(null)
                    }
                )

                Spacer(Modifier.height(12.dp))

                // Search field for second language
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.button_select_language)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Brand.accent(dark),
                        unfocusedBorderColor = stepBorderColor,
                        cursorColor = Brand.accent(dark),
                        focusedContainerColor = stepBackgroundColor,
                        unfocusedContainerColor = stepBackgroundColor
                    )
                )

                Spacer(Modifier.height(8.dp))

                // Scrollable container with languages to pick as secondary
                val listScrollState = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(listScrollState)
                ) {
                    filteredLocales.forEach { locale ->
                        SwitcherOptionRow(
                            title = SwitcherHelper.getLocaleDisplayName(ctx, locale),
                            icon = R.drawable.ic_setup_select,
                            onClick = {
                                completeSwitcher(locale)
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                SkipAction {
                    completeSwitcher(null)
                }
            }
        }
    }
}

/**
 * Standalone settings screen for running the switcher again from settings.
 */
@Composable
fun SwitcherScreen(
    onClickBack: () -> Unit
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val textColor = Brand.text(dark)
    val scrollState = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        BrandBackground(dark)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    Modifier
                        .fillMaxWidth()
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
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                SwitcherFlow(
                    onFinished = onClickBack,
                    onSkipped = onClickBack,
                    onBack = onClickBack
                )
            }
        }
    }
}
