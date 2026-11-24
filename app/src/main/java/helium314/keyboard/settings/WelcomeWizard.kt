// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.em
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
import androidx.compose.ui.text.style.TextOverflow
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.KeyboardPreset
import helium314.keyboard.latin.settings.KeyboardPresets
import helium314.keyboard.settings.preferences.AccentDots
import helium314.keyboard.latin.settings.PreviousKeyboard
import helium314.keyboard.latin.settings.SwitcherHelper
import helium314.keyboard.latin.settings.Settings as KeySettings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.vibevoice.TrialResult
import helium314.keyboard.latin.vibevoice.VibeVoiceClient
import helium314.keyboard.latin.vibevoice.VibeVoiceDebugLogger
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

private enum class TrialState {
    IDLE,
    LOADING,
    GRANTED,
    FAILED
}

// The wizard's screens, in order. StepNumbers counts the ones between the hero and the closing
// screen, so inserting a step means renumbering the headers below, not just adding a constant.
private const val STEP_HERO = 0
private const val STEP_TURN_ON = 1
private const val STEP_LANGUAGE = 2
private const val STEP_PRACTICE = 3
private const val STEP_LINK = 4
private const val STEP_FLOATING_MARK = 5
private const val STEP_STYLE = 6
private const val STEP_DONE = 7

/** Headers are numbered 1..this; the hero and the closing screen carry no number. */
private const val NUMBERED_STEPS = 6

/** The steps that fill the screen and pin their button, rather than scroll as one column. */
private fun isPinnedStep(step: Int) = step == STEP_LANGUAGE || step == STEP_STYLE

