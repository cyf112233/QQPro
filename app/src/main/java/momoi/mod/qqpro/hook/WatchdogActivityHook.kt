package momoi.mod.qqpro.hook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import momoi.mod.qqpro.util.Utils
import byd.cxkcxkckx.watchdog.CrashHandler
import byd.cxkcxkckx.watchdog.HangWatcher

/**
 * Hook into com.tencent.qqnt.watch.mainframe.MainActivity to:
 * 1. Start the watchdog system on app launch
 * 2. Check and display crash/hang reports from previous sessions
 */
@Mixin
class WatchdogActivityHook : com.tencent.qqnt.watch.mainframe.MainActivity() {
    companion object {
        private const val TAG = "WatchdogHook"
        private const val PREF_NAME = "watchdog_prefs"
        private const val KEY_CRASH_REPORT = "crash_report"
        private var watchdogStarted = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start watchdog system once
        if (!watchdogStarted) {
            watchdogStarted = true
            Utils.log("$TAG: Starting watchdog system")
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
            HangWatcher(this).start()
        }
        
        // Check for saved crash/hang report from previous session
        val sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val savedReport = sp.getString(KEY_CRASH_REPORT, null)
        
        if (savedReport != null) {
            Utils.log("$TAG: Found saved report from previous crash/hang, showing dialog")
            val report = savedReport
            // Show dialog on the main thread
            runOnUiThread {
                AlertDialog.Builder(this)
                        .setTitle("上次使用时崩溃了")
                        .setMessage("请把以下报告发送给开发者:\n\n$report")
                        .setPositiveButton("复制报告") { d, w ->
                            val cm = getSystemService(ClipboardManager::class.java)
                            val clip = ClipData.newPlainText("Crash Report", report)
                            cm.setPrimaryClip(clip)
                            Toast.makeText(this, "报告已复制", Toast.LENGTH_SHORT).show()
                        }
                        .setNeutralButton("分享") { d, w ->
                            val share = Intent(Intent.ACTION_SEND)
                            share.type = "text/plain"
                            share.putExtra(Intent.EXTRA_TEXT, report)
                            share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(share)
                        }
                        .setNegativeButton("关闭", null)
                        .setCancelable(false)
                        .show()
            }
            
            // Clear the report after showing
            sp.edit().remove(KEY_CRASH_REPORT).apply()
        }
    }
}