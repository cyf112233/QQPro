package byd.cxkcxkckx.watchdog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

/**
 * Simple ANR‑like watcher that detects if the main thread is blocked for more than a
 * configurable threshold and shows the same crash‑report dialog.
 */
public class HangWatcher {
    private static final long CHECK_INTERVAL_MS = 1000; // check every second
    private static final long THRESHOLD_MS = 5000; // consider hung if blocked >5s

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;
    private long lastTick;

    public HangWatcher(Context ctx) {
        this.context = ctx.getApplicationContext();
    }

    public void start() {
        lastTick = System.currentTimeMillis();
        // post a no‑op to update lastTick regularly
        mainHandler.post(tickRunnable);
        // start monitoring thread
        new Thread(monitorRunnable, "HangWatcher").start();
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            lastTick = System.currentTimeMillis();
            mainHandler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    private final Runnable monitorRunnable = new Runnable() {
        @Override
        public void run() {
            while (true) {
                long now = System.currentTimeMillis();
                if (now - lastTick > THRESHOLD_MS) {
                    // main thread appears hung
                    showHangDialog();
                    // after showing, break to avoid spamming
                    break;
                }
                try {
                    Thread.sleep(CHECK_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                }
            }
        }
    };

    private void showHangDialog() {
        try {
            new Handler(Looper.getMainLooper()).post(() -> {
                new AlertDialog.Builder(context)
                        .setTitle("程序卡死")
                        .setMessage("程序响应超时，请复制报告给开发者。")
                        .setPositiveButton("复制报告", (d, w) -> {
                            // Simple report without stack trace
                            android.content.ClipboardManager cm =
                                    (android.content.ClipboardManager) context.getSystemService(android.content.ClipboardManager.class);
                            android.content.ClipData clip = android.content.ClipData.newPlainText("Hang Report", "App hung for >" + THRESHOLD_MS + "ms");
                            cm.setPrimaryClip(clip);
                        })
                        .setNegativeButton("关闭", null)
                        .setCancelable(false)
                        .show();
            });
        } catch (Exception e) {
            Log.e("HangWatcher", "Failed to show hang dialog", e);
        }
    }
}
