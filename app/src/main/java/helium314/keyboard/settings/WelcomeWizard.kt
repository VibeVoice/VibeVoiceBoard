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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
    val stepBackgroundColor = Color(ContextCompat.getColor(ctx, R.color.setup_step_background))
    val textColor = Color(ContextCompat.getColor(ctx, R.color.setup_text_action))
    val textColorDim = textColor.copy(alpha = 0.5f)
    val titleColor = Color(ContextCompat.getColor(ctx, R.color.setup_text_title))
    val appName = stringResource(ctx.applicationInfo.labelRes)
    @Composable fun bigText() {
        // Nothing above the hero. It carries the wordmark and the slogan itself, and a second
        // heading over them would be the page saying its own name twice.
        if (step == 0) return
        val resource = R.string.setup_steps_title
        Column(Modifier.padding(bottom = 36.dp)) {
            Text(
                stringResource(resource, appName),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                color = titleColor,
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
            (1..6).forEach { Text("$it", color = if (it == step) titleColor else textColorDim) }
        }
        Column(Modifier
            .background(color = stepBackgroundColor)
            .padding(16.dp)
        ) {
            Text(title)
            Text(instruction, style = MaterialTheme.typography.bodyLarge.merge(color = textColor))
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.clickable { action() }
                .background(color = stepBackgroundColor)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.padding(end = 6.dp).size(32.dp), tint = textColor)
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
            (1..6).forEach { Text("$it", color = if (it == current) titleC else dimC) }
        }
        Column(Modifier.background(color = bg).padding(16.dp)) {
            Text(title)
            Text(instruction, style = MaterialTheme.typography.bodyLarge.merge(color = textC))
        }
    }
    @Composable fun ActionRow(icon: Int, text: String, active: Boolean, onClick: () -> Unit) {
        Row(
            Modifier.clickable { onClick() }
                .background(color = stepBackgroundColor)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(icon), null,
                Modifier.padding(end = 6.dp).size(32.dp),
                tint = if (active) textColor else textColorDim
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
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.clickable { close() }
                            .background(color = stepBackgroundColor)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.sym_keyboard_language_switch),
                            null,
                            Modifier.padding(end = 6.dp).size(32.dp),
                            tint = textColor
                        )
                        Text(stringResource(R.string.setup_step3_action), Modifier.weight(1f))
                    }
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
                        stringResource(R.string.setup_step4_action),
                        painterResource(R.drawable.ic_vibevoice_active)
                    ) { step = 4 }
                } else if (step == 4) {
                    // The account first, because nothing else here does anything without it. Sent
                    // to the VibeVoice screen rather than reimplemented: the device flow there
                    // handles an expired code, a missing browser and a body that is not the JSON it
                    // expected, and a second copy of that would be a second copy to get wrong.
                    var linked by rememberSaveable { mutableStateOf(VibeVoiceClient.getApiKey(ctx) != null) }
                    OnResume { linked = VibeVoiceClient.getApiKey(ctx) != null }
                    StepHeader(4, titleColor, textColorDim, stepBackgroundColor, textColor,
                        stringResource(R.string.setup_step4_title),
                        stringResource(R.string.setup_step4_instruction))
                    Spacer(Modifier.height(4.dp))
                    if (linked) {
                        ActionRow(R.drawable.ic_setup_check, stringResource(R.string.setup_step4_linked), true) { }
                        Spacer(Modifier.height(4.dp))
                        ActionRow(R.drawable.ic_setup_select, stringResource(R.string.setup_next_action), true) {
                            step = 5
                        }
                    } else {
                        // Linked here rather than by sending the user into the settings screen,
                        // which carries the account, the quota, bug reports and three tuning blocks
                        // -- everything except the one thing they came for. The panel is the same
                        // implementation that screen uses.
                        Column(Modifier.background(color = stepBackgroundColor).padding(16.dp)) {
                            VibeVoiceLinkPanel { linked = true; step = 5 }
                        }
                    }
                } else if (step == 5) { // the microphone, and how dictation is actually started
                    var mic by rememberSaveable {
                        mutableStateOf(ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO)
                                == android.content.pm.PackageManager.PERMISSION_GRANTED)
                    }
                    // Asked for directly. PermissionActivity exists because an input method cannot
                    // request a runtime permission; here we are in an activity and can.
                    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                        mic = it
                    }
                    StepHeader(5, titleColor, textColorDim, stepBackgroundColor, textColor,
                        stringResource(R.string.setup_step5_title),
                        stringResource(R.string.setup_step5_instruction))
                    Spacer(Modifier.height(4.dp))
                    ActionRow(
                        if (mic) R.drawable.ic_setup_check else R.drawable.ic_vibevoice_active,
                        stringResource(if (mic) R.string.setup_step5_mic_granted else R.string.setup_step5_mic),
                        mic
                    ) {
                        if (!mic) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                    if (mic) {
                        // The first dictation, here, before the wizard is over. Everything up to
                        // this point is a description of a thing; this is the thing. It works
                        // because the account was linked in step 4 -- the version that runs before
                        // any of that needs the server-side trial, which is P-058 in the VibeVoice
                        // repository.
                        var practice by rememberSaveable { mutableStateOf("") }
                        Spacer(Modifier.height(4.dp))
                        Column(Modifier.background(color = stepBackgroundColor).padding(16.dp)) {
                            Text(
                                stringResource(R.string.setup_step5_try_label),
                                style = MaterialTheme.typography.bodyLarge.merge(color = textColor)
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = practice,
                                onValueChange = { practice = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.setup_step5_try_hint)) },
                                minLines = 3
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    ActionRow(R.drawable.ic_setup_select, stringResource(R.string.setup_next_action), true) {
                        step = 6
                    }
                } else { // step 6: optional, and chained -- the second offer only means something
                         // once the first has been taken
                    val prefs = ctx.prefs()
                    var background by rememberSaveable {
                        mutableStateOf(prefs.getBoolean(KeySettings.PREF_VOICE_BACKGROUND, Defaults.PREF_VOICE_BACKGROUND))
                    }
                    var overlay by rememberSaveable { mutableStateOf(VoiceOverlay.isAllowed(ctx)) }
                    OnResume { overlay = VoiceOverlay.isAllowed(ctx) }
                    StepHeader(6, titleColor, textColorDim, stepBackgroundColor, textColor,
                        stringResource(R.string.setup_step6_title),
                        stringResource(R.string.setup_step6_instruction))
                    Spacer(Modifier.height(4.dp))
                    ActionRow(
                        if (background) R.drawable.ic_setup_check else R.drawable.ic_vibevoice_active,
                        stringResource(R.string.setup_step6_background),
                        background
                    ) {
                        background = !background
                        prefs.edit().putBoolean(KeySettings.PREF_VOICE_BACKGROUND, background).apply()
                    }
                    if (background) {
                        // Only now: allowing an overlay for a mark that can never appear is a
                        // permission asked for nothing.
                        Spacer(Modifier.height(4.dp))
                        ActionRow(
                            if (overlay) R.drawable.ic_setup_check else R.drawable.ic_setup_select,
                            stringResource(if (overlay) R.string.setup_step6_overlay_granted
                                else R.string.setup_step6_overlay),
                            overlay
                        ) {
                            if (!overlay) {
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
                    }
                    Spacer(Modifier.height(4.dp))
                    ActionRow(R.drawable.ic_setup_check, stringResource(R.string.setup_finish_action), true) {
                        finish()
                    }
                    Spacer(Modifier.height(4.dp))
                    // The settings, at the end, where leaving the wizard costs nothing.
                    ActionRow(R.drawable.sym_keyboard_language_switch, stringResource(R.string.setup_step3_action), false) {
                        close()
                    }
                }
            }
    }
    val ground = MaterialTheme.colorScheme.surface
    // Which palette the blobs take. The CSS has two, and they are not each other's inverse: the
    // light one multiplies at 0.12 and the dark one screens at 0.165.
    val dark = ground.luminance() < 0.5f
    Box(Modifier.fillMaxSize()) {
        BrandBackground(ground, dark)
        // The waves only on the hero. They are the keyboard's signature -- what a running session
        // looks like -- and putting them behind every page would spend that.
        if (step == 0) HeroWaves()
        Surface(color = Color.Transparent) {
        CompositionLocalProvider(
            LocalContentColor provides textColor,
            LocalTextStyle provides MaterialTheme.typography.titleLarge.merge(color = textColor),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
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
