package helium314.keyboard.latin.vibevoice

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hands the debug log out as a file.
 *
 * The bug reporter sends a log to the server, which is the right road for a report from a stranger's
 * phone. It is the wrong one for the phone this is developed on: the log has to travel back through
 * an export job to be read. This is the short way -- the log as a file, in whatever app the share
 * sheet offers, mail or a chat or Files.
 */
object VibeVoiceLogShare {

    /**
     * Copies the log (the rotated generation first, so it reads in order) into the cache and returns
     * a chooser for it, or null when there is nothing to send.
     */
    fun createShareIntent(context: Context): Intent? {
        val staged = stage(context) ?: return null
        val uri = FileProvider.getUriForFile(context, context.getString(R.string.log_provider_authority), staged)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, staged.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, staged.name).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Where the live log sits, for the line under the button. Null before the logger is initialized. */
    fun logLocation(): String? = VibeVoiceDebugLogger.currentFile()?.absolutePath

    private fun stage(context: Context): File? {
        val current = VibeVoiceDebugLogger.currentFile() ?: return null
        val previous = VibeVoiceDebugLogger.previousFile()
        if (!current.exists() && previous == null) return null

        val dir = File(context.cacheDir, "logs")
        if (!dir.isDirectory && !dir.mkdirs()) return null
        // One file per share, and the last one goes: a stale copy in the cache is a log somebody
        // sends a week later believing it is today's.
        dir.listFiles()?.forEach { it.delete() }

        val stamp = SimpleDateFormat("yyyyMMdd'T'HHmm", Locale.US).format(Date())
        val target = File(dir, "vibevoice_${BuildConfig.VERSION_NAME}_$stamp.log")
        target.outputStream().use { out ->
            previous?.inputStream()?.use { it.copyTo(out) }
            if (current.exists()) current.inputStream().use { it.copyTo(out) }
        }
        return if (target.length() > 0) target else null
    }
}
