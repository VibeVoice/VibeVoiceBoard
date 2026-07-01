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
import helium314.keyboard.latin.vibevoice.VibeVoiceClient
import helium314.keyboard.settings.SearchSettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
                userCode = res.getString("user_code")
                verificationUri = res.getString("verification_uri")
                val deviceCode = res.getString("device_code")
                val interval = res.optInt("interval", 5)

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
        }
    }
}

private const val VIBEVOICE_API_KEY_PREF = "vibevoice_api_key"
