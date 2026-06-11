package byd.cxkcxkckx.watchdog

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment

/**
 * A dialog fragment used for testing the watchdog. It provides two buttons:
 *  - "Crash App" throws a RuntimeException.
 *  - "Hang UI (5s)" blocks the UI thread for 5 seconds.
 * This replaces the previously used TestActivity and does not require a manifest entry.
 */
class TestFragment : DialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        val crashBtn = Button(ctx).apply { text = "Crash App" }
        crashBtn.setOnClickListener {
            throw RuntimeException("Test crash triggered by user")
        }

        val hangBtn = Button(ctx).apply { text = "Hang UI (5s)" }
        hangBtn.setOnClickListener {
            val end = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < end) {
                // busy wait to simulate freeze
            }
        }

        root.addView(crashBtn)
        root.addView(hangBtn)
        return root
    }

    override fun onStart() {
        super.onStart()
        // Make dialog fill the screen for easier testing
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
