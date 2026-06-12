package momoi.mod.qqpro.hook.view

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.lib.SwipeBackLayout
import momoi.mod.qqpro.lib.clickable
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.gravity
import momoi.mod.qqpro.lib.margin
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.text
import momoi.mod.qqpro.lib.textColor
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.lib.vertical
import momoi.mod.qqpro.lib.width

/**
 * Hidden test panel for the watchdog (opened by tapping the version line 5× in [AboutFragment]).
 * Each button deliberately crashes or hangs the app so the separate-process crash viewer can be
 * verified. A [MyDialogFragment] so it gets the project's swipe-to-dismiss back gesture.
 */
class WatchdogTestFragment : MyDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = inflater.context
        val root = LinearLayout(ctx)
            .vertical()
            .padding(left = 16.dp, top = 14.dp, right = 16.dp, bottom = 14.dp)
        root.setBackgroundColor(0xF0_121212.toInt())

        val scroll = ScrollView(ctx).apply { isFillViewport = false }
        val column = LinearLayout(ctx).vertical()
        column.content {
            add<TextView>()
                .text("Watchdog 测试")
                .textSize(15f)
                .textColor(0xFF_FFFFFF)
                .gravity(Gravity.CENTER)
                .padding(bottom = 12.dp)

            section("崩溃测试")
            button("空指针 (NPE)") { val s: String? = null; s!!.length }
            button("数组越界") { IntArray(1)[5].toString() }
            button("除以零") { (1 / (System.currentTimeMillis() - System.currentTimeMillis()).toInt()).toString() }
            button("抛出异常") { throw RuntimeException("测试崩溃") }
            button("后台线程崩溃") { Thread { throw RuntimeException("后台线程测试崩溃") }.start() }

            section("卡死测试")
            button("卡死 10 秒") {
                val end = System.currentTimeMillis() + 10_000
                @Suppress("ControlFlowWithEmptyBody")
                while (System.currentTimeMillis() < end) { }
            }

            section("操作")
            button("关闭") { dismiss() }
        }
        scroll.addView(column, ViewGroup.LayoutParams(FILL, WRAP))
        root.addView(scroll, LinearLayout.LayoutParams(FILL, FILL))

        return SwipeBackLayout(ctx).apply {
            addView(root, FILL, FILL)
            onSwipeBack = { dismiss() }
        }
    }

    private fun momoi.mod.qqpro.lib.LinearScope.section(title: String) {
        add<TextView>()
            .text(title)
            .textSize(11f)
            .textColor(0xFF_4FC3F7)
            .padding(top = 12.dp, bottom = 6.dp)
    }

    private fun momoi.mod.qqpro.lib.LinearScope.button(label: String, action: () -> Unit) {
        add<TextView>()
            .text(label)
            .textSize(13f)
            .textColor(0xFF_FFFFFF)
            .gravity(Gravity.CENTER)
            .width(FILL)
            .padding(top = 9.dp, bottom = 9.dp)
            .apply {
                background = GradientDrawable().apply {
                    setColor(0xFF_2196F3.toInt())
                    cornerRadius = 18.dp.toFloat()
                }
            }
            .margin(top = 6.dp)
            // Run after the dialog has handled the click so the trigger is realistic.
            .clickable { Handler(Looper.getMainLooper()).post { action() } }
    }
}
