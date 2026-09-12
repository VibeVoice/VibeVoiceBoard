// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import android.content.SharedPreferences
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings as KeySettings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.vibevoice.TrialResult
import helium314.keyboard.latin.vibevoice.VibeVoiceClient
import helium314.keyboard.latin.vibevoice.VoiceGlow
import helium314.keyboard.latin.vibevoice.VoiceOverlay
import helium314.keyboard.latin.vibevoice.VoiceWaveView
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.utils.previewDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class TryPhase { A, B, C }

@Composable
fun WelcomeWizard(
    close: () -> Unit,
    finish: () -> Unit
) {
    val ctx = LocalContext.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    var step by rememberSaveable { mutableIntStateOf(0) }

    // Derived IME states for Step 1
    var isImeEnabled by remember { mutableStateOf(UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm)) }
    var isImeCurrent by remember { mutableStateOf(UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)) }
    var previousImeCurrent by rememberSaveable { mutableStateOf(isImeCurrent) }

    fun updateImeState() {
        isImeEnabled = UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm)
        isImeCurrent = UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)
    }

    // Free trial minutes pre-fetch: requested at start so dictation is ready by Step 2
    var trialMinutes by rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(Unit) {
        if (VibeVoiceClient.isLinked(ctx)) { trialMinutes = 0; return@LaunchedEffect }
        trialMinutes = when (val res = VibeVoiceClient.requestTrialKey(ctx)) {
            is TrialResult.Granted -> res.minutesGranted.toInt()
            else -> 0
        }
    }

    // Ensure pulse preference is cleared when wizard leaves composition
    DisposableEffect(Unit) {
        onDispose {
            ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_KEY_PULSE, false).apply()
        }
    }

    val useWideLayout = isWideScreen()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val stepBackgroundColor = Brand.card(dark)
    val stepBorderColor = Brand.cardBorder(dark)
    val cardShape = RoundedCornerShape(Brand.corner.dp)
    val textColor = Brand.text(dark)
    val textColorDim = Brand.textFaint(dark)
    val titleColor = Brand.text(dark)
    val appName = stringResource(ctx.applicationInfo.labelRes)

    @Composable fun bigText() {
        // Nothing above the hero or closing screens
        if (step == 0 || step == 5) return
        Column(Modifier.padding(bottom = 20.dp)) {
            Text(
                stringResource(R.string.brand_wordmark).uppercase(),
                fontFamily = BrandFont,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = titleColor,
                modifier = Modifier.fillMaxWidth()
            )
            if (JniUtils.sHaveGestureLib)
                Text(
                    stringResource(R.string.setup_welcome_additional_description),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                    color = titleColor,
                    modifier = Modifier.fillMaxWidth()
                )
        }
    }

    @Composable fun StepNumbers(current: Int) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            (1..4).forEach {
                Text(
                    if (it < current) "\u2713" else "$it",
                    fontFamily = BrandFont,
                    color = when {
                        it == current -> Brand.accent
                        it < current -> Brand.accent.copy(alpha = 0.5f)
                        else -> textColorDim
                    }
                )
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    @Composable fun OnResume(block: () -> Unit) {
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) block()
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
    }

    OnResume {
        updateImeState()
    }

    LaunchedEffect(step) {
        if (step == 1) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (step == 1) {
                    updateImeState()
                    delay(200)
                }
            }
        }
    }

    LaunchedEffect(isImeCurrent) {
        if (step == 1 && isImeCurrent && !previousImeCurrent) {
            delay(400)
            step = 2
        }
        previousImeCurrent = isImeCurrent
    }

    @Composable fun StepHeader(current: Int, title: String, instruction: String) {
        StepNumbers(current)
        Column(Modifier
            .clip(cardShape)
            .background(color = stepBackgroundColor)
            .border(1.dp, stepBorderColor, cardShape)
            .padding(16.dp)
        ) {
            Text(title)
            Text(instruction, style = MaterialTheme.typography.bodyLarge.merge(color = Brand.textDim(dark)))
        }
    }

    @Composable fun ActionRow(icon: Int, text: String, active: Boolean, onClick: () -> Unit) {
        Row(
            Modifier.clip(cardShape)
                .clickable { onClick() }
                .background(color = stepBackgroundColor)
                .border(1.dp, stepBorderColor, cardShape)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(icon), null,
                Modifier.padding(end = 10.dp).size(28.dp),
                tint = if (active) Brand.accent else textColorDim
            )
            Text(text, Modifier.weight(1f))
        }
    }

    @Composable fun steps() {
        if (step == 0) {
            WizardHero(closing = false) { step = 1 }
        } else if (step == 5) {
            WizardHero(closing = true) {
                ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_KEY_PULSE, false).apply()
                finish()
            }
        } else {
            Column {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    updateImeState()
                }

                if (step == 1) {
                    StepHeader(
                        1,
                        stringResource(R.string.setup_step1_turn_on),
                        stringResource(R.string.setup_step1_turn_on_instruction, appName)
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .clip(cardShape)
                            .background(color = stepBackgroundColor)
                            .border(1.dp, stepBorderColor, cardShape)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(if (isImeEnabled) R.drawable.ic_setup_check else R.drawable.ic_setup_select),
                                null,
                                Modifier.padding(end = 10.dp).size(24.dp),
                                tint = if (isImeEnabled) Brand.accent else textColorDim
                            )
                            Text(
                                stringResource(R.string.setup_step1_tick_enable, appName),
                                style = MaterialTheme.typography.bodyLarge.merge(
                                    color = if (isImeEnabled) textColor else textColorDim
                                )
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(if (isImeCurrent) R.drawable.ic_setup_check else R.drawable.ic_setup_select),
                                null,
                                Modifier.padding(end = 10.dp).size(24.dp),
                                tint = if (isImeCurrent) Brand.accent else textColorDim
                            )
                            Text(
                                stringResource(R.string.setup_step1_tick_select),
                                style = MaterialTheme.typography.bodyLarge.merge(
                                    color = if (isImeCurrent) textColor else textColorDim
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        !isImeEnabled -> {
                            ActionRow(
                                R.drawable.ic_setup_key,
                                stringResource(R.string.setup_step1_action),
                                active = true
                            ) {
                                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                                    addCategory(Intent.CATEGORY_DEFAULT)
                                }
                                launcher.launch(intent)
                            }
                        }
                        !isImeCurrent -> {
                            ActionRow(
                                R.drawable.ic_setup_select,
                                stringResource(R.string.setup_step1_action_switch, appName),
                                active = true
                            ) {
                                imm.showInputMethodPicker()
                            }
                        }
                        else -> {
                            ActionRow(
                                R.drawable.ic_setup_check,
                                stringResource(R.string.setup_next_action),
                                active = true
                            ) {
                                step = 2
                            }
                        }
                    }
                } else if (step == 2) {
                    var phase by rememberSaveable { mutableStateOf(TryPhase.A) }
                    var practiceText by rememberSaveable { mutableStateOf("") }
                    var hasDictated by rememberSaveable { mutableStateOf(false) }
                    var micGranted by rememberSaveable {
                        mutableStateOf(ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED)
                    }

                    // Refresh microphone permission on resume from Android Settings or PermissionActivity
                    OnResume {
                        val granted = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        micGranted = granted
                        if (granted && phase == TryPhase.B) {
                            phase = TryPhase.C
                        }
                    }

                    // Reset PREF_HAS_DICTATED on entering Step 2, and listen for dictation events from LatinIME
                    DisposableEffect(step) {
                        if (step == 2) {
                            ctx.prefs().edit().putBoolean(KeySettings.PREF_HAS_DICTATED, false).apply()
                            val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                                if (key == KeySettings.PREF_HAS_DICTATED && prefs.getBoolean(KeySettings.PREF_HAS_DICTATED, false)) {
                                    hasDictated = true
                                }
                            }
                            ctx.prefs().registerOnSharedPreferenceChangeListener(listener)
                            onDispose {
                                ctx.prefs().unregisterOnSharedPreferenceChangeListener(listener)
                            }
                        } else {
                            onDispose {}
                        }
                    }

                    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        micGranted = granted
                        if (granted) {
                            phase = TryPhase.C
                        }
                    }
                    val focusRequester = remember { FocusRequester() }

                    LaunchedEffect(step) {
                        if (step == 2) {
                            delay(150)
                            try { focusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }

                    // Phase A: advances after user types >= 2 characters plus 1s pause
                    LaunchedEffect(practiceText) {
                        if (step == 2 && phase == TryPhase.A && practiceText.length >= 2) {
                            delay(1000)
                            if (phase == TryPhase.A) {
                                phase = if (micGranted) TryPhase.C else TryPhase.B
                            }
                        }
                    }

                    // Phase A timeout: advances after 6s if user types nothing (resets if typing starts)
                    LaunchedEffect(step, phase, practiceText.isEmpty()) {
                        if (step == 2 && phase == TryPhase.A && practiceText.isEmpty()) {
                            delay(6000)
                            if (phase == TryPhase.A && practiceText.isEmpty()) {
                                phase = if (micGranted) TryPhase.C else TryPhase.B
                            }
                        }
                    }

                    // Phase C pulse & timeout: pulses toolbar key, auto-clears after 15s
                    LaunchedEffect(phase) {
                        if (step == 2 && phase == TryPhase.C) {
                            ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_KEY_PULSE, true).apply()
                            delay(15000)
                            ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_KEY_PULSE, false).apply()
                        }
                    }

                    val (title, instruction) = when (phase) {
                        TryPhase.A -> Pair(
                            stringResource(R.string.setup_try_phase_a_title),
                            stringResource(R.string.setup_try_phase_a_instruction)
                        )
                        TryPhase.B -> Pair(
                            stringResource(R.string.setup_try_phase_b_title),
                            stringResource(R.string.setup_try_phase_b_instruction)
                        )
                        TryPhase.C -> Pair(
                            stringResource(R.string.setup_try_phase_c_title),
                            stringResource(R.string.setup_try_phase_c_instruction)
                        )
                    }
                    StepHeader(2, title, instruction)

                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .clip(cardShape)
                            .background(color = stepBackgroundColor)
                            .border(1.dp, stepBorderColor, cardShape)
                            .padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = practiceText,
                            onValueChange = { practiceText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.setup_try_hint)) },
                            minLines = 3
                        )
                    }

                    if (phase == TryPhase.B) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.setup_mic_disclosure),
                            style = MaterialTheme.typography.bodyMedium.merge(color = textColor)
                        )
                        Spacer(Modifier.height(8.dp))
                        ActionRow(
                            R.drawable.ic_setup_key,
                            stringResource(R.string.setup_mic_grant),
                            active = true
                        ) {
                            micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    if (hasDictated) {
                        ActionRow(
                            R.drawable.ic_setup_check,
                            stringResource(R.string.setup_next_action),
                            active = true
                        ) {
                            ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_KEY_PULSE, false).apply()
                            step = 3
                        }
                    } else {
                        ActionRow(
                            R.drawable.ic_setup_select,
                            stringResource(R.string.setup_try_skip),
                            active = false
                        ) {
                            ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_KEY_PULSE, false).apply()
                            step = 3
                        }
                    }
                } else if (step == 3) {
                    var linked by rememberSaveable { mutableStateOf(VibeVoiceClient.isLinked(ctx)) }
                    OnResume { linked = VibeVoiceClient.isLinked(ctx) }
                    StepHeader(
                        3,
                        stringResource(R.string.setup_minutes_title),
                        stringResource(R.string.setup_minutes_instruction)
                    )
                    Spacer(Modifier.height(8.dp))
                    if (linked) {
                        ActionRow(R.drawable.ic_setup_check, stringResource(R.string.setup_link_done), true) { }
                        Spacer(Modifier.height(8.dp))
                        ActionRow(R.drawable.ic_setup_select, stringResource(R.string.setup_next_action), true) {
                            step = 4
                        }
                    } else {
                        VibeVoiceLinkPanel(
                            modifier = Modifier
                                .clip(cardShape)
                                .background(color = stepBackgroundColor)
                                .border(1.dp, stepBorderColor, cardShape)
                                .padding(16.dp),
                            trigger = { enabled, loading, onClick ->
                                ActionRow(
                                    R.drawable.ic_vibevoice_active,
                                    stringResource(
                                        if (loading) R.string.vibevoice_polling_for_token
                                        else R.string.setup_link_action
                                    ),
                                    enabled
                                ) { if (enabled) onClick() }
                            }
                        ) { linked = true; step = 4 }
                        Spacer(Modifier.height(8.dp))
                        ActionRow(R.drawable.ic_setup_select, stringResource(R.string.setup_link_later_equal), false) {
                            step = 4
                        }
                    }
                } else if (step == 4) {
                    var overlay by rememberSaveable { mutableStateOf(VoiceOverlay.isAllowed(ctx)) }
                    OnResume {
                        val allowed = VoiceOverlay.isAllowed(ctx)
                        overlay = allowed
                        if (allowed) {
                            ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_BACKGROUND, true).apply()
                        }
                    }
                    var showGuardDialog by rememberSaveable { mutableStateOf(false) }
                    var guardDialogShown by rememberSaveable { mutableStateOf(false) }

                    StepHeader(
                        4,
                        stringResource(R.string.setup_floating_mark_title),
                        stringResource(R.string.setup_floating_mark_instruction)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .clip(cardShape)
                            .background(color = stepBackgroundColor)
                            .border(1.dp, stepBorderColor, cardShape)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painterResource(R.drawable.floating_mark_preview),
                            null,
                            Modifier.size(72.dp)
                        )
                        Text(
                            stringResource(R.string.setup_extras_overlay_preview),
                            style = MaterialTheme.typography.bodyMedium.merge(color = Brand.textDim(dark)),
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (overlay) {
                        ActionRow(
                            R.drawable.ic_setup_check,
                            stringResource(R.string.setup_next_action),
                            active = true
                        ) {
                            ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_BACKGROUND, true).apply()
                            step = 5
                        }
                    } else {
                        ActionRow(
                            R.drawable.ic_setup_select,
                            stringResource(R.string.setup_overlay_enable),
                            active = true
                        ) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + ctx.packageName)
                            )
                            try {
                                launcher.launch(intent)
                            } catch (_: android.content.ActivityNotFoundException) {
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!guardDialogShown) {
                                        guardDialogShown = true
                                        showGuardDialog = true
                                    } else {
                                        ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_BACKGROUND, false).apply()
                                        step = 5
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.setup_overlay_not_now),
                                style = MaterialTheme.typography.bodyMedium.merge(color = Brand.textDim(dark)),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (showGuardDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showGuardDialog = false
                                ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_BACKGROUND, false).apply()
                                step = 5
                            },
                            text = {
                                Text(
                                    stringResource(R.string.setup_overlay_guard_message),
                                    style = MaterialTheme.typography.bodyLarge.merge(color = textColor)
                                )
                            },
                            confirmButton = {
                                Text(
                                    stringResource(R.string.setup_overlay_guard_turn_on),
                                    modifier = Modifier
                                        .clickable {
                                            showGuardDialog = false
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                android.net.Uri.parse("package:" + ctx.packageName)
                                            )
                                            try {
                                                launcher.launch(intent)
                                            } catch (_: android.content.ActivityNotFoundException) {
                                            }
                                        }
                                        .padding(8.dp),
                                    color = Brand.accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            dismissButton = {
                                Text(
                                    stringResource(R.string.setup_overlay_guard_keep_off),
                                    modifier = Modifier
                                        .clickable {
                                            showGuardDialog = false
                                            ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_BACKGROUND, false).apply()
                                            step = 5
                                        }
                                        .padding(8.dp),
                                    color = Brand.textDim(dark)
                                )
                            },
                            containerColor = stepBackgroundColor,
                            shape = cardShape
                        )
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        BrandBackground(dark)
        // The waves on hero and closing screens
        if (step == 0 || step == 5) HeroWaves()
        Surface(color = Color.Transparent) {
        CompositionLocalProvider(
            LocalContentColor provides textColor,
            LocalTextStyle provides MaterialTheme.typography.titleLarge.merge(color = textColor),
        ) {
            // Scrollable, and padded for the keyboard.
            //
            // The practice field in step 4 asks to be brought into view when it takes focus --
            // BasicTextField does that by itself -- but the request needs a scrollable ancestor to
            // be honoured by, and there was none: a fillMaxSize Box with centred content cannot
            // move. So tapping the field put the keyboard over the field. `imePadding` is what
            // makes room for it (the activity is edge-to-edge, so the window does not resize on its
            // own), and the scroll is what lets the content use that room.
            //
            // fillMaxSize before verticalScroll is deliberate: the column takes the viewport's
            // height, so Arrangement.Center still centres content that fits, and only content that
            // does not fit scrolls. Step 6 was already close to overflowing on a short screen.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (useWideLayout)
                    Row {
                        Box(Modifier.weight(0.4f)) {
                            bigText()
                        }
                        Box(Modifier.weight(0.6f)) {
                            steps()
                        }
                    }
                else
                    Column {
                        bigText()
                        steps()
                    }
            }
        }
        }
    }
}

