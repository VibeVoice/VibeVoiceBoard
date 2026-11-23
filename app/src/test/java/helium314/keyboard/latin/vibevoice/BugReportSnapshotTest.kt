package helium314.keyboard.latin.vibevoice

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BugReportSnapshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun reportOpensWithTheDeviceSnapshot() {
        val usage = JSONObject().put("minutes_remaining", 0.9).put("email", "someone@example.com")
        val logs = VibeVoiceBugReporter.gatherDiagnostics(context, usage).getString("client_logs")
        println(logs.substringBefore("=== VIBEVOICE DEBUG LOG ===").substringBefore("=== IN-MEMORY"))
        assertTrue(logs.startsWith("=== DEVICE & SETTINGS ==="))
        for (key in listOf("display:", "screen_dp:", "font_scale:", "split_keyboard:", "keyboard:", "account:",
                "usage:", "installer:", "perm_microphone:", "perm_overlay:", "default_ime:")) {
            assertTrue("missing $key", logs.contains("\n$key"))
        }
        assertTrue(logs.contains("minutes_remaining"))
        assertFalse("the usage block must not carry the email", logs.contains("someone@example.com"))
    }
}