@Composable
fun WelcomeWizard(
    close: () -> Unit,
    finish: () -> Unit
) {
    val ctx = LocalContext.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    var step by rememberSaveable { mutableIntStateOf(STEP_HERO) }

    // Answers that outlive their step. The previous-keyboard answer from the language step
    // pre-selects the style on the last one; the style, once chosen or pre-selected, is remembered
    // so that stepping back and forth does not apply it again.
    var previousName by rememberSaveable { mutableStateOf(PreviousKeyboard.DEFAULT.name) }
    var mainTag by rememberSaveable { mutableStateOf<String?>(null) }
    var secondTag by rememberSaveable { mutableStateOf<String?>(null) }
    var stylePresetName by rememberSaveable { mutableStateOf<String?>(null) }

    // Derived IME states for Step 1
    var isImeEnabled by remember { mutableStateOf(UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm)) }
    var isImeCurrent by remember { mutableStateOf(UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)) }
    var previousImeCurrent by rememberSaveable { mutableStateOf(isImeCurrent) }

    fun updateImeState() {
        isImeEnabled = UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm)
        isImeCurrent = UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)
    }

    var trialState by remember {
        mutableStateOf(
            if (VibeVoiceClient.getApiKey(ctx) != null || VibeVoiceClient.isTrialSpent(ctx) || VibeVoiceClient.isLinked(ctx))
                TrialState.GRANTED
            else
                TrialState.IDLE
        )
    }
    val wizardScope = rememberCoroutineScope()

    fun performTrialRequest(withRetry: Boolean) {
        if (VibeVoiceClient.getApiKey(ctx) != null || VibeVoiceClient.isTrialSpent(ctx) || VibeVoiceClient.isLinked(ctx)) {
            trialState = TrialState.GRANTED
            return
        }
        if (trialState == TrialState.LOADING) return
        trialState = TrialState.LOADING
        wizardScope.launch {
            val result = if (withRetry) {
                VibeVoiceClient.requestTrialKeyWithRetry(ctx)
            } else {
                VibeVoiceClient.requestTrialKey(ctx)
            }
            val granted = (result is TrialResult.Granted) || (VibeVoiceClient.getApiKey(ctx) != null)
            trialState = if (granted) TrialState.GRANTED else TrialState.FAILED
            if (!granted) {
                VibeVoiceDebugLogger.log("Wizard: trial key failed")
            }
        }
    }

    LaunchedEffect(step) {
        if (trialState == TrialState.GRANTED) return@LaunchedEffect
        if (step == STEP_HERO && trialState == TrialState.IDLE) {
            performTrialRequest(withRetry = true)
        } else if (step == STEP_PRACTICE && trialState != TrialState.LOADING) {
            if (VibeVoiceClient.getApiKey(ctx) != null || VibeVoiceClient.isTrialSpent(ctx) || VibeVoiceClient.isLinked(ctx)) {
                trialState = TrialState.GRANTED
            } else if (trialState == TrialState.IDLE || trialState == TrialState.FAILED) {
                performTrialRequest(withRetry = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        VibeVoiceClient.trialRetryFlow.collect {
            performTrialRequest(withRetry = false)
        }
    }

    // Ensure pulse preference and wizard active state are maintained with composition.
    // Finding (a): Stop writing persisted PREF_SETUP_WIZARD_ACTIVE; use in-memory flag only.
    DisposableEffect(Unit) {
        VibeVoiceClient.isWizardActive = true
        onDispose {
            VibeVoiceClient.isWizardActive = false
            ctx.prefs().edit()
                .putBoolean(KeySettings.PREF_VOICE_KEY_PULSE, false)
                .apply()
        }
    }

    BackHandler {
        if (step > 0) {
            ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_KEY_PULSE, false).apply()
            step--
        } else if (isImeEnabled && isImeCurrent) {
            close()
        } else {
            finish()
        }
    }

    val useWideLayout = isWideScreen()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // Flat surfaces, no hairline border, a small radius. Every element used to be its own outlined
    // card with a 14dp radius, so the header, the explanation and each action all weighed the same
    // and the screen read as a stack of boxes. Now the explanation sits on the ground as text, the
    // actions are flat rows, and only the primary one carries colour.
    val stepBackgroundColor = if (dark) Color(0x17FFFFFF) else Color(0x0F000000)
    val stepBorderColor = Brand.cardBorder(dark)
    val cardShape = RoundedCornerShape(6.dp)
    val textColor = Brand.text(dark)
    val textColorDim = Brand.textFaint(dark)
    val titleColor = Brand.text(dark)
    val appName = stringResource(ctx.applicationInfo.labelRes)

    @Composable fun bigText() {
        // Nothing above the hero or closing screens
        if (step == STEP_HERO || step == STEP_DONE || isPinnedStep(step)) return
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.brand_wordmark).uppercase(),
                fontFamily = BrandFont,
                fontSize = 36.sp,
                letterSpacing = 0.08.em,
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

    // A segmented progress bar rather than a row of digits. The digits read as a footnote under a
    // 36sp wordmark; four segments across the full width say "how far along" at a glance, and the
    // caption above gives the exact count. Done segments are a muted accent, the current one full
    // accent, the rest a faint track, and the colour eases across when the step changes instead of
    // snapping.
    @Composable fun StepNumbers(current: Int) {
        val total = NUMBERED_STEPS
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(
                stringResource(R.string.setup_step_progress, current, total).uppercase(),
                fontFamily = BrandFont,
                fontSize = 12.sp,
                letterSpacing = 0.16.em,
                fontWeight = FontWeight.SemiBold,
                color = Brand.textDim(dark)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..total).forEach { i ->
                    val target = when {
                        i < current -> Brand.accent(dark).copy(alpha = 0.5f)
                        i == current -> Brand.accent(dark)
                        else -> stepBorderColor
                    }
                    val color by animateColorAsState(target, tween(durationMillis = 350), label = "step$i")
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
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
        if (step == STEP_TURN_ON) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (step == STEP_TURN_ON) {
                    updateImeState()
                    delay(200)
                }
            }
        }
    }

    LaunchedEffect(isImeCurrent) {
        if (step == STEP_TURN_ON && isImeCurrent && !previousImeCurrent) {
            delay(400)
            if (step == STEP_TURN_ON) step = STEP_LANGUAGE
        }
        previousImeCurrent = isImeCurrent
    }

    @Composable fun StepHeader(current: Int, title: String, instruction: String) {
        StepNumbers(current)
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.merge(color = textColor),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(instruction, style = MaterialTheme.typography.bodyLarge.merge(color = Brand.textDim(dark)))
        }
    }

    // `brand` shows the mark on its own dark tile -- white mark on a black ground -- so it reads the
    // same on dark and light without tinting or flattening.
    //
    // The mark that answers a finished link. It pops in with a spring and, when the link happened just
    // now, plays a short chime -- unless the phone is on silent or vibrate, where a sound would be rude.
    @Composable fun LinkedMark(celebrate: Boolean, onCelebrated: () -> Unit) {
        val scale = remember { Animatable(if (celebrate) 0.4f else 1f) }
        LaunchedEffect(Unit) {
            if (!celebrate) return@LaunchedEffect
            onCelebrated()
            val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                MediaPlayer.create(ctx, R.raw.linked_chime)?.apply {
                    setOnCompletionListener { it.release() }
                    setOnErrorListener { mp, _, _ -> mp.release(); true }
                    start()
                }
            }
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_notification), null,
                Modifier.size(96.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                tint = Brand.accent(dark)
            )
        }
    }

    // `primary` decides the fill and defaults to `active`. They are separate because a status row such
    // as "Account linked" is active (accent tick) without being the thing to press; giving it the
    // accent fill too put two primary buttons on one screen.
    @Composable fun ActionRow(
        icon: Int,
        text: String,
        active: Boolean,
        brand: Boolean = false,
        primary: Boolean = active,
        onClick: () -> Unit
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(if (primary) Brand.accent(dark).copy(alpha = if (dark) 0.20f else 0.14f) else stepBackgroundColor)
                .clickable { onClick() }
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (brand) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
                }
            } else {
                Icon(
                    painterResource(icon), null,
                    Modifier.size(20.dp),
                    tint = if (active) Brand.accent(dark) else textColorDim
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.merge(color = textColor),
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }

    @Composable fun steps() {
        if (step == STEP_HERO) {
            WizardHero(closing = false) { step = STEP_TURN_ON }
        } else if (step == STEP_DONE) {
            WizardHero(closing = true) {
                ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_KEY_PULSE, false).apply()
                finish()
            }
        } else {
            Column(if (isPinnedStep(step)) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    updateImeState()
                }

                if (step == STEP_TURN_ON) {
                    StepHeader(
                        1,
                        stringResource(R.string.setup_step1_turn_on),
                        stringResource(R.string.setup_step1_turn_on_instruction, appName)
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(color = stepBackgroundColor)
                            .padding(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(if (isImeEnabled) R.drawable.ic_setup_check else R.drawable.ic_setup_select),
                                null,
                                Modifier.padding(end = 10.dp).size(24.dp),
                                tint = if (isImeEnabled) Brand.accent(dark) else textColorDim
                            )
                            Text(
                                stringResource(R.string.setup_step1_tick_enable, appName),
                                style = MaterialTheme.typography.bodyLarge.merge(
                                    color = if (isImeEnabled) textColor else textColorDim
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(if (isImeCurrent) R.drawable.ic_setup_check else R.drawable.ic_setup_select),
                                null,
                                Modifier.padding(end = 10.dp).size(24.dp),
                                tint = if (isImeCurrent) Brand.accent(dark) else textColorDim
                            )
                            Text(
                                stringResource(R.string.setup_step1_tick_select),
                                style = MaterialTheme.typography.bodyLarge.merge(
                                    color = if (isImeCurrent) textColor else textColorDim
                                ),
                                modifier = Modifier.weight(1f)
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
                                step = STEP_LANGUAGE
                            }
                        }
                    }
                } else if (step == STEP_LANGUAGE) {
                    LanguageStep(
                        previous = PreviousKeyboard.fromName(previousName),
                        onPrevious = { answer ->
                            // The style on the last step follows this answer; a changed answer
                            // means it has to be worked out again.
                            if (answer.name != previousName) stylePresetName = null
                            previousName = answer.name
                        },
                        mainTag = mainTag,
                        secondTag = secondTag,
                        onMain = { mainTag = it },
                        onSecond = { secondTag = it },
                        onContinue = { main, second ->
                            // main is null when nothing was touched: leave the languages alone
                            SwitcherHelper.applyAnswers(ctx, preset = null, mainLocale = main, secondLocale = second)
                            (ctx.getActivity() as? SettingsActivity)?.prefChanged()
                            step = STEP_PRACTICE
                        },
                        header = {
                            StepHeader(
                                2,
                                stringResource(R.string.setup_language_title),
                                stringResource(R.string.setup_language_instruction)
                            )
                        }
                    )
                } else if (step == STEP_PRACTICE) {
                    var phase by rememberSaveable { mutableStateOf(TryPhase.A) }
                    var practiceText by rememberSaveable { mutableStateOf("") }
                    var hasDictated by rememberSaveable { mutableStateOf(false) }
                    var micGranted by rememberSaveable {
                        mutableStateOf(ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED)
                    }
                    // Refresh microphone permission and trial key status on resume
                    OnResume {
                        if (VibeVoiceClient.getApiKey(ctx) != null || VibeVoiceClient.isTrialSpent(ctx) || VibeVoiceClient.isLinked(ctx)) {
                            trialState = TrialState.GRANTED
                        }
                        val granted = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        micGranted = granted
                        if (granted && phase == TryPhase.B) {
                            phase = TryPhase.C
                        }
                    }

                    // Reset PREF_HAS_DICTATED on entering Step 2, and listen for dictation events from LatinIME
                    DisposableEffect(step) {
                        if (step == STEP_PRACTICE) {
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
                        } else {
                            // Answered instantly by the system once denied for good: the button would
                            // do nothing. App info is the only place left to allow it.
                            val activity = ctx.getActivity()
                            if (activity != null && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                                    activity, android.Manifest.permission.RECORD_AUDIO)) {
                                android.widget.Toast.makeText(ctx, R.string.vibevoice_mic_blocked, android.widget.Toast.LENGTH_LONG).show()
                                try {
                                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.fromParts("package", ctx.packageName, null)))
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                    val focusRequester = remember { FocusRequester() }

                    LaunchedEffect(step) {
                        if (step == STEP_PRACTICE) {
                            delay(150)
                            try { focusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }

                    // Phase A: advances after user types >= 2 characters plus 1s pause
                    LaunchedEffect(practiceText) {
                        if (step == STEP_PRACTICE && phase == TryPhase.A && practiceText.length >= 2) {
                            delay(1000)
                            if (phase == TryPhase.A) {
                                phase = if (micGranted) TryPhase.C else TryPhase.B
                            }
                        }
                    }

                    // Phase A timeout: advances after 6s unless the user is typing enough to advance on
                    // their own. Keyed on fewer than two characters, not on empty: a single stray
                    // letter cancelled this timeout and never reached the typing rule either.
                    LaunchedEffect(step, phase, practiceText.length < 2) {
                        if (step == STEP_PRACTICE && phase == TryPhase.A && practiceText.length < 2) {
                            delay(6000)
                            if (phase == TryPhase.A && practiceText.length < 2) {
                                phase = if (micGranted) TryPhase.C else TryPhase.B
                            }
                        }
                    }

                    // Phase C pulse & timeout: pulses toolbar key, auto-clears after 15s
                    LaunchedEffect(phase) {
                        if (step == STEP_PRACTICE && phase == TryPhase.C) {
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
                    StepHeader(3, title, instruction)

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = practiceText,
                        onValueChange = { practiceText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.setup_try_hint)) },
                        minLines = 3,
                        shape = cardShape,
                        textStyle = MaterialTheme.typography.bodyLarge.merge(color = textColor),
                        // Brand colours, not the Material defaults, which resolve to the wallpaper accent.
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Brand.accent(dark),
                            unfocusedBorderColor = stepBorderColor,
                            cursorColor = Brand.accent(dark),
                            focusedContainerColor = stepBackgroundColor,
                            unfocusedContainerColor = stepBackgroundColor
                        )
                    )

                    if (trialState != TrialState.GRANTED && !VibeVoiceClient.isLinked(ctx) && !VibeVoiceClient.isTrialSpent(ctx)) {
                        val isConnecting = trialState == TrialState.LOADING || trialState == TrialState.IDLE
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(color = stepBackgroundColor)
                                .clickable(enabled = !isConnecting) { performTrialRequest(withRetry = true) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Brand.accent(dark),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.setup_try_trial_connecting),
                                    style = MaterialTheme.typography.bodyMedium.merge(color = textColor),
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Icon(
                                    painterResource(R.drawable.ic_redo),
                                    null,
                                    Modifier.size(20.dp),
                                    tint = Brand.accent(dark)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.setup_try_trial_retry),
                                    style = MaterialTheme.typography.bodyMedium.merge(color = textColor),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (phase == TryPhase.B) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.setup_mic_disclosure),
                            style = MaterialTheme.typography.bodyMedium.merge(color = textColor),
                            modifier = Modifier.fillMaxWidth()
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
                            step = STEP_LINK
                        }
                    } else {
                        ActionRow(
                            R.drawable.ic_setup_select,
                            stringResource(R.string.setup_try_skip),
                            active = false
                        ) {
                            ctx.prefs().edit().putBoolean(KeySettings.PREF_VOICE_KEY_PULSE, false).apply()
                            step = STEP_LINK
                        }
                    }
                } else if (step == STEP_LINK) {
                    var linked by rememberSaveable { mutableStateOf(VibeVoiceClient.isLinked(ctx)) }
                    // Only a link made on this screen earns the chime; arriving already linked does not.
                    val linkedOnArrival = rememberSaveable { linked }
                    // Saved so turning the phone after the chime does not ring it a second time.
                    var celebrated by rememberSaveable { mutableStateOf(false) }
                    OnResume { linked = VibeVoiceClient.isLinked(ctx) }
                    StepHeader(
                        4,
                        stringResource(R.string.setup_minutes_title),
                        stringResource(R.string.setup_minutes_instruction)
                    )
                    Spacer(Modifier.height(8.dp))
                    if (linked) {
                        LinkedMark(celebrate = !linkedOnArrival && !celebrated) { celebrated = true }
                        ActionRow(R.drawable.ic_setup_check, stringResource(R.string.setup_link_done), true, primary = false) { }
                        Spacer(Modifier.height(8.dp))
                        ActionRow(R.drawable.ic_setup_select, stringResource(R.string.setup_next_action), true) {
                            step = STEP_FLOATING_MARK
                        }
                    } else {
                        ActionRow(
                            R.drawable.ic_setup_check,
                            stringResource(R.string.setup_next_action),
                            active = true,
                            primary = true
                        ) {
                            step = STEP_FLOATING_MARK
                        }
                        Spacer(Modifier.height(8.dp))
                        val linkCardModifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(color = stepBackgroundColor)
                            .padding(14.dp)
                        VibeVoiceLinkPanel(
                            modifier = Modifier.fillMaxWidth(),
                            cardModifier = linkCardModifier,
                            codeColor = Brand.accent(dark),
                            textColor = textColor,
                            textDimColor = Brand.textDim(dark),
                            progressColor = Brand.accent(dark),
                            trigger = { enabled, loading, onClick ->
                                ActionRow(
                                    R.drawable.ic_vibevoice_mark,
                                    stringResource(
                                        if (loading) R.string.vibevoice_polling_for_token
                                        else R.string.setup_link_action
                                    ),
                                    enabled,
                                    brand = true,
                                    primary = false
                                ) { if (enabled) onClick() }
                            }
                        ) { linked = true }
                    }
                } else if (step == STEP_FLOATING_MARK) {
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
                        5,
                        stringResource(R.string.setup_floating_mark_title),
                        stringResource(R.string.setup_floating_mark_instruction)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(color = stepBackgroundColor)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The real mark, drawn live by VoiceOverlay in the keyboard's own colours. It
                        // used to be a capture lifted off a black screen: right for a dark keyboard,
                        // a ghost of pale light on a light one, and never the colours a user with
                        // another theme would actually get.
                        FloatingMarkPreview(Modifier.size(56.dp))
                        Text(
                            stringResource(R.string.setup_extras_overlay_preview),
                            style = MaterialTheme.typography.bodyMedium.merge(color = Brand.textDim(dark)),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
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
                            step = STEP_STYLE
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
                                        step = STEP_STYLE
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
                            // Back or a tap outside is not "skip": it returns to the step, and the
                            // buttons stay the only way to decide.
                            onDismissRequest = { showGuardDialog = false },
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
                                    color = Brand.accent(dark),
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
                                            step = STEP_STYLE
                                        }
                                        .padding(8.dp),
                                    color = Brand.textDim(dark)
                                )
                            },
                            containerColor = stepBackgroundColor,
                            shape = cardShape
                        )
                    }
                } else if (step == STEP_STYLE) {
                    StyleStep(
                        header = {
                            StepHeader(
                                6,
                                stringResource(R.string.setup_style_title),
                                stringResource(R.string.setup_style_instruction)
                            )
                        },
                        previous = PreviousKeyboard.fromName(previousName),
                        presetName = stylePresetName,
                        onPresetApplied = { stylePresetName = it.name },
                        onNext = { step = STEP_DONE }
                    )
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        BrandBackground(dark)
        // The waves on hero and closing screens
        if (step == STEP_HERO || step == STEP_DONE) HeroWaves(dark)
        Surface(color = Color.Transparent) {
        CompositionLocalProvider(
            LocalContentColor provides textColor,
            LocalTextStyle provides MaterialTheme.typography.bodyLarge.merge(color = textColor),
        ) {
            // Scrollable, and padded for the keyboard.
            //
            // The practice field asks to be brought into view when it takes focus --
            // BasicTextField does that by itself -- but the request needs a scrollable ancestor to
            // be honoured by, and there was none: a fillMaxSize Box with centred content cannot
            // move. So tapping the field put the keyboard over the field. `imePadding` is what
            // makes room for it (the activity is edge-to-edge, so the window does not resize on its
            // own), and the scroll is what lets the content use that room.
            //
            // fillMaxSize before verticalScroll is deliberate: the column takes the viewport's
            // height, so Arrangement.Center still centres content that fits, and only content that
            // does not fit scrolls. The closing screen was already close to overflowing on a short
            // screen. A step starts at its top: the practice field scrolls the column down with
            // the keyboard open, and without the reset the next step opened with its header scrolled
            // away.
            //
            // The language and style steps are not scrolled as a whole: they pin their button to the
            // bottom, and the language list takes the height that is left. They scroll inside
            // themselves where they need to.
            //
            // The system bars are padded for explicitly. The activity is edge-to-edge, and a fixed
            // top padding that cleared the status bar on one phone sat under it on another.
            val scrollState = rememberScrollState()
            val pinned = isPinnedStep(step)
            val heroLike = step == STEP_HERO || step == STEP_DONE
            LaunchedEffect(step) { scrollState.scrollTo(0) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .then(if (pinned) Modifier else Modifier.verticalScroll(scrollState))
                    // Numbered steps start at the same height every time. Centred, each step sat
                    // wherever its own height put it, so the wordmark and the step numbers jumped up
                    // and down between steps. Anchored to the top, only the content below them
                    // changes. The hero and the closing screen are single compositions and stay
                    // centred.
                    .padding(horizontal = 28.dp)
                    .padding(top = if (heroLike) 32.dp else if (pinned) 12.dp else 24.dp, bottom = if (pinned) 12.dp else 32.dp),
                verticalArrangement = if (heroLike) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Not for the hero and the closing screen: bigText has nothing for them, and the split
                // left a blank 40 % column beside a squeezed screen.
                if (useWideLayout && !heroLike)
                    Row(if (pinned) Modifier.fillMaxSize() else Modifier) {
                        Box(Modifier.weight(0.4f)) {
                            bigText()
                        }
                        Box(Modifier.weight(0.6f)) {
                            steps()
                        }
                    }
                else
                    Column(if (pinned) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                        bigText()
                        steps()
                    }
            }
        }
        }
    }
}

