package byd.cxkcxkckx.watchdog;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/**
 * Application class that installs two watchdog components:
 * 1. CrashHandler – catches uncaught exceptions and stores the stack trace.
 * 2. HangWatcher – monitors the main thread for stalls (>5 s) and stores hang reports.
 * Both save reports to SharedPreferences synchronously.
 * Reports are shown on the FIRST launch after a crash/hang.
 */
public class CrashApplication extends Application {
    public static final String PREF_NAME = "watchdog_prefs";
    public static final String KEY_CRASH_REPORT = "crash_report";
    private static final String TAG = "WatchdogApp";

    @Override
    public void onCreate() {
        super.onCreate();
        // Install the uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
        // Start the ANR-like watcher
        new HangWatcher(this).start();
        
        // Check for a saved crash/hang report from the previous run
        SharedPreferences sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedReport = sp.getString(KEY_CRASH_REPORT, null);
        
        Log.d(TAG, "onCreate: savedReport=" + (savedReport != null ? "exists" : "null"));
        
        // Show the report immediately on any launch if it exists
        if (savedReport != null) {
            final String report = savedReport;
            Log.d(TAG, "Found saved report, showing dialog");
            new Handler(Looper.getMainLooper()).post(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("上次使用时崩溃了")
                        .setMessage("请把以下报告发送给开发者:\n\n" + report)
                        .setPositiveButton("复制报告", (d, w) -> {
                            ClipboardManager cm = (ClipboardManager) getSystemService(ClipboardManager.class);
                            ClipData clip = ClipData.newPlainText("Previous Report", report);
                            cm.setPrimaryClip(clip);
                            Toast.makeText(this, "报告已复制", Toast.LENGTH_SHORT).show();
                        })
                        .setNeutralButton("分享", (d, w) -> {
                            Intent share = new Intent(Intent.ACTION_SEND);
                            share.setType("text/plain");
                            share.putExtra(Intent.EXTRA_TEXT, report);
                            share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(share);
                        })
                        .setNegativeButton("关闭", null)
                        .setCancelable(false)
                        .show();
            });
            // Clear report after showing
            sp.edit().remove(KEY_CRASH_REPORT).apply();
        }
    }
}

/**
 * Handler for uncaught exceptions. Persists the stack trace for viewing on the next launch.
 */
class CrashHandler implements Thread.UncaughtExceptionHandler {
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;
    private static final String TAG = "CrashHandler";

    CrashHandler(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        final String stackTrace = Log.getStackTraceString(e);
        Log.e(TAG, "Uncaught exception: " + e.getClass().getSimpleName());
        
        // Store the report synchronously (will be shown on next launch)
        try {
            SharedPreferences sp = context.getSharedPreferences(CrashApplication.PREF_NAME, Context.MODE_PRIVATE);
            Log.d(TAG, "Saving crash report to SharedPreferences");
            sp.edit()
                    .putString(CrashApplication.KEY_CRASH_REPORT, stackTrace)
                    .commit();
            Log.d(TAG, "Crash report saved successfully");
        } catch (Exception ex) {
            Log.e(TAG, "Failed to save crash report", ex);
        }
        
        // Terminate without showing a dialog
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        }
    }
}