/**
 * The first page: the landing page's header, in the app.
 *
 * It used to be `setup_welcome_image` -- HeliBoard's illustration -- so the first thing anybody saw
 * after installing said nothing about VibeVoice. This is the site's hero, element for element: the
 * mark, the wordmark, the two slogan lines, the category line under them. Somebody who came from
 * vibevoice.net recognises the app; somebody who starts here recognises the site later.
 *
 * One action, like the site's own hero. A second button beside the first competes with it for the
 * same tap, and there is nothing else to do on this page.
 */
@Composable
fun WizardHero(
    closing: Boolean = false,
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    // Drawn through renderMark for the reason it exists: a vector's bounds are not its ink. The
    // launcher foreground carries the adaptive-icon safe area, so laying it out at 160dp puts a
    // mark of about a hundred on screen, off centre by whatever the artwork is off centre by.
    val logo = remember {
        val px = (ctx.resources.displayMetrics.density * HERO_LOGO_DP).toInt()
        ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_foreground)
            ?.let { VoiceGlow.renderMark(it, px) }
            ?.asImageBitmap()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (logo != null)
            Image(BitmapPainter(logo), null, Modifier.size(HERO_LOGO_DP.dp))
        else
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(HERO_LOGO_DP.dp))
        Spacer(Modifier.height(24.dp))
        // One Text, not three.
        //
        // On the site this is a single h1 with `tw-uppercase` on the element and `tw-font-thin` on
        // the last two spans, so all three lines share a size and a leading and differ only in
        // weight. Built as three composables at two Material sizes it was three headings stacked,
        // which is a different picture: the eye reads a title with a subtitle under it rather than
        // one block of type. The contrast is the whole composition, and it only works when the
        // size is constant.
        //
        // Uppercase on every line, including the slogan -- the site's `tw-uppercase` sits on the
        // h1 and reaches all of them, and sentence case on the last two was a misreading of it.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Shrink to fit rather than wrap.
            //
            // Derive required dp per sp dynamically from the longest line, weighted for weight:
            // Thin is ~0.51 dp per sp per character, SemiBold is ~0.65 dp per sp per character.
            // This prevents wrapping on narrow phones or large system font scales while keeping the
            // single-block 3-line typography.
            val scale = LocalDensity.current.fontScale
            val line1 = stringResource(
                if (closing) R.string.setup_done_slogan_line1 else R.string.brand_wordmark
            ).uppercase()
            val line2 = stringResource(
                if (closing) R.string.setup_done_slogan_line2 else R.string.brand_slogan_line1
            ).uppercase()
            val line3 = stringResource(
                if (closing) R.string.setup_done_slogan_line3 else R.string.brand_slogan_line2
            ).uppercase()
            val maxCostDpPerSp = maxOf(
                line1.length * 0.65f,
                line2.length * 0.51f,
                line3.length * 0.51f
            )
            val size = minOf(HERO_TYPE_SP.toFloat(), maxWidth.value / (maxCostDpPerSp * scale))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(line1)
                    }
                    append("\n")
                    withStyle(SpanStyle(fontWeight = FontWeight.Thin)) {
                        append(line2)
                        append("\n")
                        append(line3)
                    }
                },
                fontFamily = BrandFont,
                fontSize = size.sp,
                lineHeight = (size * HERO_LEADING).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!closing) {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.brand_subline),
                fontFamily = BrandFont,
                fontWeight = FontWeight.Normal,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.clickable { onClick() }.padding(top = 4.dp, start = 4.dp, end = 4.dp)) {
            Text(
                stringResource(if (closing) R.string.setup_finish_action else R.string.setup_start_action),
                fontFamily = BrandFont,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * The waves behind the hero, with nothing speaking into them.
 *
 * The same [VoiceWaveView] the keyboard runs during a session, in its demo mode. Not a second
 * implementation and not a video: it is the one piece of the landing page's hero that this app
 * already had, and it costs a view and a sine to reuse it.
 */
@Composable
private fun HeroWaves() {
    AndroidView(
        factory = { c -> VoiceWaveView(c).apply { startDemo(HERO_WAVE_COLOUR) } },
        modifier = Modifier.fillMaxSize(),
        // Leaving the page must stop the frame loop. VoiceWaveView stops itself on detach as well,
        // but relying on that alone is how an animation outlives the thing that started it.
        onRelease = { it.stop() }
    )
}

/** Big enough to be the page's subject rather than an icon above a heading. */
private const val HERO_LOGO_DP = 140

/**
 * One size for all three headline lines, and a leading tighter than the size.
 *
 * `leading-tight` on the site is 1.25; 1.1 here because these lines are all capitals, which have no
 * descenders to clear, and the site's own hero looks tighter than 1.25 for the same reason.
 *
 * 34sp is the cap, not the size: the block shrinks below it when the width demands. See the note
 * at the call site.
 */
private const val HERO_TYPE_SP = 34
private const val HERO_LEADING = 1.12f

/**
 * How much width, in dp, one sp of headline costs.
 *
 * Measured, not guessed: "START SPEAKING." set in Ubuntu Sans Thin is 258.4dp wide at 34sp, which
 * is 7.6 to one. Re-measure it if the slogan or the family changes.
 */
private const val HERO_DP_PER_SP = 7.6f

/**
 * The waves' colour on the hero, which is the brand's and not the keyboard theme's.
 *
 * `tailwind.config.cjs` primary-500. During a session the waves take ColorType.GESTURE_TRAIL so
 * they belong to whatever theme the user picked; here there is no keyboard on screen and no session,
 * and the page's whole job is to look like vibevoice.net.
 */
private const val HERO_WAVE_COLOUR = 0xFF8B5CF6.toInt()

@Preview
@Composable
private fun Preview() {
    Theme(previewDark) {
        Surface {
            WelcomeWizard({}) {  }
        }
    }
}

@Preview(
    // content cut off on real device, but not here... great?
    device = "spec:orientation=landscape,width=400dp,height=780dp"
)
@Composable
private fun WidePreview() {
    Theme(previewDark) {
        Surface {
            WelcomeWizard({}) {  }
        }
    }
}
