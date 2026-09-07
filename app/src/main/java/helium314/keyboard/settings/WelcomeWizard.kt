// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

@Composable
fun WelcomeWizard(
    close: () -> Unit,
    finish: () -> Unit
) {
    val ctx = LocalContext.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    fun determineStep(): Int = when {
        !UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm) -> 0
        !UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm) -> 2
        else -> 3
    }
    var step by rememberSaveable { mutableIntStateOf(determineStep()) }
    val scope = rememberCoroutineScope { Dispatchers.IO }

    // The free minutes, asked for the moment the wizard opens rather than when they are needed.
    // Between here and step 4 the user enables an input method and switches to it -- two trips
    // through system settings -- so the key is in hand long before the practice field asks for it,
    // and a slow network never shows as a spinner on the one screen that has to feel immediate.
    // Null means "no answer yet"; the practice field distinguishes that from a refusal.
    var trialMinutes by rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(Unit) {
        if (VibeVoiceClient.isLinked(ctx)) { trialMinutes = 0; return@LaunchedEffect }
        trialMinutes = when (val res = VibeVoiceClient.requestTrialKey(ctx)) {
            is TrialResult.Granted -> res.minutesGranted.toInt()
            // Already used, rate limited or unreachable all land in the same place for the user:
            // there are no free minutes, and the account step is where dictation comes from.
            else -> 0
        }
    }
    LaunchedEffect(step) {
        if (step == 2)
            scope.launch {
                while (step == 2 && !UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)) {
                    delay(50)
                }
                step = 3
            }
    }
    val useWideLayout = isWideScreen()
    // The brand's palette, not res/values*/colors.xml. Those resolve to Material You on Android 12
    // and up, so the wizard wore the user's wallpaper accent -- the loudest thing on a screen whose
    // job is to be recognised as VibeVoice. See the note on Brand.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val stepBackgroundColor = Brand.card(dark)
    val stepBorderColor = Brand.cardBorder(dark)
    val cardShape = RoundedCornerShape(Brand.corner.dp)
    val textColor = Brand.text(dark)
    val textColorDim = Brand.textFaint(dark)
    val titleColor = Brand.text(dark)
    val appName = stringResource(ctx.applicationInfo.labelRes)
    @Composable fun bigText() {
        // Nothing above the hero. It carries the wordmark and the slogan itself, and a second
        // heading over them would be the page saying its own name twice.
        if (step == 0) return
        val resource = R.string.setup_steps_title
        Column(Modifier.padding(bottom = 36.dp)) {
            // Set the way the site sets its headline: uppercase and light, not a Material display
            // face. It is the same voice two pages apart, which is the whole point of the exercise.
            Text(
                stringResource(resource, appName).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
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
    @Composable
    fun ColumnScope.Step(step: Int, title: String, instruction: String, actionText: String, icon: Painter, action: () -> Unit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (1..6).forEach { Text("$it", color = if (it == step) Brand.accent else textColorDim) }
        }
        Column(Modifier
            .clip(cardShape)
            .background(color = stepBackgroundColor)
            .border(1.dp, stepBorderColor, cardShape)
            .padding(16.dp)
        ) {
            Text(title)
            Text(instruction, style = MaterialTheme.typography.bodyLarge.merge(color = Brand.textDim(dark)))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.clip(cardShape)
                .clickable { action() }
                .background(color = stepBackgroundColor)
                .border(1.dp, stepBorderColor, cardShape)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.padding(end = 10.dp).size(28.dp), tint = Brand.accent)
            Text(actionText, Modifier.weight(1f))
        }
    }
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
    @Composable fun StepHeader(
        current: Int, titleC: Color, dimC: Color, bg: Color, textC: Color, title: String, instruction: String
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (1..6).forEach { Text("$it", color = if (it == current) Brand.accent else dimC) }
        }
        Column(Modifier
            .clip(cardShape)
            .background(color = bg)
            .border(1.dp, stepBorderColor, cardShape)
            .padding(16.dp)
        ) {
            Text(title)
            Text(instruction, style = MaterialTheme.typography.bodyLarge.merge(color = textC))
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
        if (step == 0)
            WizardHero { step = 1 }
        else
            Column {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    // Step 4 is chosen, not derived: determineStep only knows about the input method
                    // and would send anyone who opened the overlay settings back to "all set".
                    if (step < 4) step = determineStep()
                }
                if (step == 1) {
                    Step(
                        step,
                        stringResource(R.string.setup_step1_title, appName),
                        stringResource(R.string.setup_step1_instruction, appName),
                        stringResource(R.string.setup_step1_action),
                        painterResource(R.drawable.ic_setup_key)
                    ) {
                        val intent = Intent()
                        intent.action = Settings.ACTION_INPUT_METHOD_SETTINGS
                        intent.addCategory(Intent.CATEGORY_DEFAULT)
                        launcher.launch(intent)
                    }
                } else if (step == 2) {
                    Step(
                        step,
                        stringResource(R.string.setup_step2_title, appName),
                        stringResource(R.string.setup_step2_instruction, appName),
                        stringResource(R.string.setup_step2_action),
                        painterResource(R.drawable.ic_setup_select),
                        imm::showInputMethodPicker
                    )
                    // No exit here. `close()` ends the wizard for good -- there is no way back
                    // into it -- and offering that on the step before the keyboard has even been
                    // switched to drops the user into a settings tree with nothing yet to
                    // configure. The same exit was removed from step 3 for the same reason and
                    // survived here because I fixed one and not the other.
                } else if (step == 3) {
                    // One way on, and it is forward. The settings used to be offered here, which
                    // closed the wizard for good -- there is no way back into it -- two steps
                    // before dictation was set up, and dropped the user into a settings tree they
                    // have no reason to understand yet. That exit belongs at the end, once there
                    // is something to configure.
                    Step(
                        step,
                        stringResource(R.string.setup_step3_typing_ready),
                        stringResource(R.string.setup_step3_instruction, appName),
                        stringResource(R.string.setup_continue_action),
                        painterResource(R.drawable.ic_vibevoice_active)
                    ) { step = 4 }
                } else if (step == 4) {
                    // The microphone and the first dictation, BEFORE the account.
                    //
                    // It used to be the other way round, and that was the whole defect P-058 names:
                    // the account is a cost the user pays, the transcription is the benefit they
                    // have not yet seen, and asking for the cost first is asking someone to buy
                    // something they have not been shown. The server now issues free minutes
                    // against an install id, so this step can do the showing.
                    var mic by rememberSaveable {
                        mutableStateOf(ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO)
                                == android.content.pm.PackageManager.PERMISSION_GRANTED)
                    }
                    // Asked for directly. PermissionActivity exists because an input method cannot
                    // request a runtime permission; here we are in an activity and can.
                    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                        mic = it
                    }
                    var practice by rememberSaveable { mutableStateOf("") }
                    StepHeader(4, titleColor, textColorDim, stepBackgroundColor, textColor,
                        stringResource(R.string.setup_mic_title),
                        stringResource(R.string.setup_mic_instruction))
                    if (trialMinutes > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.setup_trial_note, trialMinutes),
                            style = MaterialTheme.typography.bodyMedium.merge(color = Brand.accent)
                        )
                    } else if (trialMinutes == 0 && !VibeVoiceClient.isLinked(ctx)) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.setup_trial_unavailable),
                            style = MaterialTheme.typography.bodyMedium.merge(color = Brand.textDim(dark))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ActionRow(
                        if (mic) R.drawable.ic_setup_check else R.drawable.ic_setup_key,
                        stringResource(if (mic) R.string.setup_mic_granted else R.string.setup_mic_grant),
                        mic
                    ) {
                        if (!mic) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                    if (mic && VibeVoiceClient.getApiKey(ctx) != null) {
                        // The thing itself. Everything before this describes a product; this is the
                        // product, and it happens before anybody has been asked for anything.
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier
                            .clip(cardShape)
                            .background(color = stepBackgroundColor)
                            .border(1.dp, stepBorderColor, cardShape)
                            .padding(16.dp)
                        ) {
                            Text(
                                stringResource(R.string.setup_try_label),
                                style = MaterialTheme.typography.bodyLarge.merge(color = textColor)
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = practice,
                                onValueChange = { practice = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.setup_try_hint)) },
                                minLines = 3
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Always present, never hidden: somebody whose microphone or network refuses to
                    // work would otherwise be shut in this step with no way out, and a wizard must
                    // not close a door it cannot reopen. It changes weight instead of appearing --
                    // quiet until something has been dictated, the obvious next move afterwards.
                    val tried = practice.isNotBlank()
                    ActionRow(
                        if (tried) R.drawable.ic_setup_check else R.drawable.ic_setup_select,
                        stringResource(if (tried) R.string.setup_next_action else R.string.setup_try_skip),
                        tried
                    ) { step = 5 }
                } else if (step == 5) {
                    // The account, now that there is something to have an account for.
                    var linked by rememberSaveable { mutableStateOf(VibeVoiceClient.isLinked(ctx)) }
                    OnResume { linked = VibeVoiceClient.isLinked(ctx) }
                    StepHeader(5, titleColor, textColorDim, stepBackgroundColor, textColor,
                        stringResource(R.string.setup_link_title),
                        stringResource(R.string.setup_link_instruction))
                    Spacer(Modifier.height(8.dp))
                    if (linked) {
                        ActionRow(R.drawable.ic_setup_check, stringResource(R.string.setup_link_done), true) { }
                        Spacer(Modifier.height(8.dp))
                        ActionRow(R.drawable.ic_setup_select, stringResource(R.string.setup_next_action), true) {
                            step = 6
                        }
                    } else {
                        // Linked here rather than by sending the user into the settings screen,
                        // which carries the account, the quota, bug reports and three tuning blocks
                        // -- everything except the one thing they came for. The panel is the same
                        // implementation that screen uses; only its button is ours.
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
                        ) { linked = true; step = 6 }
                        Spacer(Modifier.height(8.dp))
                        // The trial does not last forever, but nobody has to link today.
                        ActionRow(R.drawable.ic_setup_select, stringResource(R.string.setup_try_skip), false) {
                            step = 6
                        }
                    }
                } else { // step 6: two optional extras, one of which depends on the other
                    val prefs = ctx.prefs()
                    var background by rememberSaveable {
                        mutableStateOf(prefs.getBoolean(KeySettings.PREF_VOICE_BACKGROUND, Defaults.PREF_VOICE_BACKGROUND))
                    }
                    var overlay by rememberSaveable { mutableStateOf(VoiceOverlay.isAllowed(ctx)) }
                    OnResume { overlay = VoiceOverlay.isAllowed(ctx) }
                    StepHeader(6, titleColor, textColorDim, stepBackgroundColor, textColor,
                        stringResource(R.string.setup_extras_title),
                        stringResource(R.string.setup_extras_instruction))
                    Spacer(Modifier.height(8.dp))
                    ActionRow(
                        if (background) R.drawable.ic_setup_check else R.drawable.ic_setup_select,
                        stringResource(R.string.setup_extras_background),
                        background
                    ) {
                        background = !background
                        prefs.edit().putBoolean(KeySettings.PREF_VOICE_BACKGROUND, background).apply()
                    }
                    Spacer(Modifier.height(8.dp))
                    // Shown even when it cannot be taken, with the reason on it.
                    //
                    // It used to be hidden until the option above was on, and hiding it was worse
                    // than the dependency it was hiding: the heading promises two extras and the
                    // screen showed one, so the step read as either finished or broken. Allowing an
                    // overlay for a mark that can never appear is still a permission asked for
                    // nothing -- so the row stays inert, and says why.
                    ActionRow(
                        if (overlay && background) R.drawable.ic_setup_check else R.drawable.ic_setup_select,
                        stringResource(
                            when {
                                !background -> R.string.setup_extras_overlay_locked
                                overlay -> R.string.setup_extras_overlay_granted
                                else -> R.string.setup_extras_overlay
                            }
                        ),
                        background && overlay
                    ) {
                        if (background && !overlay) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + ctx.packageName)
                            )
                            try {
                                launcher.launch(intent)
                            } catch (_: android.content.ActivityNotFoundException) {
                                // Some builds have no such screen. Nothing else breaks.
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // The label says what is being given up. "Finished" under an untouched toggle
                    // reads as "you are done" and hides the fact that there was anything to decide.
                    ActionRow(
                        R.drawable.ic_setup_check,
                        stringResource(
                            if (background) R.string.setup_finish_action else R.string.setup_finish_without
                        ),
                        background
                    ) {
                        finish()
                    }
                    // No second exit. "Finished" and "Configure the keyboard" both ended the wizard
                    // and only one of them said so; the settings are one tap away afterwards.
                }
            }
    }
    Box(Modifier.fillMaxSize()) {
        BrandBackground(dark)
        // The waves only on the hero. They are the keyboard's signature -- what a running session
        // looks like -- and putting them behind every page would spend that.
        if (step == 0) HeroWaves()
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
fun WizardHero(onClick: () -> Unit) {
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
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.brand_wordmark).uppercase(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.brand_slogan_line1),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Thin,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.brand_slogan_line2),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Thin,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.brand_subline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Row(Modifier.clickable { onClick() }.padding(top = 4.dp, start = 4.dp, end = 4.dp)) {
            Text(
                stringResource(R.string.setup_start_action),
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
