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
 * Reports are shown on the SECOND launch after a crash/hang (not immediately).
 */
public class CrashApplication extends Application {
    public static final String PREF_NAME = "watchdog_prefs";
    public static final String KEY_CRASH_REPORT = "crash_report";
    public static final String KEY_REPORT_SHOWN = "report_shown";

    @Override
    public void onCreate() {
        super.onCreate();
        // Install the uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
        // Start the ANR-like watcher
        new HangWatcher(this).start();
        
        // Check for a saved crash/hang report from the previous run
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String savedReport = sp.getString(KEY_CRASH_REPORT, null);
        boolean reportShown = sp.getBoolean(KEY_REPORT_SHOWN, false);
        
        // Only show the report on the SECOND launch (when reportShown is already true)
        if (savedReport != null && reportShown) {
            final String report = savedReport;
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
            sp.edit().remove(KEY_CRASH_REPORT).remove(KEY_REPORT_SHOWN).commit();
        } else if (savedReport != null && !reportShown) {
            // Mark that we've seen the report once (for next launch)
            sp.edit().putBoolean(KEY_REPORT_SHOWN, true).commit();
        }
    }
}

/**
 * Handler for uncaught exceptions. Persists the stack trace for viewing on the next-next launch.
 */
class CrashHandler implements Thread.UncaughtExceptionHandler {
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    CrashHandler(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        final String stackTrace = Log.getStackTraceString(e);
        
        // Store the report synchronously (will be shown on second launch)
        try {
            SharedPreferences sp = context.getSharedPreferences(CrashApplication.PREF_NAME, Context.MODE_PRIVATE);
            sp.edit()
                    .putString(CrashApplication.KEY_CRASH_REPORT, stackTrace)
                    .putBoolean(CrashApplication.KEY_REPORT_SHOWN, false)
                    .commit();
        } catch (Exception ignored) {
        }
        
        // Terminate without showing a dialog
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        }
    }
}