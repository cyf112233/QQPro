package byd.cxkcxkckx.watchdog;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/**
 * Application class that installs two watchdog components:
 * 1. CrashHandler – catches uncaught exceptions, shows a dialog with a copy‑button and stores the
 *    stack trace in SharedPreferences so it can be presented on the next launch.
 * 2. HangWatcher – monitors the main thread for long stalls (>5 s) and shows a similar dialog.
 */
public class CrashApplication extends Application {
    public static final String PREF_NAME = "watchdog_prefs";
    public static final String KEY_CRASH_REPORT = "crash_report";

    @Override
    public void onCreate() {
        super.onCreate();
        // Install the uncaught‑exception handler
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
        // Start the ANR‑like watcher
        new HangWatcher(this).start();
        // If there is a saved crash report from the previous run, show it to the user
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String savedReport = sp.getString(KEY_CRASH_REPORT, null);
        if (savedReport != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("上次使用时崩溃了")
                        .setMessage("请把以下报告发送给开发者:\n\n" + savedReport)
                        .setPositiveButton("复制报告", (d, w) -> {
                            ClipboardManager cm = (ClipboardManager) getSystemService(ClipboardManager.class);
                            ClipData clip = ClipData.newPlainText("Previous Crash Report", savedReport);
                            cm.setPrimaryClip(clip);
                        })
                        .setNegativeButton("关闭", null)
                        .setCancelable(false)
                        .show();
            });
            // Remove the stored report after showing it
            sp.edit().remove(KEY_CRASH_REPORT).apply();
        }
    }
}

/**
 * Handler for uncaught exceptions. Shows a dialog with a copy‑button and persists the stack trace
 * for the next app launch.
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
        // Show dialog on UI thread
        new Handler(Looper.getMainLooper()).post(() -> {
            new AlertDialog.Builder(context)
                    .setTitle("程序异常崩溃")
                    .setMessage("程序发生错误，即将关闭。\n\n点击复制可将报告复制给开发者。")
                    .setPositiveButton("复制报告", (dialog, which) -> {
                        ClipboardManager cm = (ClipboardManager) context.getSystemService(ClipboardManager.class);
                        ClipData clip = ClipData.newPlainText("Crash Report", stackTrace);
                        cm.setPrimaryClip(clip);
                        Toast.makeText(context, "报告已复制", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("关闭", null)
                    .setCancelable(false)
                    .show();
        });
        // Store the report for the next launch
        try {
            SharedPreferences sp = context.getSharedPreferences(CrashApplication.PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(CrashApplication.KEY_CRASH_REPORT, stackTrace).apply();
        } catch (Exception ignored) {
        }
        // Give UI a moment to show the dialog before terminating the process
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        }
    }
}
