package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.vibevoice.VibeVoiceClient
import helium314.keyboard.settings.SearchSettingsScreen
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VibeVoiceSettingsScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { vibeVoicePrefs(context) }

    var apiKey by remember { mutableStateOf(prefs.getString(VIBEVOICE_API_KEY_PREF, null)) }
    var userCode by remember { mutableStateOf<String?>(null) }
    var verificationUri by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

                // Copy verificationUri
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$verificationUri?code=$userCode"))
                context.startActivity(intent)

                // Poll
                var polling = true
                while (polling) {
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
                Text(
                    stringResource(R.string.vibevoice_status_linked),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.size(16.dp))
                Button(onClick = { unlink() }) {
                    Text(stringResource(R.string.vibevoice_unlink_device))
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

private fun vibeVoicePrefs(context: Context) = try {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    EncryptedSharedPreferences.create(
        context,
        "vibevoice_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
} catch (_: Exception) {
    context.prefs()
}
