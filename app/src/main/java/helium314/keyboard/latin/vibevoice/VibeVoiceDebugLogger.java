package helium314.keyboard.latin.vibevoice;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VibeVoiceDebugLogger {
    private static final String TAG = "VibeVoiceDebug";
    private static final String FILENAME = "vibevoice_debug.log";
    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB

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

    private synchronized void writeLog(String message) {
        String timestamp = mDateFormat.format(new Date());
        String entry = String.format("[%s] %s\n", timestamp, message);
        Log.d(TAG, entry.trim());

        try {
            boolean rotate = mLogFile.length() > MAX_FILE_SIZE;
            try (FileOutputStream fos = new FileOutputStream(mLogFile, !rotate)) {
                if (rotate) fos.write("[LOG ROTATED]\n".getBytes());
                fos.write(entry.getBytes());
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to write log", e);
        }
    }
}
