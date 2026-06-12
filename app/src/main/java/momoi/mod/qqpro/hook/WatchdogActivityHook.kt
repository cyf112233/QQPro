package momoi.mod.qqpro.hook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.tencent.watch.aio_impl.ui.frames.MainActivity
import momoi.mod.qqpro.util.Utils
import android.content.SharedPreferences

/**
 * Hook into MainActivity onCreate to check for watchdog crash/hang reports
 * and display them immediately when the app starts.
 */
@Mixin
class WatchdogActivityHook : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check for saved crash/hang report
        val sp = getSharedPreferences("watchdog_prefs", MODE_PRIVATE)
        val savedReport = sp.getString("crash_report", null)
        
        if (savedReport != null) {
            Utils.log("WatchdogActivityHook: Found saved report, showing dialog")
            val report = savedReport
            // Show dialog immediately
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
            
            // Clear the report after showing
            sp.edit().remove("crash_report").apply()
        }
    }
}