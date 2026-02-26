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
import androidx.compose.runtime.LaunchedEffect
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
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SearchSettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VibeVoiceSettingsScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.prefs()

    var apiKey by remember { mutableStateOf(prefs.getString("vibevoice_api_key", null)) }
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
                            prefs.edit().putString("vibevoice_api_key", apiKey).apply()
                            polling = false
                        } else if (tokenRes.optString("error") != "authorization_pending") {
                            polling = false
                            errorMessage = "Linking failed: ${tokenRes.optString("error")}"
                        }
                    }
                }
            } else {
                errorMessage = "Failed to request device code"
            }
        }
    }

    fun unlink() {
        prefs.edit().remove("vibevoice_api_key").apply()
        apiKey = null
        userCode = null
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = "VibeVoice Integration",
        settings = emptyList() // Not a SearchSettingsScreen with list preferences
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("VibeVoice Account linking allows utilizing cloud transcription directly inside the keyboard.", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.size(16.dp))

            if (apiKey != null) {
                Text("Status: Linked!", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.size(16.dp))
                Button(onClick = { unlink() }) {
                    Text("Unlink Device")
                }
            } else if (userCode != null) {
                Text("Waiting for approval...", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Please enter the code in your browser:", style = MaterialTheme.typography.bodyMedium)
                Text(userCode ?: "", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

                Spacer(modifier = Modifier.size(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Polling for token...")
                }
            } else {
                Button(onClick = { startLinking() }, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Link VibeVoice Account")
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
