package helium314.keyboard.latin.vibevoice

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import helium314.keyboard.latin.R

/**
 * Asks for RECORD_AUDIO on behalf of the keyboard, which cannot ask for itself.
 *
 * WHY THERE IS A DIALOG NOW
 *
 * The setup wizard states what leaves the device before it asks for the microphone, and for a while
 * that was treated as the disclosure. It is not, because the wizard is not the only road here:
 * SettingsActivity only shows it while the keyboard is not yet enabled or not yet current, so
 * somebody who enables and selects VibeVoice Keyboard from Android's own settings never sees it.
 * Their first microphone prompt was this activity's bare system dialog, with nothing anywhere saying
 * that audio is sent off the device.
 *
 * Play requires the disclosure in the foreground, before the request, on every path that reaches it.
 * So it lives here as well -- and here it is the more important of the two, because this is the path
 * a user takes without having read anything.
 */
class PermissionActivity : Activity() {
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                finish()
                return
            }
            dialog = AlertDialog.Builder(this)
                .setTitle(R.string.vibevoice_mic_disclosure_title)
                .setMessage(R.string.setup_mic_disclosure)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
                }
                // Dismissing is a decision, not an accident: closing without granting must not leave
                // an invisible activity behind waiting for a result that will never come.
                .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Permission Error: " + e.message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }
}
