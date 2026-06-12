package momoi.mod.qqpro.watchdog

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import momoi.mod.qqpro.lib.SwipeBackLayout

/**
 * Standalone crash/hang report viewer. Declared with `android:process=":crash"` so it lives in a
 * separate process and renders even after the main (crashing) process is gone — see [Watchdog].
 *
 * This is NOT an ApkMixin hook, so it may freely use lambdas / anonymous classes. It also avoids
 * the project's `lib` dp helpers (which read `Utils.application`) since that singleton may be
 * uninitialised in the `:crash` process — it converts dp from the local display metrics instead.
 */
class CrashReportActivity : Activity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun sp(view: TextView, value: Float) =
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val kind = intent?.getStringExtra(Watchdog.EXTRA_KIND) ?: Watchdog.KIND_CRASH
        val report = runCatching { Watchdog.reportFile(this).readText() }
            .getOrElse { "(无法读取报告: $it)" }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF_121212.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        root.addView(TextView(this).apply {
            text = if (kind == Watchdog.KIND_HANG) "应用卡死了" else "应用崩溃了"
            setTextColor(Color.WHITE)
            sp(this, 15f)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "请把下面的报告发给开发者"
            setTextColor(0xFF_BBBBBB.toInt())
            sp(this, 10f)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        })

        // Report body fills the remaining space and scrolls.
        val body = TextView(this).apply {
            text = report
            setTextColor(0xFF_DDDDDD.toInt())
            sp(this, 9f)
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0xFF_0A0A0A.toInt())
        }
        root.addView(
            ScrollView(this).apply { addView(body) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        // First row: 复制 / 分享 / 重启
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        fun cell(v: View, left: Int, right: Int) = row.addView(
            v,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = left
                rightMargin = right
            }
        )
        cell(button("复制", 0xFF_2196F3.toInt()) { copyReport(report) }, 0, dp(3))
        cell(button("分享", 0xFF_4CAF50.toInt()) { shareReport(report) }, dp(3), dp(3))
        cell(button("重启", 0xFF_FF9800.toInt()) { restartApp() }, dp(3), 0)
        root.addView(
            row,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        // Second row: 关闭 (full width)
        root.addView(
            button("关闭", 0xFF_555555.toInt()) { finishAndRemoveTask() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) }
        )

        // SwipeBackLayout gives watches without a hardware back button a swipe-to-dismiss gesture.
        setContentView(
            SwipeBackLayout(this).apply {
                addView(
                    root,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                onSwipeBack = { finishAndRemoveTask() }
            }
        )

        // Consume the report so it doesn't resurface on the next normal launch.
        runCatching { Watchdog.reportFile(this).delete() }
    }

    private fun button(label: String, color: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            sp(this, 12f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(9), dp(8), dp(9))
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { onClick() }
        }

    private fun copyReport(report: String) {
        runCatching {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("crash report", report))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        }.onFailure { Log.e("Watchdog", "copy failed", it) }
    }

    private fun shareReport(report: String) {
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
            }
            startActivity(
                Intent.createChooser(send, "分享报告").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.e("Watchdog", "share failed", it) }
    }

    private fun restartApp() {
        runCatching {
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(it)
            }
        }.onFailure { Log.e("Watchdog", "restart failed", it) }
        finishAndRemoveTask()
    }
}
