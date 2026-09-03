package helium314.keyboard.latin.vibevoice;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VibeVoiceDebugLogger {
    private static final String TAG = "VibeVoiceDebug";
    private static final String FILENAME = "vibevoice_debug.log";
    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB
    /** One thread, so entries keep their order without any of them blocking a caller. */
    private static final ExecutorService sWriter = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VibeVoiceDebugLogger");
        t.setDaemon(true);
        return t;
    });

    private static VibeVoiceDebugLogger sInstance;
    private final File mLogFile;
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private VibeVoiceDebugLogger(Context context) {
        mLogFile = new File(context.getFilesDir(), FILENAME);
    }

    public static synchronized void init(Context context) {
        if (sInstance == null) {
            sInstance = new VibeVoiceDebugLogger(context.getApplicationContext());
        }
    }

    public static void log(String message) {
        if (sInstance == null) {
            Log.e(TAG, "Logger not initialized: " + message);
            return;
        }
        sInstance.writeLog(message);
    }

    private void writeLog(String message) {
        String timestamp = mDateFormat.format(new Date());
        String entry = String.format("[%s] %s\n", timestamp, message);
        Log.d(TAG, entry.trim());
        // Off the caller's thread. Every one of these came from the UI thread -- an IME logs from
        // key handling and from voice callbacks -- and each was an open, a write and a close of an
        // unbuffered stream while the keyboard was drawing.
        sWriter.execute(() -> appendEntry(entry));
    }

    private synchronized void appendEntry(String entry) {
        try {
            boolean rotate = mLogFile.length() > MAX_FILE_SIZE;
            if (rotate) {
                // Keep the previous generation. Rotating used to truncate outright, which threw
                // away every line leading up to a failure at exactly the moment a bug report would
                // have wanted them -- the log is 1 MB precisely because that history is the point.
                File previous = new File(mLogFile.getParentFile(), mLogFile.getName() + ".1");
                if (previous.exists() && !previous.delete()) {
                    Log.w(TAG, "Could not remove the previous log generation");
                }
                if (!mLogFile.renameTo(previous)) {
                    Log.w(TAG, "Could not rotate the log; truncating instead");
                }
            }
            try (FileOutputStream fos = new FileOutputStream(mLogFile, !rotate)) {
                if (rotate) fos.write("[LOG ROTATED]\n".getBytes());
                fos.write(entry.getBytes());
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to write log", e);
        }
    }
}
