package momoi.mod.qqpro.hook

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.tencent.watch.aio_impl.ui.WatchAIOFragment
import com.tencent.watch.aio_impl.ui.frames.MenuFrame
import momoi.mod.qqpro.util.Utils

/**
 * Shows the chat attachment list (相册/拍照/录像/… + 表情) as an overlay over the chat,
 * instead of the (now removed) ViewPager attachment page. Triggered by the "+" button that
 * replaces the input bar's emoji button when [Settings.attachmentOverlay] is on.
 *
 * The attachment list is a REAL [MenuFrame], hosted in the [WatchAIOFragment]'s own view tree
 * and childFragmentManager — exactly where the ViewPager would host it — so gallery navigation
 * (which resolves the AIO NavController from the fragment) and all existing QQPro customizations
 * (MenuPanelLayout / CameraSend / gallery flows) keep working unchanged. A plain DialogFragment
 * would put the content in a separate window where the NavController can't be found.
 */
object AttachmentOverlay {
    /** True while the overlay is showing — read by MenuPanelLayout to inject the 表情 item. */
    var active = false
        private set

    /** Action for the injected 表情 list item: dismiss the overlay and open the native emoji panel. */
    var emojiAction: (() -> Unit)? = null
        private set

    private var scrim: FrameLayout? = null
    private var hostFm: FragmentManager? = null
    private var hosted: Fragment? = null
    private var backCallback: OnBackPressedCallback? = null

    /** [anchor] = the "+" button view; [emojiView] = the native emoji button (getChildAt(0)). */
    fun show(anchor: View, emojiView: View) {
        // If a previous overlay's host was torn down without dismiss() (e.g. chat closed
        // underneath it), the flag may be stale — clear it so we can open again.
        if (active && scrim?.isAttachedToWindow != true) resetState()
        if (active) return
        runCatching {
            val aio = findAioFragment(anchor) ?: run {
                Utils.log("AttachmentOverlay: WatchAIOFragment not found"); return
            }
            val container = aio.view as? ViewGroup ?: run {
                Utils.log("AttachmentOverlay: AIO container view missing"); return
            }
            val fm = aio.childFragmentManager

            val inner = FrameLayout(container.context).apply { id = View.generateViewId() }
            val box = FrameLayout(container.context).apply {
                background = android.graphics.drawable.ColorDrawable(0x44_000000)
                isClickable = true
                setOnClickListener { dismiss() }
                addView(inner, FrameLayout.LayoutParams(-1, -1))
                // If the host is destroyed without dismiss(), reset our state on detach.
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) { if (active) resetState() }
                })
            }
            container.addView(box, ViewGroup.LayoutParams(-1, -1))

            active = true
            emojiAction = { dismiss(); emojiView.callOnClick() }
            scrim = box
            hostFm = fm

            // Back press should close the overlay, not pop the whole chat fragment.
            val cb = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { dismiss() }
            }
            aio.requireActivity().onBackPressedDispatcher.addCallback(aio.viewLifecycleOwner, cb)
            backCallback = cb

            val menu = MenuFrame({ _: Int -> dismiss() }, true)
            menu.arguments = aio.arguments
            hosted = menu
            fm.beginTransaction().replace(inner.id, menu).commitNow()
            Utils.log("AttachmentOverlay: shown")
        }.onFailure { Utils.log("AttachmentOverlay.show failed: $it"); resetState() }
    }

    fun dismiss() {
        if (!active) return
        runCatching {
            hosted?.let { f -> hostFm?.beginTransaction()?.remove(f)?.commitNowAllowingStateLoss() }
            (scrim?.parent as? ViewGroup)?.removeView(scrim)
        }.onFailure { Utils.log("AttachmentOverlay.dismiss failed: $it") }
        resetState()
    }

    private fun resetState() {
        backCallback?.remove()
        active = false
        emojiAction = null
        scrim = null
        hostFm = null
        hosted = null
        backCallback = null
    }

    private fun findAioFragment(anchor: View): WatchAIOFragment? {
        var f: Fragment? = runCatching { FragmentManager.findFragment<Fragment>(anchor) }.getOrNull()
        while (f != null) {
            if (f is WatchAIOFragment) return f
            f = f.parentFragment
        }
        return null
    }
}
