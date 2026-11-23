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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    fun wizardActiveFlagIsInMemoryOnly() {
        assertFalse(VibeVoiceClient.isSetupWizardActive(app))

        VibeVoiceClient.setSetupWizardActive(true)
        assertTrue(VibeVoiceClient.isSetupWizardActive(app))

        VibeVoiceClient.setSetupWizardActive(false)
        assertFalse(VibeVoiceClient.isSetupWizardActive(app))

        // a value left behind by a killed process in the old persisted flag must not count
        app.prefs().edit().putBoolean(Settings.PREF_SETUP_WIZARD_ACTIVE, true).apply()
        assertFalse(VibeVoiceClient.isSetupWizardActive(app))
    }

    @Test
    fun triggerTrialRetryEmitsToCollectorAndReportsDelivery() = runBlocking {
        val received = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5000) { VibeVoiceClient.trialRetryFlow.first() }
        }
        assertEquals(0, app.prefs().getInt(Settings.PREF_TRIAL_RETRY_TRIGGER, 0))

        assertTrue(VibeVoiceClient.triggerTrialRetry(app))

        assertEquals(Unit, received.await())
        assertEquals(1, app.prefs().getInt(Settings.PREF_TRIAL_RETRY_TRIGGER, 0))
    }

    @Test
    fun triggerTrialRetryWithoutCollectorReportsNoDelivery() {
        assertFalse(VibeVoiceClient.triggerTrialRetry(app))
        assertEquals(0, app.prefs().getInt(Settings.PREF_TRIAL_RETRY_TRIGGER, 0))
    }

    @Test
    fun latinIMEHandleVoiceInputDoesNotLaunchSettingsWhenWizardActive() = runBlocking {
        val latinIME = Robolectric.setupService(LatinIME::class.java)
        VibeVoiceClient.vibeVoicePrefs(latinIME).edit().clear().apply()
        assertNull(VibeVoiceClient.getApiKey(latinIME))
        VibeVoiceClient.setSetupWizardActive(true)
        // the wizard listens for the retry request
        val received = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5000) { VibeVoiceClient.trialRetryFlow.first() }
        }

        latinIME.handleVoiceInput()

        assertNull("Settings should not be launched while wizard is active", shadowOf(app).nextStartedActivity)
        assertEquals(Unit, received.await())
    }

    @Test
    fun latinIMEHandleVoiceInputLaunchesSettingsWhenWizardHasNoCollector() {
        val latinIME = Robolectric.setupService(LatinIME::class.java)
        VibeVoiceClient.vibeVoicePrefs(latinIME).edit().clear().apply()
        VibeVoiceClient.setSetupWizardActive(true) // flag set, but nobody collects the retry flow

        latinIME.handleVoiceInput()

        val nextIntent = shadowOf(app).nextStartedActivity
        assertNotNull("Mic key must not be swallowed", nextIntent)
        assertEquals("vibevoice", nextIntent?.getStringExtra("startDestination"))
    }

    @Test
    fun latinIMEHandleVoiceInputLaunchesSettingsWhenTrialSpentInWizard() = runBlocking {
        val latinIME = Robolectric.setupService(LatinIME::class.java)
        VibeVoiceClient.vibeVoicePrefs(latinIME).edit().clear().apply()
        VibeVoiceClient.markTrialSpent(latinIME)
        VibeVoiceClient.setSetupWizardActive(true)
        val collector = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            VibeVoiceClient.trialRetryFlow.first()
        }

        latinIME.handleVoiceInput()

        assertNotNull(shadowOf(app).nextStartedActivity)
        assertFalse(collector.isCompleted)
        collector.cancel()
    }
    @Test
    fun latinIMEHandleVoiceInputLaunchesSettingsWhenWizardInactive() {
        val latinIME = Robolectric.setupService(LatinIME::class.java)
        // Ensure no API key
        VibeVoiceClient.vibeVoicePrefs(latinIME).edit().clear().apply()
        assertNull(VibeVoiceClient.getApiKey(latinIME))

        // Set wizard inactive
        VibeVoiceClient.setSetupWizardActive(false)

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
