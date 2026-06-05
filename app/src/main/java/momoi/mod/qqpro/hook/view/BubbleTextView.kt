package momoi.mod.qqpro.hook.view

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.AIOLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.watch.aio_impl.data.WatchAIOMsgItem
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.hook.action.CurrentMsgList
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils

/**
 * The native chat unread bubble ([com.tencent.watch.aio_impl.reserve1.unreadbubble.UnreadBubbleVB])
 * drives this view through [setText] / [setBackgroundResource] / [setVisibility].
 *
 * We restyle it into a large pill anchored at the bottom-right, with only the left side
 * rounded (right side flush/cut by the screen edge):
 *  - blue "↓ N" while there are new/unread messages,
 *  - grey "↓" back-to-bottom button while merely scrolled up.
 *
 * Visibility for the grey back-to-bottom state follows scroll direction (shown while
 * scrolling down, hidden while scrolling up); the blue new-message count is always shown.
 *
 * Two-stage return: when the chat is scrolled UP programmatically (tapping a reply source, or
 * "jump to first unread"), [beginJumpUp] remembers the position we left from and forces this
 * button visible immediately. The first tap then returns to that remembered position; only the
 * next tap (no anchor pending) does the native go-to-bottom.
 */
@SuppressLint("ViewConstructor", "SetTextI18n")
class BubbleTextView(context: Context) : TextView(context) {
    // Left corners fully rounded (semicircle), right corners square so it sits flush to the screen edge.
    private val blueBg = roundCornerDrawable(0xFF_22a6f2.toInt(), 9999f, 0f, 9999f, 0f)
    private val greyBg = roundCornerDrawable(0xCC_303030.toInt(), 9999f, 0f, 9999f, 0f)

    private var nativeWantsShow = false
    private var isCountMode = false
    private var hiddenByScrollUp = false
    private var forceShow = false
    private var scrollAttached = false

    // Native installs its own go-to-bottom click; we capture it here and wrap it so the
    // first tap after a programmatic jump-up returns to the remembered anchor instead.
    private var delegateClick: OnClickListener? = null

    init {
        gravity = Gravity.CENTER
        setTextColor(0xFF_FFFFFF.toInt())
        textSize = 14f
        setPadding(18.dp, 9.dp, 14.dp, 9.dp)
    }

    // Native K() sets aio_unread_bg (the blue count circle) — replace with our blue pill.
    override fun setBackgroundResource(resid: Int) {
        background = blueBg
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        val t = text?.toString().orEmpty()
        isCountMode = t.isNotEmpty()
        if (isCountMode) {
            background = blueBg
            super.setText("↓ $t", type)
        } else {
            // Empty text means the back-to-bottom state: grey down-arrow pill.
            background = greyBg
            super.setText("↓", type)
        }
        applyVisibility()
    }

    // Native uses VISIBLE(0) for the count, INVISIBLE(4) for back-to-bottom, GONE(8) to hide.
    // Treat INVISIBLE as "show" so the back-to-bottom button is actually visible.
    override fun setVisibility(visibility: Int) {
        nativeWantsShow = visibility != View.GONE
        applyVisibility()
    }

    // Wrap whatever click listener native installs so we can intercept it for the two-stage return.
    override fun setOnClickListener(l: OnClickListener?) {
        if (l == null) {
            delegateClick = null
            super.setOnClickListener(null)
            return
        }
        delegateClick = l
        super.setOnClickListener { v -> onBubbleClick(v) }
    }

    private fun onBubbleClick(v: View?) {
        val anchor = returnAnchor
        // Honour the anchor only if we're still ABOVE it (haven't scrolled down to/past it yet).
        // If the user already scrolled back down past the anchor, jumping up to it would be wrong —
        // treat it like a normal go-to-bottom instead.
        if (anchor != null && anchorStillBelowViewport(anchor)) {
            // First tap of a programmatic jump-up: go back to where we started.
            returnAnchor = null
            scrollBackToAnchor(anchor)
        } else {
            // No pending anchor (or already scrolled past it): the real go-to-bottom (native behaviour).
            returnAnchor = null
            forceShow = false
            delegateClick?.onClick(v)
        }
    }

