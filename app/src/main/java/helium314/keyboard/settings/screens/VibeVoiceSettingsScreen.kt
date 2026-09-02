package helium314.keyboard.settings.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import androidx.compose.material3.HorizontalDivider
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.vibevoice.VibeVoiceClient
import helium314.keyboard.settings.preferences.SliderPreference
import java.util.Locale
import helium314.keyboard.settings.SearchSettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import helium314.keyboard.latin.vibevoice.VibeVoiceBugReporter

@Composable
fun VibeVoiceSettingsScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { VibeVoiceClient.vibeVoicePrefs(context) }

    var apiKey by remember { mutableStateOf(prefs.getString(VIBEVOICE_API_KEY_PREF, null)) }
    var userCode by remember { mutableStateOf<String?>(null) }
    var verificationUri by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showBugReportDialog by remember { mutableStateOf(false) }
    var bugDescription by remember { mutableStateOf("") }
    var isSubmittingBugReport by remember { mutableStateOf(false) }
    var bugReportStatus by remember { mutableStateOf<String?>(null) }
    var isBugReportSuccess by remember { mutableStateOf(false) }

    var quotaInfo by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var isQuotaLoading by remember { mutableStateOf(false) }
    var quotaError by remember { mutableStateOf(false) }

    LaunchedEffect(apiKey) {
        val currentKey = apiKey
        if (currentKey != null) {
            isQuotaLoading = true
            quotaError = false
            val quota = VibeVoiceClient.fetchQuota(currentKey)
            if (quota != null) {
                quotaInfo = quota
            } else {
                quotaError = true
            }
            isQuotaLoading = false
        } else {
            quotaInfo = null
            quotaError = false
        }
    }

    fun startLinking() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val res = VibeVoiceClient.requestDeviceCode("VibeVoiceBoard Android", BuildConfig.VERSION_NAME)
            isLoading = false
            if (res != null) {
                // A body without these keys -- an error payload, an API change, a captive portal
                // answering 200 with HTML -- used to throw JSONException out of the coroutine and
                // take the settings activity down with it.
                val code = res.optString("user_code").takeIf { it.isNotBlank() }
                val uri = res.optString("verification_uri").takeIf { it.isNotBlank() }
                val deviceCode = res.optString("device_code").takeIf { it.isNotBlank() }
                if (code == null || uri == null || deviceCode == null) {
                    errorMessage = context.getString(R.string.vibevoice_failed_request_device_code)
                    return@launch
                }
                userCode = code
                verificationUri = uri
                val interval = res.optInt("interval", 5).coerceAtLeast(1)
                // RFC 8628 device codes expire; without this the loop below polls forever whenever
                // pollForToken keeps returning null (offline, or the user never finishes in the browser).
                val expiresAt = System.currentTimeMillis() + res.optInt("expires_in", 600).coerceAtLeast(30) * 1000L

                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$verificationUri?code=$userCode"))
                    context.startActivity(intent)
                } catch (_: android.content.ActivityNotFoundException) {
                    errorMessage = context.getString(R.string.vibevoice_no_browser)
                }

                // Poll
                var polling = true
                while (polling && isActive) {
                    delay(interval * 1000L)
                    if (System.currentTimeMillis() > expiresAt) {
                        polling = false
                        userCode = null
                        errorMessage = context.getString(R.string.vibevoice_linking_failed, "expired_token")
                        break
                    }
                    val tokenRes = VibeVoiceClient.pollForToken(deviceCode)
                    if (tokenRes != null) {
                        if (tokenRes.has("api_key")) {
                            apiKey = tokenRes.getString("api_key")
                            prefs.edit().putString(VIBEVOICE_API_KEY_PREF, apiKey).apply()
                            polling = false
                        } else if (tokenRes.optString("error") != "authorization_pending") {
                            polling = false
                            errorMessage = context.getString(R.string.vibevoice_linking_failed, tokenRes.optString("error"))
                        }
                    }
                }
            } else {
                errorMessage = context.getString(R.string.vibevoice_failed_request_device_code)
            }
        }
    }

    fun unlink() {
        prefs.edit().remove(VIBEVOICE_API_KEY_PREF).apply()
        apiKey = null
        userCode = null
    }

    fun submitBugReport() {
        if (bugDescription.isBlank()) return
        isSubmittingBugReport = true
        bugReportStatus = null
        isBugReportSuccess = false
        scope.launch {
            val result = VibeVoiceBugReporter.sendBugReport(context, bugDescription)
            isSubmittingBugReport = false
            result.onSuccess { reportId ->
                isBugReportSuccess = true
                bugReportStatus = context.getString(R.string.vibevoice_report_bug_success, reportId)
            }.onFailure { err ->
                isBugReportSuccess = false
                bugReportStatus = err.message ?: "Failed to submit bug report"
            }
        }
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.vibevoice_integration_title),
        settings = emptyList() // Not a SearchSettingsScreen with list preferences
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.vibevoice_account_linking_description),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.size(16.dp))

            if (apiKey != null) {
                if (isQuotaLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            stringResource(R.string.vibevoice_fetching_quota),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.size(16.dp))
                } else if (quotaError) {
                    Text(
                        stringResource(R.string.vibevoice_quota_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Button(onClick = { unlink() }) {
                        Text(stringResource(R.string.vibevoice_unlink_device))
                    }
                } else if (quotaInfo != null) {
                    val planCode = quotaInfo!!.optString("plan_code", "free").lowercase()
                    val minutesUsed = quotaInfo!!.optDouble("minutes_used", 0.0)
                    val hasTotal = !quotaInfo!!.isNull("monthly_minutes")
                    val monthlyMinutes = if (hasTotal) quotaInfo!!.optDouble("monthly_minutes", 30.0) else 30.0
                    
                    val planColor = when (planCode) {
                        "ultra" -> Color(0xFF8B5CF6)
                        "pro" -> Color(0xFF3B82F6)
                        else -> Color(0xFF10B981)
                    }
                    
                    val fraction = if (hasTotal) (minutesUsed / monthlyMinutes).coerceIn(0.0, 1.0).toFloat() else 0f
                    val textLabel = if (hasTotal) {
                        stringResource(R.string.vibevoice_quota_used, minutesUsed, monthlyMinutes)
                    } else {
                        stringResource(R.string.vibevoice_quota_unlimited, minutesUsed)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.vibevoice_plan_label, planCode.uppercase()),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            textLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.size(8.dp))
                    
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = planColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                        gapSize = (-2).dp,
                        drawStopIndicator = {}
                    )

                    Spacer(modifier = Modifier.size(16.dp))

                    Button(onClick = { unlink() }) {
                        Text(stringResource(R.string.vibevoice_unlink_device))
                    }
                } else {
                    Text(
                        stringResource(R.string.vibevoice_status_linked),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Button(onClick = { unlink() }) {
                        Text(stringResource(R.string.vibevoice_unlink_device))
                    }
                }
            } else if (userCode != null) {
                Text(stringResource(R.string.vibevoice_waiting_for_approval), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.vibevoice_enter_code_in_browser), style = MaterialTheme.typography.bodyMedium)
                Text(userCode ?: "", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

                Spacer(modifier = Modifier.size(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.vibevoice_polling_for_token))
                }
            } else {
                Button(onClick = { startLinking() }, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.vibevoice_link_account))
                    }
                }
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.size(24.dp))

            // Bug Reporting Section
            Text(
                stringResource(R.string.vibevoice_report_bug_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                stringResource(R.string.vibevoice_report_bug_desc),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.size(12.dp))
            Button(
                onClick = {
                    bugDescription = ""
                    bugReportStatus = null
                    showBugReportDialog = true
                },
                enabled = apiKey != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.vibevoice_report_bug_title))
            }
            if (apiKey == null) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    stringResource(R.string.vibevoice_not_linked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Text(
                stringResource(R.string.vibevoice_waves_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                stringResource(R.string.vibevoice_waves_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.size(8.dp))

            // VoiceWaveView re-reads these every frame, so dragging a slider moves the waves while
            // the keyboard is open. Ranges are deliberately wide: this is for finding the look, not
            // for keeping anyone inside sensible values.
            SliderPreference(
                name = stringResource(R.string.vibevoice_waves_amplitude),
                key = Settings.PREF_WAVE_AMPLITUDE,
                default = Defaults.PREF_WAVE_AMPLITUDE,
                range = 0.05f..1.2f,
                // The three terms sum to at most 1.7, so what you see is 1.7x this value. The
                // label reports the excursion, not the raw factor.
                description = { "${(170 * it).toInt()}% of the gap between waves" }
            ) { }
            SliderPreference(
                name = stringResource(R.string.vibevoice_waves_reaction),
                key = Settings.PREF_WAVE_REACTION,
                default = Defaults.PREF_WAVE_REACTION,
                range = 0f..12f,
                description = { "loud voice = ${String.format(Locale.US, "%.1f", 1f + it)}x the size" }
            ) { }
            SliderPreference(
                name = stringResource(R.string.vibevoice_waves_attack),
                key = Settings.PREF_WAVE_ATTACK,
                default = Defaults.PREF_WAVE_ATTACK,
                range = 0.05f..1f,
                description = { if (it > 0.95f) "instant" else String.format(Locale.US, "%.2f", it) }
            ) { }
            SliderPreference(
                name = stringResource(R.string.vibevoice_waves_damping),
                key = Settings.PREF_WAVE_DAMPING,
                default = Defaults.PREF_WAVE_DAMPING,
                range = 0.02f..1f,
                description = {
                    if (it < 0.06f) "swings on for a while" else String.format(Locale.US, "%.2f", it)
                }
            ) { }
            SliderPreference(
                name = stringResource(R.string.vibevoice_waves_spread),
                key = Settings.PREF_WAVE_SPREAD,
                default = Defaults.PREF_WAVE_SPREAD,
                range = 0f..1f,
                description = {
                    if (it < 0.05f) "parallel, never crossing" else "${(100 * it).toInt()}% apart"
                }
            ) { }
            SliderPreference(
                name = stringResource(R.string.vibevoice_waves_cycles),
                key = Settings.PREF_WAVE_CYCLES,
                default = Defaults.PREF_WAVE_CYCLES,
                range = 0.3f..6f,
                description = { String.format(Locale.US, "%.1f periods across the width", it) }
            ) { }
            SliderPreference(
                name = stringResource(R.string.vibevoice_waves_count),
                key = Settings.PREF_WAVE_COUNT,
                default = Defaults.PREF_WAVE_COUNT,
                range = 1f..12f,
                description = { "${it.toInt()}" }
            ) { }
            SliderPreference(
                name = stringResource(R.string.vibevoice_waves_speed),
                key = Settings.PREF_WAVE_SPEED,
                default = Defaults.PREF_WAVE_SPEED,
                range = 0f..0.06f,
                description = { String.format(Locale.US, "%.3f", it) }
            ) { }
            SliderPreference(
                name = stringResource(R.string.vibevoice_waves_jitter),
                key = Settings.PREF_WAVE_JITTER,
                default = Defaults.PREF_WAVE_JITTER,
                range = 0f..1f,
                description = { if (it < 0.02f) "none" else "${(100 * it).toInt()}%" }
            ) { }
        }

        if (showBugReportDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isSubmittingBugReport) showBugReportDialog = false
                },
                title = { Text(stringResource(R.string.vibevoice_report_bug_dialog_title)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = bugDescription,
                            onValueChange = { bugDescription = it },
                            placeholder = { Text(stringResource(R.string.vibevoice_report_bug_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 6,
                            enabled = !isSubmittingBugReport
                        )
                        if (isSubmittingBugReport) {
                            Spacer(modifier = Modifier.size(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(R.string.vibevoice_report_bug_submitting))
                            }
                        }
                        val status = bugReportStatus
                        if (status != null) {
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                status,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isBugReportSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { submitBugReport() },
                        enabled = bugDescription.isNotBlank() && !isSubmittingBugReport
                    ) {
                        Text(stringResource(R.string.vibevoice_report_bug_submit))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showBugReportDialog = false },
                        enabled = !isSubmittingBugReport
                    ) {
                        Text(stringResource(R.string.dialog_close))
                    }
                }
            )
        }
    }
}

private const val VIBEVOICE_API_KEY_PREF = "vibevoice_api_key"
