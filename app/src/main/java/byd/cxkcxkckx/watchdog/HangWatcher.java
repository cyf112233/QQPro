package byd.cxkcxkckx.watchdog;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * ANR‑like watcher that detects main thread stalls and shows/saves a structured report.
 */
public class HangWatcher {
    private static final long CHECK_INTERVAL_MS = 1000; // check every second
    private static final long THRESHOLD_MS = 5000; // consider hung if blocked >5s

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;
    private long lastTick;
    private volatile boolean triggered = false;

    public HangWatcher(Context ctx) {
        this.context = ctx.getApplicationContext();
    }

    public void start() {
        lastTick = System.currentTimeMillis();
        mainHandler.post(tickRunnable);
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
            while (!triggered) {
                long now = System.currentTimeMillis();
                if (now - lastTick > THRESHOLD_MS) {
                    triggered = true;
                    onHangDetected(now - lastTick);
                    break;
                }
                try {
                    Thread.sleep(CHECK_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                }
            }
        }
    };

    private void onHangDetected(long durationMs) {
        final String report = buildReport(durationMs);
        // Save structured report for next launch & immediate sharing
        try {
            SharedPreferences sp = context.getSharedPreferences(CrashApplication.PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(CrashApplication.KEY_CRASH_REPORT, report).apply();
        } catch (Exception ignored) {}

        // Show dialog on main thread
        mainHandler.post(() -> {
            try {
                JSONObject json = new JSONObject(report);
                String summary = String.format("应用卡死 %d ms。\n线程: %s", durationMs, json.optString("thread", ""));
                new AlertDialog.Builder(context)
                        .setTitle("程序卡死")
                        .setMessage(summary + "\n\n点击复制或分享报告给开发者。")
                        .setPositiveButton("复制报告", (d, w) -> {
                            ClipboardManager cm = (ClipboardManager) context.getSystemService(ClipboardManager.class);
                            ClipData clip = ClipData.newPlainText("Hang Report", report);
                            cm.setPrimaryClip(clip);
                        })
                        .setNeutralButton("分享", (d, w) -> {
                            Intent share = new Intent(Intent.ACTION_SEND);
                            share.setType("text/plain");
                            share.putExtra(Intent.EXTRA_TEXT, report);
                            share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(share);
                        })
                        .setNegativeButton("关闭", null)
                        .setCancelable(false)
                        .show();
            } catch (JSONException e) {
                Log.e("HangWatcher", "show dialog failed", e);
            }
        });
    }

    private String buildReport(long durationMs) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "hang");
            json.put("timestamp", System.currentTimeMillis());
            json.put("duration_ms", durationMs);
            Thread main = Looper.getMainLooper().getThread();
            json.put("thread", main != null ? main.getName() : "main");
            // include all thread stack traces
            JSONArray threads = new JSONArray();
            Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
            for (Map.Entry<Thread, StackTraceElement[]> e : all.entrySet()) {
                JSONObject t = new JSONObject();
                t.put("name", e.getKey().getName());
                JSONArray st = new JSONArray();
                for (StackTraceElement el : e.getValue()) {
                    st.put(el.toString());
                }
                t.put("stack", st);
                threads.put(t);
            }
            json.put("threads", threads);
            JSONObject device = new JSONObject();
            device.put("model", Build.MODEL);
            device.put("sdk", Build.VERSION.SDK_INT);
            device.put("manufacturer", Build.MANUFACTURER);
            json.put("device", device);
        } catch (JSONException ignored) {}
        return json.toString();
    }
}
