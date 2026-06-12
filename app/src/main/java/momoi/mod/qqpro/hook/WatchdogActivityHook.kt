package momoi.mod.qqpro.hook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.lib.dp
import byd.cxkcxkckx.watchdog.CrashHandler
import byd.cxkcxkckx.watchdog.HangWatcher
import byd.cxkcxkckx.watchdog.CrashApplication

/**
 * Hook into com.tencent.qqnt.watch.mainframe.MainActivity to:
 * 1. Start the watchdog system on app launch
 * 2. Check and display crash/hang reports from previous sessions
 */
@Mixin
class WatchdogActivityHook : com.tencent.qqnt.watch.mainframe.MainActivity() {
    companion object {
        private const val TAG = "WatchdogHook"
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
        val sp = getSharedPreferences(CrashApplication.PREF_NAME, MODE_PRIVATE)
        val savedReport = sp.getString(CrashApplication.KEY_CRASH_REPORT, null)
        
        if (savedReport != null) {
            Utils.log("$TAG: Found saved report from previous crash/hang, showing dialog")
            val report = savedReport
            // Show modern dialog on the main thread
            runOnUiThread {
                val root = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(0xF0_1F1F1F.toInt())
                    setPadding(20.dp, 20.dp, 20.dp, 20.dp)
                }
                
                // Title
                root.addView(TextView(this).apply {
                    text = "上次使用时崩溃了"
                    textSize = 18f
                    setTextColor(0xFF_FFFFFF.toInt())
                    setPadding(0, 0, 0, 12.dp)
                })
                
                // Message
                root.addView(TextView(this).apply {
                    text = "请把以下报告发送给开发者"
                    textSize = 14f
                    setTextColor(0xFF_BBBBBB.toInt())
                    setPadding(0, 0, 0, 16.dp)
                })
                
                // Report scroll
                val scroll = ScrollView(this).apply {
                    isFillViewport = false
                }
                scroll.addView(TextView(this).apply {
                    text = report
                    textSize = 12f
                    setTextColor(0xFF_DDDDDD.toInt())
                    setPadding(8.dp, 8.dp, 8.dp, 8.dp)
                    setBackgroundColor(0xFF_0D0D0D.toInt())
                })
                root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150.dp))
                
                // Buttons container
                val btnContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 16.dp, 0, 0)
                }
                
                // Copy button
                btnContainer.addView(Button(this).apply {
                    text = "复制报告"
                    setOnClickListener {
                        val cm = getSystemService(ClipboardManager::class.java)
                        val clip = ClipData.newPlainText("Crash Report", report)
                        cm.setPrimaryClip(clip)
                        Toast.makeText(this@WatchdogActivityHook, "报告已复制", Toast.LENGTH_SHORT).show()
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = 4.dp
                })
                
                // Share button
                btnContainer.addView(Button(this).apply {
                    text = "分享"
                    setOnClickListener {
                        val share = Intent(Intent.ACTION_SEND)
                        share.type = "text/plain"
                        share.putExtra(Intent.EXTRA_TEXT, report)
                        share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(share)
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = 4.dp
                })
                
                root.addView(btnContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                
                // Create and show dialog
                val dialog = android.app.AlertDialog.Builder(this)
                        .setView(root)
                        .setCancelable(false)
                        .create()
                dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF_1F1F1F.toInt()))
                dialog.show()
            }
            
            // Clear the report after showing
            sp.edit().remove(CrashApplication.KEY_CRASH_REPORT).apply()
        }
    }
}