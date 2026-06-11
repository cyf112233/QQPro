package byd.cxkcxkckx.watchdog;

import android.content.SharedPreferences;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * ANR-like watcher that detects main thread stalls (>5s) and persists a hang report
 * for viewing on the second app launch after the hang is detected.
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
        final String report = String.format("应用卡死 %d ms\n线程堆栈:\n%s", durationMs, buildThreadDump());
        
        // Save report synchronously (will be shown on second launch)
        try {
            SharedPreferences sp = context.getSharedPreferences(CrashApplication.PREF_NAME, Context.MODE_PRIVATE);
            sp.edit()
                    .putString(CrashApplication.KEY_CRASH_REPORT, report)
                    .putBoolean(CrashApplication.KEY_REPORT_SHOWN, false)
                    .commit();
        } catch (Exception ignored) {}
        
        // No dialog is shown; report will appear on second launch
    }

    private String buildThreadDump() {
        StringBuilder sb = new StringBuilder();
        java.util.Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        for (java.util.Map.Entry<Thread, StackTraceElement[]> e : all.entrySet()) {
            sb.append("Thread: ").append(e.getKey().getName()).append("\n");
            for (StackTraceElement el : e.getValue()) {
                sb.append("  at ").append(el.toString()).append("\n");
            }
        }
        return sb.toString();
    }
}