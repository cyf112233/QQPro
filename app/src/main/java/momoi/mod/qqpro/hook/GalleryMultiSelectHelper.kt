package momoi.mod.qqpro.hook

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.watch.gallery.GalleryFragment
import com.tencent.watch.aio_impl.ext.MsgUtil as WatchMsgUtil
import moye.wearqq.IMEOperation
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.hook.action.GalleryMultiSelectState
import momoi.mod.qqpro.lib.dp

private const val HTAG = "QQPro"

/** All multi-select state and logic, kept in a top-level class so its constructor is public. */
class GalleryMultiSelectHelper(private val fragment: GalleryFragment) {

    var multiSelectMode = false
    val selectedPaths = linkedSetOf<String>()
    var sendButton: TextView? = null

    // Set to true by gesture handlers when an UP event should be consumed
    // (long-press UP or single-tap-in-multiselect). Cleared on every ACTION_DOWN.
    private var interceptNextUp = false

    // Set once we've initiated a send+pop so a fast double-tap can't fire the action twice
    // (which pops/sends twice and crashes). Sticky for the lifetime of this helper instance.
    private var actionConsumed = false

    private val overlayPaint = Paint().apply { color = Color.parseColor("#661B9AF7") }

    // ---- public API called from the mixin hook --------------------------------