/**
 * Step 6: Style and feel.
 *
 * Shows the real keyboard with a live practice text field and compact controls above it.
 * The style is pre-selected from the previous-keyboard answer in step 3 (Gboard -> GBOARD, Samsung -> SAMSUNG,
 * else OWN). Vibration toggle defaults to off.
 *
 * Finding (c): Choosing OTHER or FIRST in previous-keyboard does NOT call KeyboardPresets.apply(OWN) on entry;
 * it only pre-selects the radio button. Explicitly tapping OWN applies it.
 */
@Composable
private fun StyleStep(
    header: @Composable () -> Unit,
    previous: PreviousKeyboard?,
    presetName: String?,
    onPresetApplied: (KeyboardPreset) -> Unit,
    onNext: () -> Unit
) {
    val ctx = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = Brand.accent(dark)
    val textColor = Brand.text(dark)
    val textColorDim = Brand.textDim(dark)
    val fill = if (dark) Color(0x17FFFFFF) else Color(0x0F000000)
    val border = Brand.cardBorder(dark)
    val shape = RoundedCornerShape(6.dp)

    var selectedPreset by rememberSaveable {
        mutableStateOf(
            presetName?.let { runCatching { KeyboardPreset.valueOf(it) }.getOrNull() }
                ?: when (previous) {
                    PreviousKeyboard.GBOARD -> KeyboardPreset.GBOARD
                    PreviousKeyboard.SAMSUNG -> KeyboardPreset.SAMSUNG
                    else -> KeyboardPreset.OWN
                }
        )
    }

    var vibrateEnabled by rememberSaveable {
        mutableStateOf(ctx.prefs().getBoolean(KeySettings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON))
    }

    var testText by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (presetName == null) {
            val initial = previous?.toPreset()
            if (initial != null) {
                KeyboardPresets.apply(ctx, initial)
                (ctx.getActivity() as? SettingsActivity)?.prefChanged()
                KeyboardSwitcher.getInstance().reloadKeyboard()
                onPresetApplied(initial)
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun selectPreset(preset: KeyboardPreset) {
        selectedPreset = preset
        KeyboardPresets.apply(ctx, preset)
        (ctx.getActivity() as? SettingsActivity)?.prefChanged()
        KeyboardSwitcher.getInstance().reloadKeyboard()
        onPresetApplied(preset)
    }

    Column(Modifier.fillMaxSize()) {
        header()
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.setup_style_label),
                style = MaterialTheme.typography.titleSmall.merge(color = textColor),
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    Pair(KeyboardPreset.OWN, stringResource(R.string.setup_style_own)),
                    Pair(KeyboardPreset.GBOARD, stringResource(R.string.setup_style_gboard)),
                    Pair(KeyboardPreset.SAMSUNG, stringResource(R.string.setup_style_samsung))
                )
                presets.forEach { (preset, label) ->
                    val isSelected = selectedPreset == preset
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(shape)
                            .background(if (isSelected) accent.copy(alpha = if (dark) 0.20f else 0.14f) else fill)
                            .border(
                                BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accent else border),
                                shape
                            )
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { selectPreset(preset) }
                            )
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            modifier = Modifier.size(20.dp),
                            colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = textColorDim)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall.merge(color = textColor),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(fill)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.keyboard_accent_title),
                    style = MaterialTheme.typography.bodyMedium.merge(color = textColor),
                    modifier = Modifier.weight(1f)
                )
                // keyed on the preset: choosing a preset changes what "no choice yet" shows
                androidx.compose.runtime.key(selectedPreset) { AccentDots(ringColor = textColor) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(fill)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.setup_vibrate_switch),
                    style = MaterialTheme.typography.bodyMedium.merge(color = textColor),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = vibrateEnabled,
                    onCheckedChange = { checked ->
                        vibrateEnabled = checked
                        ctx.prefs().edit().putBoolean(KeySettings.PREF_VIBRATE_ON, checked).apply()
                        (ctx.getActivity() as? SettingsActivity)?.prefChanged()
                        KeyboardSwitcher.getInstance().reloadKeyboard()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent,
                        uncheckedThumbColor = textColorDim,
                        uncheckedTrackColor = fill
                    )
                )
            }

            OutlinedTextField(
                value = testText,
                onValueChange = { testText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.setup_style_hint), style = MaterialTheme.typography.bodyMedium) },
                minLines = 2,
                maxLines = 3,
                shape = shape,
                textStyle = MaterialTheme.typography.bodyMedium.merge(color = textColor),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = border,
                    cursorColor = accent,
                    focusedContainerColor = fill,
                    unfocusedContainerColor = fill
                )
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onNext,
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
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
    // On a light ground the mark is the brand's black version: black where the everyday mark is
    // white, light where it is dark. The white one all but disappeared into the page.
    val logoRes = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) R.drawable.ic_launcher_foreground
        else R.drawable.ic_launcher_foreground_dark
    // Drawn through renderMark for the reason it exists: a vector's bounds are not its ink. The
    // launcher foreground carries the adaptive-icon safe area, so laying it out at 160dp puts a
    // mark of about a hundred on screen, off centre by whatever the artwork is off centre by.
    val logo = remember(logoRes) {
        val px = (ctx.resources.displayMetrics.density * HERO_LOGO_DP).toInt()
        ContextCompat.getDrawable(ctx, logoRes)
            ?.let { VoiceGlow.renderMark(it, px) }
            ?.asImageBitmap()
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (logo != null)
            Image(BitmapPainter(logo), null, Modifier.size(HERO_LOGO_DP.dp))
        else
            Image(painterResource(logoRes), null, Modifier.size(HERO_LOGO_DP.dp))
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
            //
            // The Thin figure is measured, not guessed: "START SPEAKING." set in Ubuntu Sans Thin is
            // 258.4dp wide at 34sp, 7.6dp per sp across its 15 characters, so 0.51 per character.
            // It used to be one constant for that one phrase, which is why the closing line -- twice
            // as long -- wrapped. Per character it holds for any line. Re-measure if the family
            // changes; the SemiBold figure scales the same measurement by the weight's width.
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
        // A button, filled in the brand's violet. It used to be the label alone with a click handler
        // -- type floating under the headline that nobody could tell was the way forward.
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand.accent(dark),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
            modifier = Modifier.heightIn(min = 52.dp)
        ) {
            Text(
                stringResource(if (closing) R.string.setup_finish_action else R.string.setup_start_action),
                fontFamily = BrandFont,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * The floating mark, live, shrunk to fit [modifier]'s box.
 *
 * The view is laid out at the size the real window would have and scaled down to the box, so the
 * drawing is the real one; the box itself stays the layout size, which keeps it from overlapping
 * the text beside it.
 */
@Composable
private fun FloatingMarkPreview(modifier: Modifier) {
    val ctx = LocalContext.current
    val native = remember { VoiceOverlay(ctx).previewSizePx() }
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val target = with(density) { maxWidth.toPx() }
        val scale = if (native > 0) target / native else 1f
        AndroidView(
            factory = { VoiceOverlay(it).apply { startPreview() } },
            modifier = Modifier
                .requiredSize(with(density) { native.toDp() })
                .graphicsLayer { scaleX = scale; scaleY = scale }
        )
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
private fun HeroWaves(dark: Boolean) {
    // Keyed on the theme, so switching it rebuilds the view with the matching colour.
    val colour = if (dark) HERO_WAVE_COLOUR else HERO_WAVE_COLOUR_LIGHT
    key(colour) {
    AndroidView(
        factory = { c -> VoiceWaveView(c).apply { startDemo(colour) } },
        modifier = Modifier.fillMaxSize(),
        // Leaving the page must stop the frame loop. VoiceWaveView stops itself on detach as well,
        // but relying on that alone is how an animation outlives the thing that started it.
        onRelease = { it.stop() }
    )
    }
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
 * The waves' colour on the hero, which is the brand's and not the keyboard theme's.
 *
 * `tailwind.config.cjs` primary-500. During a session the waves take ColorType.GESTURE_TRAIL so
 * they belong to whatever theme the user picked; here there is no keyboard on screen and no session,
 * and the page's whole job is to look like vibevoice.net.
 */
private const val HERO_WAVE_COLOUR = 0xFF8B5CF6.toInt()

/**
 * The same violet, three steps lighter (primary-300), for a light page.
 *
 * At primary-500 the lines were nearly as dark as the black headline running across them, and a
 * colleague testing in light mode read them as noise in the text. On a dark page the lines are
 * light and the text is white, so the contrast comes from elsewhere; on a light page it has to come
 * from the lines being pale.
 */
private const val HERO_WAVE_COLOUR_LIGHT = 0xFFC4B5FD.toInt()

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
