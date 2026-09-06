// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.vibevoice.VibeVoiceClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val VIBEVOICE_API_KEY_PREF = "vibevoice_api_key"

/**
 * Linking a VibeVoice account, as one piece.
 *
 * Used by the settings screen and by the setup wizard. It is a single implementation on purpose:
 * the device authorization grant has more failure modes than it looks -- a code that expires while
 * the user is elsewhere, a device with no browser to send them to, a poll that has to respect the
 * server's interval, and a response body that is not the JSON it claimed to be -- and a second copy
 * of that is a second copy to get wrong, and to drift.
 *
 * Renders nothing once an account is linked. What "linked" looks like is the caller's business: the
 * settings screen shows quota and an unlink button, the wizard shows a tick and moves on.
 *
 * [onLinked] fires once, when a key arrives.
 */
@Composable
fun VibeVoiceLinkPanel(modifier: Modifier = Modifier, onLinked: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { VibeVoiceClient.vibeVoicePrefs(context) }
    val scope = rememberCoroutineScope()

    var userCode by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun startLinking() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val res = VibeVoiceClient.requestDeviceCode("VibeVoiceBoard Android", BuildConfig.VERSION_NAME)
            isLoading = false
            if (res == null) {
                errorMessage = context.getString(R.string.vibevoice_failed_request_device_code)
                return@launch
            }
            // A body without these keys -- an error payload, an API change, a captive portal
            // answering 200 with HTML -- used to throw JSONException out of the coroutine and take
            // the whole activity down with it.
            val code = res.optString("user_code").takeIf { it.isNotBlank() }
            val uri = res.optString("verification_uri").takeIf { it.isNotBlank() }
            val deviceCode = res.optString("device_code").takeIf { it.isNotBlank() }
            if (code == null || uri == null || deviceCode == null) {
                errorMessage = context.getString(R.string.vibevoice_failed_request_device_code)
                return@launch
            }
            userCode = code
            val interval = res.optInt("interval", 5).coerceAtLeast(1)
            // RFC 8628 device codes expire; without this the loop below polls forever whenever
            // pollForToken keeps returning null -- offline, or the user never finishes in the browser.
            val expiresAt = System.currentTimeMillis() + res.optInt("expires_in", 600).coerceAtLeast(30) * 1000L

            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$uri?code=$code")))
            } catch (_: android.content.ActivityNotFoundException) {
                // Nothing can approve the code without a browser, so polling for ten minutes would
                // only hide the message behind a spinner.
                errorMessage = context.getString(R.string.vibevoice_no_browser)
                userCode = null
                return@launch
            }

            var polling = true
            while (polling && isActive) {
                delay(interval * 1000L)
                if (System.currentTimeMillis() > expiresAt) {
                    polling = false
                    userCode = null
                    errorMessage = context.getString(R.string.vibevoice_linking_failed, "expired_token")
                    break
                }
                val tokenRes = VibeVoiceClient.pollForToken(deviceCode) ?: continue
                if (tokenRes.has("api_key")) {
                    prefs.edit().putString(VIBEVOICE_API_KEY_PREF, tokenRes.getString("api_key")).apply()
                    polling = false
                    userCode = null
                    onLinked()
                } else if (tokenRes.optString("error") != "authorization_pending") {
                    polling = false
                    userCode = null
                    errorMessage = context.getString(R.string.vibevoice_linking_failed, tokenRes.optString("error"))
                }
            }
        }
    }

    if (VibeVoiceClient.getApiKey(context) != null && userCode == null && !isLoading) return

    Column(modifier = modifier) {
        if (userCode != null) {
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
