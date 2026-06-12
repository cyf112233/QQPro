package momoi.mod.qqpro.hook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TabHost
import android.widget.TabWidget
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
 * 2. Check and display crash/hang reports from previous sessions with tabbed interface
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
            // Show dialog with tabbed interface
            runOnUiThread {
                val root = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(0xF0_1F1F1F.toInt())
                    setPadding(0, 0, 0, 0)
                }
                
                // TabHost for tabbed interface
                val tabHost = TabHost(this)
                tabHost.setBackgroundColor(0xF0_1F1F1F.toInt())
                
                // TabWidget (tab buttons)
                val tabWidget = TabWidget(this).apply {
                    setBackgroundColor(0xFF_0D0D0D.toInt())
                }
                tabHost.addView(tabWidget, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp))
                
                // FrameLayout for tab content
                val frameLayout = android.widget.FrameLayout(this)
                frameLayout.setBackgroundColor(0xF0_1F1F1F.toInt())
                tabHost.addView(frameLayout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                
                tabHost.setup()
                
                // Tab 1: Report
                val reportTab = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(12.dp, 12.dp, 12.dp, 12.dp)
                }
                
                reportTab.addView(TextView(this).apply {
                    text = "上次使用时崩溃了"
                    textSize = 16f
                    setTextColor(0xFF_FFFFFF.toInt())
                    setPadding(0, 0, 0, 8.dp)
                })
                
                reportTab.addView(TextView(this).apply {
                    text = "请把以下报告发送给开发者"
                    textSize = 13f
                    setTextColor(0xFF_BBBBBB.toInt())
                    setPadding(0, 0, 0, 12.dp)
                })
                
                val scroll = ScrollView(this).apply {
                    isFillViewport = false
                }
                scroll.addView(TextView(this).apply {
                    text = report
                    textSize = 11f
                    setTextColor(0xFF_DDDDDD.toInt())
                    setPadding(8.dp, 8.dp, 8.dp, 8.dp)
                    setBackgroundColor(0xFF_0D0D0D.toInt())
                })
                
                val displayMetrics = resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels
                val maxScrollHeight = (screenHeight * 0.35).toInt()
                val reportHeight = maxOf(100.dp, minOf(maxScrollHeight, 180.dp))
                
                reportTab.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, reportHeight))
                
                // Tab 2: Help
                val helpTab = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(12.dp, 12.dp, 12.dp, 12.dp)
                }
                
                val helpScroll = ScrollView(this).apply {
                    isFillViewport = false
                }
                helpScroll.addView(TextView(this).apply {
                    text = "如何处理:\n\n1. 点击\"复制报告\"将错误信息复制到剪贴板\n\n2. 点击\"分享\"通过其他应用发送报告\n\n3. 将报告发送给开发者以获得支持\n\n这个报告包含了应用崩溃时的详细信息，有助于开发者快速定位和解决问题。"
                    textSize = 13f
                    setTextColor(0xFF_DDDDDD.toInt())
                    setPadding(12.dp, 12.dp, 12.dp, 12.dp)
                    setBackgroundColor(0xFF_0D0D0D.toInt())
                })
                helpTab.addView(helpScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                
                // Add tabs to TabHost
                tabHost.addTab(tabHost.newTabSpec("report")
                        .setIndicator(createTabIndicator("报告"))
                        .setContent { reportTab })
                
                tabHost.addTab(tabHost.newTabSpec("help")
                        .setIndicator(createTabIndicator("帮助"))
                        .setContent { helpTab })
                
                root.addView(tabHost, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                
                // Button container
                val btnContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundColor(0xFF_0D0D0D.toInt())
                    setPadding(8.dp, 8.dp, 8.dp, 8.dp)
                }
                
                // Copy button
                btnContainer.addView(Button(this).apply {
                    text = "复制"
                    setTextColor(0xFF_FFFFFF.toInt())
                    setBackgroundColor(0xFF_2196F3.toInt())
                    setPadding(6.dp, 6.dp, 6.dp, 6.dp)
                    textSize = 12f
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
                    setTextColor(0xFF_FFFFFF.toInt())
                    setBackgroundColor(0xFF_4CAF50.toInt())
                    setPadding(6.dp, 6.dp, 6.dp, 6.dp)
                    textSize = 12f
                    setOnClickListener {
                        val share = Intent(Intent.ACTION_SEND)
                        share.type = "text/plain"
                        share.putExtra(Intent.EXTRA_TEXT, report)
                        share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(share)
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 4.dp
                    marginEnd = 4.dp
                })
                
                root.addView(btnContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                
                // Create and show dialog
                val dialog = android.app.AlertDialog.Builder(this)
                        .setView(root)
                        .setCancelable(false)
                        .create()
                
                // Exit button (add after dialog creation so it can reference the dialog)
                btnContainer.addView(Button(this).apply {
                    text = "退出"
                    setTextColor(0xFF_FFFFFF.toInt())
                    setBackgroundColor(0xFF_F44336.toInt())
                    setPadding(6.dp, 6.dp, 6.dp, 6.dp)
                    textSize = 12f
                    setOnClickListener {
                        dialog.dismiss()
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 4.dp
                })
                
                // Set responsive dialog dimensions
                dialog.window?.apply {
                    setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF_1F1F1F.toInt()))
                    val screenWidth = resources.displayMetrics.widthPixels
                    val dialogWidth = minOf(screenWidth - 32.dp, 600.dp)
                    setLayout(dialogWidth, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
                }
                dialog.show()
            }
            
            // Clear the report after showing
            sp.edit().remove(CrashApplication.KEY_CRASH_REPORT).apply()
        }
    }
    
    private fun createTabIndicator(label: String): android.view.View {
        return TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(0xFF_BBBBBB.toInt())
            gravity = Gravity.CENTER
            setPadding(12.dp, 8.dp, 12.dp, 8.dp)
            setBackgroundColor(0xFF_0D0D0D.toInt())
        }
    }
}