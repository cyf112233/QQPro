package byd.cxkcxkckx.watchdog

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment

/**
 * Comprehensive test dialog for the watchdog system.
 * Provides buttons to trigger various crash and hang scenarios for testing.
 */
class TestFragment : DialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val root = ScrollView(ctx).apply {
            isFillViewport = false
        }
        
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        
        // Title
        content.addView(TextView(ctx).apply {
            text = "Watchdog测试"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })
        
        // Crash Tests
        content.addView(TextView(ctx).apply {
            text = "崩溃测试"
            textSize = 14f
            setTextColor(0xFF_BBBBBB.toInt())
            setPadding(0, 8, 0, 8)
        })
        
        content.addView(createButton("NullPointerException") {
            val x: String? = null
            x!!.length
        })
        
        content.addView(createButton("ArrayIndexOutOfBoundsException") {
            val arr = IntArray(5)
            arr[100].toString()
        })
        
        content.addView(createButton("ArithmeticException") {
            (1 / 0).toString()
        })
        
        content.addView(createButton("ClassCastException") {
            val obj: Any = "string"
            (obj as Int).toString()
        })
        
        content.addView(createButton("RuntimeException") {
            throw RuntimeException("Test RuntimeException")
        })
        
        // Hang Tests
        content.addView(TextView(ctx).apply {
            text = "卡死测试"
            textSize = 14f
            setTextColor(0xFF_BBBBBB.toInt())
            setPadding(0, 16, 0, 8)
        })
        
        content.addView(createButton("卡死 5 秒") {
            val end = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < end) {
                // busy wait
            }
        })
        
        content.addView(createButton("卡死 10 秒") {
            val end = System.currentTimeMillis() + 10000
            while (System.currentTimeMillis() < end) {
                // busy wait
            }
        })
        
        content.addView(createButton("无限循环") {
            @Suppress("ControlFlowWithEmptyBody")
            while (true) {
                // infinite loop
            }
        })
        
        // Thread Tests
        content.addView(TextView(ctx).apply {
            text = "线程相关"
            textSize = 14f
            setTextColor(0xFF_BBBBBB.toInt())
            setPadding(0, 16, 0, 8)
        })
        
        content.addView(createButton("后台线程崩溃") {
            Thread {
                throw RuntimeException("Background thread crash")
            }.start()
        })
        
        content.addView(createButton("延迟 2 秒后崩溃") {
            Thread {
                Thread.sleep(2000)
                throw RuntimeException("Delayed crash")
            }.start()
        })
        
        // Action Tests
        content.addView(TextView(ctx).apply {
            text = "操作"
            textSize = 14f
            setTextColor(0xFF_BBBBBB.toInt())
            setPadding(0, 16, 0, 8)
        })
        
        content.addView(createButton("关闭测试界面") {
            dismiss()
        })
        
        root.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }
    
    private fun createButton(label: String, action: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label
            setOnClickListener {
                try {
                    action()
                } catch (e: Exception) {
                    // Let uncaught exception handler catch it
                    throw e
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}