package helium314.keyboard.latin.vibevoice

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.bringAppToForeground
import helium314.keyboard.settings.openLinkInBrowser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodService::class, ShadowLooper::class])
class TrialResilienceAndLinkingTest {

    private lateinit var server: MockWebServer
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = MockWebServer().apply { start() }
        VibeVoiceClient.trialKeyEndpoint = server.url("/api/trial/key").toString()
        VibeVoiceClient.isWizardActive = false
        app.prefs().edit().clear().apply()
        VibeVoiceClient.vibeVoicePrefs(app).edit().clear().apply()
        shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        val cm = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val shadowCm = shadowOf(cm)
        val networkInfo = org.robolectric.shadows.ShadowNetworkInfo.newInstance(
            android.net.NetworkInfo.DetailedState.CONNECTED,
            android.net.ConnectivityManager.TYPE_WIFI,
            0,
            true,
            true
        )
        shadowCm.setActiveNetworkInfo(networkInfo)
        val network = cm.activeNetwork
        if (network != null) {
            val caps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
            shadowOf(caps).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowCm.setNetworkCapabilities(network, caps)
        }
    }

    @After
    fun tearDown() {
        VibeVoiceClient.trialKeyEndpoint = "https://vibevoice.net/api/trial/key"
        VibeVoiceClient.isWizardActive = false
        server.shutdown()
    }

    @Test
    fun trialKeyRequestSucceedsImmediatelyWhenServerReturns200() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"api_key":"trial_123","minutes_granted":10.0}""")
        )

        val result = VibeVoiceClient.requestTrialKeyWithRetry(app, maxAttempts = 3, initialDelayMs = 50L)
        assertTrue(result is TrialResult.Granted)
        assertEquals("trial_123", (result as TrialResult.Granted).key)
        assertEquals(1, server.requestCount)
        assertEquals("trial_123", VibeVoiceClient.getApiKey(app))
    }

    @Test
    fun trialKeyRequestRetriesWithBackoffAndSucceedsOnSecondAttempt() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"api_key":"trial_retry_success","minutes_granted":10.0}""")
        )

        val result = VibeVoiceClient.requestTrialKeyWithRetry(app, maxAttempts = 3, initialDelayMs = 50L)
        assertTrue(result is TrialResult.Granted)
        assertEquals("trial_retry_success", (result as TrialResult.Granted).key)
        assertEquals(2, server.requestCount)
        assertEquals("trial_retry_success", VibeVoiceClient.getApiKey(app))
    }

    @Test
    fun trialKeyRequestExhaustsRetriesAndReturnsFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Error 1"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("Error 2"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("Error 3"))

        val result = VibeVoiceClient.requestTrialKeyWithRetry(app, maxAttempts = 3, initialDelayMs = 50L)
        assertTrue(result is TrialResult.Failed)
        assertEquals(3, server.requestCount)
        assertNull(VibeVoiceClient.getApiKey(app))
    }

    @Test
    fun trialKeyRequestDoesNotRetryOnAlreadyUsed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("Conflict"))

        val result = VibeVoiceClient.requestTrialKeyWithRetry(app, maxAttempts = 3, initialDelayMs = 50L)
        assertTrue(result is TrialResult.AlreadyUsed)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun wizardActiveTrackingWorksViaFlagAndPreference() {
        assertFalse(VibeVoiceClient.isSetupWizardActive(app))

        VibeVoiceClient.isWizardActive = true
        assertTrue(VibeVoiceClient.isSetupWizardActive(app))

        VibeVoiceClient.isWizardActive = false
        assertFalse(VibeVoiceClient.isSetupWizardActive(app))

        app.prefs().edit().putBoolean(Settings.PREF_SETUP_WIZARD_ACTIVE, true).apply()
        assertTrue(VibeVoiceClient.isSetupWizardActive(app))
    }

    @Test
    fun triggerTrialRetryUpdatesPreferenceAndFlow() = runBlocking {
        assertEquals(0, app.prefs().getInt(Settings.PREF_TRIAL_RETRY_TRIGGER, 0))

        VibeVoiceClient.triggerTrialRetry(app)
        assertEquals(1, app.prefs().getInt(Settings.PREF_TRIAL_RETRY_TRIGGER, 0))

        VibeVoiceClient.triggerTrialRetry(app)
        assertEquals(2, app.prefs().getInt(Settings.PREF_TRIAL_RETRY_TRIGGER, 0))
    }

    @Test
    fun latinIMEHandleVoiceInputDoesNotLaunchSettingsWhenWizardActive() {
        val latinIME = Robolectric.setupService(LatinIME::class.java)
        // Ensure no API key
        VibeVoiceClient.vibeVoicePrefs(latinIME).edit().clear().apply()
        assertNull(VibeVoiceClient.getApiKey(latinIME))

        // Set wizard active
        VibeVoiceClient.isWizardActive = true
        val retryCountBefore = latinIME.prefs().getInt(Settings.PREF_TRIAL_RETRY_TRIGGER, 0)

        latinIME.handleVoiceInput()

        // Verify settings was NOT launched
        val nextIntent = shadowOf(app).nextStartedActivity
        assertNull("Settings should not be launched while wizard is active", nextIntent)

        // Verify trial retry was triggered
        val retryCountAfter = latinIME.prefs().getInt(Settings.PREF_TRIAL_RETRY_TRIGGER, 0)
        assertEquals(retryCountBefore + 1, retryCountAfter)
    }

    @Test
    fun latinIMEHandleVoiceInputLaunchesSettingsWhenWizardInactive() {
        val latinIME = Robolectric.setupService(LatinIME::class.java)
        // Ensure no API key
        VibeVoiceClient.vibeVoicePrefs(latinIME).edit().clear().apply()
        assertNull(VibeVoiceClient.getApiKey(latinIME))

        // Set wizard inactive
        VibeVoiceClient.isWizardActive = false
        latinIME.prefs().edit().putBoolean(Settings.PREF_SETUP_WIZARD_ACTIVE, false).apply()

        latinIME.handleVoiceInput()

        // Verify settings WAS launched
        val nextIntent = shadowOf(app).nextStartedActivity
        assertNotNull("Settings should be launched when wizard is inactive", nextIntent)
        assertEquals("vibevoice", nextIntent?.getStringExtra("startDestination"))
    }

    @Test
    fun openLinkInBrowserAddsCustomTabExtras() {
        openLinkInBrowser(app, "https://vibevoice.net/link?code=TEST-1234")

        val startedIntent = shadowOf(app).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals(Uri.parse("https://vibevoice.net/link?code=TEST-1234"), startedIntent.data)
        assertTrue(startedIntent.hasExtra("android.support.customtabs.extra.SESSION"))
        assertEquals(0xFF171717.toInt(), startedIntent.getIntExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0))
    }

    @Test
    fun bringAppToForegroundLaunchesSettingsActivityWithReorderFlag() {
        bringAppToForeground(app)

        val startedIntent = shadowOf(app).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(SettingsActivity::class.java.name, startedIntent.component?.className)
        val flags = startedIntent.flags
        assertTrue(flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
