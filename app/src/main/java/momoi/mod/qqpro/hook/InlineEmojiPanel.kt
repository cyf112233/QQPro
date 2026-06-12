package momoi.mod.qqpro.hook

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import com.tencent.mobileqq.text.QQText
import com.tencent.qqnt.emotion.utils.QQSysFaceUtil
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils

/**
 * In-chat system-face (sysface) picker for the inline input pill ([Settings.inlineEmojiButton]).
 * Shows a scrollable grid of QQ sysfaces floating just above the input bar (where the keyboard
 * was). Tapping a face inserts its emo-code rendered as an EmoticonSpan (via [QQText], same
 * encoding the inline send path parses) into the EditText. Toggling the panel collapses the soft
 * keyboard; tapping the input field again closes the panel and reopens the keyboard.
 *
 * This is a plain object (not a @Mixin), so the anonymous listener/adapter classes it creates are
 * fine — the mixin-copy package issue only affects classes declared inside @Mixin method bodies.
 */
object InlineEmojiPanel {
    private const val TAG = "qqpro_inline_emoji_panel"
    private var panel: View? = null
    private var boundEdit: EditText? = null
    // Input pill we translated up to sit above the panel (reset to 0 on dismiss).
    private var liftedPill: View? = null
    // Decoded sysface drawables, cached after the first (slow) build so later opens are instant.
    private var faceCache: List<Pair<Int, Drawable>>? = null

    private val detachDismiss = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}
        override fun onViewDetachedFromWindow(v: View) { dismiss() }
    }

    val isShowing get() = panel != null

    fun toggle(editText: EditText) {
        if (isShowing) { dismiss(); showKeyboard(editText) } else show(editText)
    }

    fun dismiss() {
        liftedPill?.let { it.translationY = 0f }
        liftedPill = null
        panel?.let { (it.parent as? ViewGroup)?.removeView(it) }
        panel = null
    }

    private fun imm(v: View) = v.context.getSystemService(InputMethodManager::class.java)

    private fun showKeyboard(editText: EditText) {
        editText.requestFocus()
        editText.post { imm(editText)?.showSoftInput(editText, 0) }
    }

    private fun hideKeyboard(editText: EditText) {
        imm(editText)?.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    private fun show(editText: EditText) {
        runCatching {
            val activity = editText.context as? Activity ?: return
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

            if (boundEdit !== editText) {
                boundEdit = editText
                // Tapping the input field closes the panel and brings the keyboard back.
                editText.setOnClickListener { if (isShowing) { dismiss(); showKeyboard(editText) } }
                editText.removeOnAttachStateChangeListener(detachDismiss)
                editText.addOnAttachStateChangeListener(detachDismiss)
            }

            hideKeyboard(editText)
            // Build after the keyboard collapses so the panel floats just above the input bar
            // (which stays at the bottom, tappable), not over it.
            editText.postDelayed({
                runCatching {
                    if (boundEdit !== editText) return@runCatching
                    dismiss()
                    val ctx = editText.context
                    val panelH = (content.height * 0.62f).toInt().coerceAtLeast(220.dp)
                    val container = FrameLayout(ctx).apply {
                        tag = TAG
                        isClickable = true // swallow taps so they don't fall through to the chat
                        setBackgroundColor(0xF2_1C1C1C.toInt())
                        elevation = 24.dp.toFloat()
                    }
                    // Sit just above the input pill (its parent), leaving it tappable below.
                    val pill = editText.parent as? View
                    val bottomMargin = if (pill != null) {
                        val loc = IntArray(2); pill.getLocationInWindow(loc)
                        val cloc = IntArray(2); content.getLocationInWindow(cloc)
                        ((cloc[1] + content.height) - loc[1]).coerceAtLeast(0)
                    } else 96.dp
                    content.addView(container, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, panelH, Gravity.BOTTOM
                    ).apply { this.bottomMargin = bottomMargin })
                    panel = container
                    populate(container, editText)
                    Utils.log("InlineEmojiPanel shown (h=$panelH bottomMargin=$bottomMargin)")
                }.onFailure { Utils.log("InlineEmojiPanel build failed: $it") }
            }, 180)
        }.onFailure { Utils.log("InlineEmojiPanel.show failed: $it") }
    }

    /**
     * Fills [container] with the sysface grid. Shows a spinner immediately; if the drawables are
     * already cached the grid is built instantly, otherwise they are decoded in small batches
     * (posting between batches) so the spinner keeps animating instead of freezing the UI.
     */
    private fun populate(container: FrameLayout, editText: EditText) {
        val ctx = container.context
        val screenW = ctx.resources.displayMetrics.widthPixels
        // Big faces for a watch: fewer columns => larger cells.
        val columns = 6
        val cell = screenW / columns
        val pad = (cell * 0.16f).toInt()

        val progress = ProgressBar(ctx)
        container.addView(progress, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val grid = GridLayout(ctx).apply {
            columnCount = columns
            setPadding(2.dp, 2.dp, 2.dp, 2.dp)
        }
        val scroll = ScrollView(ctx).apply { addView(grid) }
        container.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        fun addCell(id: Int, d: Drawable) {
            val iv = ImageView(ctx).apply {
                setImageDrawable(d)
                setPadding(pad, pad, pad, pad)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setOnClickListener { insert(editText, id) }
            }
            grid.addView(iv, GridLayout.LayoutParams().apply { width = cell; height = cell })
        }

        val cache = faceCache
        if (cache != null) {
            for ((id, d) in cache) addCell(id, d)
            container.removeView(progress)
            Utils.log("InlineEmojiPanel: grid from cache (${cache.size})")
            return
        }

        // First open: decode in batches so the spinner animates; hide the grid until it's ready.
        scroll.visibility = View.INVISIBLE
        val ids = ArrayList<Int>()
        val all = QQSysFaceUtil.a.h()
        for (k in 0 until all.size) {
            val id = all[k] ?: continue
            if (QQSysFaceUtil.a.j(id)) ids.add(id)
        }
        val result = ArrayList<Pair<Int, Drawable>>()
        fun step(start: Int) {
            if (panel !== container) return // dismissed mid-build
            var i = start
            var n = 0
            while (i < ids.size && n < 24) {
                val id = ids[i]; i++
                val d = runCatching { QQSysFaceUtil.a.d(id) }.getOrNull() ?: continue
                result.add(id to d)
                addCell(id, d)
                n++
            }
            if (i < ids.size) {
                grid.post { step(i) }
            } else {
                faceCache = result
                scroll.visibility = View.VISIBLE
                container.removeView(progress)
                Utils.log("InlineEmojiPanel: grid built+cached (${result.size})")
            }
        }
        step(0)
    }

    private fun insert(editText: EditText, localId: Int) {
        runCatching {
            val emo = QQSysFaceUtil.a.g(localId)
            val rendered = QQText(emo, 3, 18, null)
            val s = editText.selectionStart.coerceAtLeast(0)
            val e = editText.selectionEnd.coerceAtLeast(0)
            editText.text?.replace(minOf(s, e), maxOf(s, e), rendered)
        }.onFailure { Utils.log("InlineEmojiPanel insert failed: $it") }
    }
}