    fun buildSendButton(ctx: Context): TextView {
        return TextView(ctx).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.WHITE)
            background = roundCornerDrawable(Color.parseColor("#1B9AF7"), 9999f)
            setPadding(24.dp, 0, 24.dp, 0)
            text = "发送"
        }
    }

    fun setupGestureDetector(rv: RecyclerView) {
        val gesture = GestureDetector(rv.context, makeGestureListener(rv))
        // Disable double-tap detection. Otherwise the 2nd tap of a fast double-tap is swallowed by
        // GestureDetector (routed to onDoubleTap, not onSingleTapUp), so we never intercept its UP
        // and it leaks to the native item onClick — which just pops the picker (our launch context
        // has no fragment-result listener), dismissing the gallery. With this off, every tap fires
        // onSingleTapUp and is handled/intercepted normally.
        gesture.setOnDoubleTapListener(null)
        rv.addOnItemTouchListener(makeTouchListener(gesture))
    }

    fun setupItemDecoration(rv: RecyclerView) {
        rv.addItemDecoration(makeItemDecoration(rv))
    }

    fun toggleSelection(path: String, rv: RecyclerView) {
        if (selectedPaths.contains(path)) selectedPaths.remove(path)
        else selectedPaths.add(path)
        updateSendButton()
        rv.invalidateItemDecorations()
        Utils.log("Gallery toggleSelection now selected=${selectedPaths.size} multiSelectMode=$multiSelectMode")
        if (multiSelectMode && selectedPaths.isEmpty()) exitMultiSelectMode(rv)
    }

    fun onSendClicked(rv: RecyclerView) {
        Log.e(HTAG, "GalleryMultiSelect: send button clicked, selected=${selectedPaths.size}")
        Utils.log("MultiSelect onSendClicked selected=${selectedPaths.size}")
        if (selectedPaths.isEmpty()) {
            Log.e(HTAG, "GalleryMultiSelect: nothing selected, aborting")
            Utils.log("MultiSelect nothing selected")
            return
        }

        // Build in SELECTION order (selectedPaths is a LinkedHashSet → insertion-ordered),
        // not gallery display order, so the images send in the order the user tapped them.
        val allItems = fragment.i?.currentList ?: emptyList()
        val byPath = allItems.associateBy { it.c }
        val selectedItems = selectedPaths.mapNotNull { byPath[it] }
        Utils.log("MultiSelect selectedItems=${selectedItems.size} (selection order), building image elements")

        val elements = ArrayList<MsgElement>()
        for (item in selectedItems) {
            try {
                elements.add(WatchMsgUtil.a.a(item.c, 0))
            } catch (t: Throwable) {
                Log.e(HTAG, "GalleryMultiSelect: build failed ${item.c}: ${t.message}")
                Utils.log("MultiSelect build FAILED ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        exitMultiSelectMode(rv)
        attachToImeAndOpen(elements)
    }

    // ---- private helpers -------------------------------------------------------

    /**
     * Attach the picked image element(s) to the chat input bar's pending list and ask the chat
     * to open the input-bar preview once the gallery pops. The user then reviews and sends them
     * as a single message (or cancels). Mirrors QQ's own default image flow, minus the forced
     * page switch. Any stale pending attachment from a cancelled pick is dropped first so it
     * cannot ride along with this send.
     */
    private fun attachToImeAndOpen(elements: List<MsgElement>) {
        if (elements.isEmpty()) {
            Utils.log("Gallery attachToImeAndOpen: no elements, just popping")
            fragment.pop()
            return
        }
        IMEOperation.extraMsg.clear()
        IMEOperation.extraMsg.addAll(elements)
        GalleryMultiSelectState.pendingOpenIme = true
        Utils.log("Gallery attached ${elements.size} image(s) to IME, popping gallery")
        Log.e(HTAG, "GalleryMultiSelect: attached ${elements.size} image(s), calling pop()")
        fragment.pop()
    }

    private fun enterMultiSelectMode() {
        multiSelectMode = true
        Log.e(HTAG, "GalleryMultiSelect: entered multi-select mode")
    }

    private fun exitMultiSelectMode(rv: RecyclerView) {
        multiSelectMode = false
        selectedPaths.clear()
        sendButton?.visibility = View.GONE
        rv.invalidateItemDecorations()
        Log.e(HTAG, "GalleryMultiSelect: exited multi-select mode")
    }

    private fun updateSendButton() {
        val btn = sendButton ?: return
        if (multiSelectMode && selectedPaths.isNotEmpty()) {
            btn.text = "发送 ${selectedPaths.size}"
            btn.visibility = View.VISIBLE
        } else {
            btn.visibility = View.GONE
        }
    }

    // ---- anonymous-class factories (all in this package, so constructors are accessible) ------

    private fun makeGestureListener(rv: RecyclerView) = object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            val child = rv.findChildViewUnder(e.x, e.y) ?: return
            val pos = rv.getChildAdapterPosition(child)
            if (pos < 0) return
            val item = fragment.i?.currentList?.getOrNull(pos) ?: return
            val path = item.c ?: return
            // Multi-select supports images only (C == 1 means video). Long-pressing a video can't
            // start a selection — tell the user and consume so the gesture doesn't fall through.
            if (item.C == 1) {
                Utils.log("Gallery onLongPress video pos=$pos (not supported)")
                Utils.toast(rv.context, "视频不支持多选")
                interceptNextUp = true
                return
            }
            Utils.log("Gallery onLongPress pos=$pos multiSelectMode=$multiSelectMode")
            if (!multiSelectMode) enterMultiSelectMode()
            // consume the ACTION_UP that follows a long-press so the item's click doesn't fire
            interceptNextUp = true
            toggleSelection(path, rv)
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            Utils.log("Gallery onSingleTapUp multiSelectMode=$multiSelectMode selected=${selectedPaths.size}")
            if (!multiSelectMode) {
                val child = rv.findChildViewUnder(e.x, e.y) ?: return false
                val pos = rv.getChildAdapterPosition(child)
                val item = if (pos >= 0) fragment.i?.currentList?.getOrNull(pos) else null
                val path = item?.c ?: return false
                if (item.C == 1) {
                    // Video: build+send it ourselves. The native item tap only fires a fragment
                    // result + pop, and our gallery launch has no result listener, so delegating to
                    // native just dismisses the picker without sending. Drop any stale pending image
                    // first so it can't ride along, then send the video directly (heavy build runs
                    // off the UI thread inside sendGalleryVideo) and pop.
                    if (actionConsumed) { interceptNextUp = true; return true } // block double send
                    IMEOperation.extraMsg.clear()
                    interceptNextUp = true
                    actionConsumed = true
                    sendGalleryVideo(path)
                    fragment.pop()
                    return true
                }
                // Image: take over the tap so the send routes through the input-bar preview without
                // the native selector.invoke(0) that would force-switch to the chat page even on cancel.
                if (actionConsumed) return true  // guard against a fast double-tap popping twice
                val element = try {
                    WatchMsgUtil.a.a(path, 0)
                } catch (t: Throwable) {
                    Utils.log("Gallery single image build FAILED: ${t.message}")
                    return false
                }
                // consume this UP so the item's native click (which would send immediately) doesn't fire
                interceptNextUp = true
                actionConsumed = true
                attachToImeAndOpen(listOf(element))
                return true
            }
            val child = rv.findChildViewUnder(e.x, e.y)
            if (child == null) { Utils.log("Gallery multiselect tap: child=null"); return false }
            val pos = rv.getChildAdapterPosition(child)
            if (pos < 0) { Utils.log("Gallery multiselect tap: pos<0"); return false }
            val item = fragment.i?.currentList?.getOrNull(pos)
            if (item == null) { Utils.log("Gallery multiselect tap: item=null pos=$pos"); return false }
            val path = item.c
            if (path == null) { Utils.log("Gallery multiselect tap: path=null pos=$pos"); return false }
            if (item.C == 1) {
                // Video can't be multi-selected. Tell the user and consume the tap so it doesn't
                // leak to the native item onClick (which would pop/dismiss the picker).
                Utils.log("Gallery multiselect tap: video pos=$pos (not supported)")
                Utils.toast(rv.context, "视频不支持多选")
                interceptNextUp = true
                return true
            }
            // consume this UP event so the item's default click (open viewer) doesn't fire
            interceptNextUp = true
            toggleSelection(path, rv)
            return true
        }

        override fun onDown(e: MotionEvent): Boolean = true
    }

    private fun makeTouchListener(gesture: GestureDetector) = object : RecyclerView.OnItemTouchListener {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    interceptNextUp = false
                    gesture.onTouchEvent(e)
                    return false  // always let DOWN through so scroll tracking starts
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    gesture.onTouchEvent(e)
                    // interceptNextUp may have been set synchronously by onLongPress / onSingleTapUp above
                    val intercept = interceptNextUp
                    interceptNextUp = false
                    return intercept
                }
                else -> {
                    gesture.onTouchEvent(e)
                    return false  // never intercept MOVE → scrolling works in multi-select mode
                }
            }
        }
        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) { gesture.onTouchEvent(e) }
        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
    }

    private fun makeItemDecoration(rv: RecyclerView) = object : RecyclerView.ItemDecoration() {
        private val radius = 10.dp.toFloat()
        private val margin = 6.dp.toFloat()
        private val circleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B9AF7") }
        private val circleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
        }
        private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
        }
        private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER
            textSize = radius * 1.1f; isFakeBoldText = true
        }

        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            if (!multiSelectMode) return
            val list = fragment.i?.currentList ?: return
            for (idx in 0 until parent.childCount) {
                val child = parent.getChildAt(idx)
                val pos = parent.getChildAdapterPosition(child)
                if (pos < 0) continue
                val item = list.getOrNull(pos) ?: continue
                val path = item.c ?: continue
                val selected = selectedPaths.contains(path)

                if (selected) c.drawRect(
                    child.left.toFloat(), child.top.toFloat(),
                    child.right.toFloat(), child.bottom.toFloat(), overlayPaint
                )

                val cx = child.right - margin - radius
                val cy = child.top + margin + radius

                if (selected) {
                    c.drawCircle(cx, cy, radius, circleFill)
                    // Show the 1-based selection order so the user can see/verify the send order.
                    val order = selectedPaths.indexOf(path) + 1
                    val baseline = cy - (numberPaint.descent() + numberPaint.ascent()) / 2f
                    c.drawText(order.toString(), cx, baseline, numberPaint)
                } else {
                    c.drawCircle(cx, cy, radius, circleBorder)
                }
            }
        }
    }
}