    // True when the anchor message is still below the bottom of the current viewport, i.e. the user
    // is reading above it and a return-to-anchor would scroll downward toward it.
    private fun anchorStillBelowViewport(anchor: WatchAIOMsgItem): Boolean {
        val rv = runCatching { CurrentMsgList.vb.H }.getOrNull() ?: return false
        val lm = rv.layoutManager as? AIOLayoutManager ?: return false
        val idx = CurrentMsgList.getMsgIndex(anchor)
        if (idx < 0) return false
        return lm.findLastVisibleItemPosition() < idx
    }

    private fun scrollBackToAnchor(anchor: WatchAIOMsgItem) {
        val rv = runCatching { CurrentMsgList.vb.H }.getOrNull()
        val idx = CurrentMsgList.getMsgIndex(anchor)
        if (rv == null || idx < 0) {
            // Anchor no longer loaded — fall back to going straight to the bottom.
            forceShow = false
            delegateClick?.onClick(this)
            return
        }
        rv.smoothScrollToStart(idx)
        // Keep the button shown; a further tap (now without an anchor) goes to the real bottom.
        forceShow = true
        hiddenByScrollUp = false
        applyVisibility()
        Utils.log("BubbleTextView returned to anchor index=$idx")
    }

    private fun applyVisibility() {
        val show = forceShow || (nativeWantsShow && (isCountMode || !hiddenByScrollUp))
        super.setVisibility(if (show) View.VISIBLE else View.GONE)
    }

    private fun showForBackDown() {
        forceShow = true
        hiddenByScrollUp = false
        applyVisibility()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        current = this
        attachScrollListener(0)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (current === this) current = null
        returnAnchor = null
        forceShow = false
    }

    private fun attachScrollListener(tries: Int) {
        if (scrollAttached || tries > 20) return
        val rv = runCatching { CurrentMsgList.vb.H }.getOrNull()
        if (rv == null) {
            postDelayed({ attachScrollListener(tries + 1) }, 200)
            return
        }
        scrollAttached = true
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Reaching the real bottom ends the two-stage state and hands control back to native.
                val lm = recyclerView.layoutManager as? AIOLayoutManager
                if (lm != null && lm.findLastVisibleItemPosition() >= CurrentMsgList.msgList.value.size - 1) {
                    if (forceShow) forceShow = false
                    returnAnchor = null
                }
                when {
                    // Scrolling down (towards the latest message) -> allow showing.
                    dy > 4 -> if (hiddenByScrollUp) {
                        hiddenByScrollUp = false
                    }
                    // Scrolling up (reading history) -> hide the grey back-to-bottom button.
                    dy < -4 -> if (!hiddenByScrollUp) {
                        hiddenByScrollUp = true
                    }
                }
                applyVisibility()
            }
        })
        Utils.log("BubbleTextView scroll listener attached")
    }

    companion object {
        private var current: BubbleTextView? = null

        // The position we left from on the last programmatic upward jump; the first tap of the
        // back-down button returns here before the next tap goes to the real bottom.
        private var returnAnchor: WatchAIOMsgItem? = null

        /**
         * Call right before a programmatic upward jump (tapping a reply source, or "jump to first
         * unread"). Remembers the current top-most visible message and forces the back-down button
         * visible immediately so the user can return.
         */
        fun beginJumpUp() {
            val rv = runCatching { CurrentMsgList.vb.H }.getOrNull() ?: return
            val pos = (rv.layoutManager as? AIOLayoutManager)?.findFirstVisibleItemPosition() ?: -1
            returnAnchor = CurrentMsgList.msgList.value.getOrNull(pos)
            Utils.log("BubbleTextView beginJumpUp anchor pos=$pos set=${returnAnchor != null}")
            current?.showForBackDown()
        }
    }
}
