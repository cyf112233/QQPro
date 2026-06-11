package byd.cxkcxkckx.watchdog;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/**
 * Simple watchdog that catches uncaught exceptions and shows a dialog allowing the user
 * to copy the stack trace.
 */
public class CrashApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
        // Start hang watcher to detect UI freezes
        new HangWatcher(this).start();
    }
}

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
        try {
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
        } catch (Exception ex) {
            Log.e("CrashHandler", "Failed to show crash dialog", ex);
        }
        // Give UI time to show dialog before terminating
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {}
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        }
    }
}
