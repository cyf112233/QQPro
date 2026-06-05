package momoi.mod.qqpro.hook

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.watch.aio_impl.ui.frames.MenuFrame
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.drawable.audioFileIconDrawable
import momoi.mod.qqpro.drawable.cameraIconDrawable
import momoi.mod.qqpro.drawable.galleryIconDrawable
import momoi.mod.qqpro.drawable.phoneIconDrawable
import momoi.mod.qqpro.drawable.recordIconDrawable
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.drawable.videoIconDrawable
import momoi.mod.qqpro.findAll
import momoi.mod.qqpro.hook.style.cardMargin
import momoi.mod.qqpro.hook.view.CallConfirmFragment
import momoi.mod.qqpro.lib.clickable
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.dpf
import momoi.mod.qqpro.lib.onChildAttached
import momoi.mod.qqpro.util.Utils

/**
 * Chat "+" panel (相册 / 拍照 / 语音通话 / 视频通话). The stock layout is a 2-column grid of
 * icon-over-text cells with poor system icons. Re-lay it out as a single-column vertical list
 * of rounded "icon-left / text-right" cards with freshly drawn icons.
 *
 * We never touch the obfuscated adapter or its click logic: the original OnClickListener stays
 * on the icon ImageView (so gallery / camera / call all still work) — we just restyle each
 * attached cell in place and forward whole-row taps to the icon.
 */
@Mixin
class MenuPanelLayout(p0: (Int) -> Unit, p1: Boolean) : MenuFrame(p0, p1) {

    override fun Y(p0: LayoutInflater, p1: ViewGroup?, p2: Bundle?): View {
        val root = super.Y(p0, p1, p2)!!
        val list = (root as? ViewGroup)?.findAll { it is RecyclerView } as? RecyclerView
        if (list != null) {
            list.layoutManager = GridLayoutManager(list.context, 1)
            list.setPadding(8.dp, 0, 8.dp, 0)
            list.clipToPadding = false
        }
        Utils.log("MenuPanelLayout: switched to vertical list")
        return root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        handleCaptureResult(requestCode, resultCode, data)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list = (view as? ViewGroup)?.findAll { it is RecyclerView } as? RecyclerView
        list?.onChildAttached { restyleCell(it) }
        // Inject a "录像" item right after 拍照 in the native adapter's data list.
        list?.let { injectRecordItem(it) }
    }

    private fun injectRecordItem(list: RecyclerView) {
        runCatching {
            val adapter = list.adapter ?: return
            val field = adapter.javaClass.declaredFields.firstOrNull {
                List::class.java.isAssignableFrom(it.type)
            } ?: return
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val items = field.get(adapter) as? MutableList<com.tencent.watch.aio_impl.ui.frames.MenuItem>
                ?: return
            if (items.any { it is RecordMenuItem }) return
            val camIndex = items.indexOfFirst { it.b().contains("拍") }
            val at = if (camIndex >= 0) camIndex + 1 else items.size
            items.add(at, RecordMenuItem(this))
            // 音频文件 goes right below 录像.
            items.add(at + 1, AudioMenuItem(this))
            adapter.notifyDataSetChanged()
            Utils.log("MenuPanelLayout: injected 录像/音频文件 at $at")
        }.onFailure { Utils.log("MenuPanelLayout: inject 录像 failed: $it") }
    }

    private fun restyleCell(cell: View) {
        val ll = cell as? LinearLayout ?: return
        var icon: ImageView? = null
        var label: TextView? = null
        for (i in 0 until ll.childCount) {
            val c = ll.getChildAt(i)
            if (icon == null && c is ImageView) icon = c
            if (label == null && c is TextView) label = c
        }
        if (icon == null || label == null) return

        // Card container: horizontal row, dark rounded background, unified margin + padding.
        ll.orientation = LinearLayout.HORIZONTAL
        ll.gravity = Gravity.CENTER_VERTICAL
        ll.background = roundCornerDrawable(0x80_242424.toInt(), 14.dpf)
        ll.cardMargin()
        ll.setPadding(14.dp, 10.dp, 14.dp, 10.dp)

        // Icon on the left, fixed size, with our drawn drawable chosen by the label text.
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        icon.setImageDrawable(iconFor(label.text?.toString().orEmpty()))
        icon.layoutParams = LinearLayout.LayoutParams(28.dp, 28.dp)

        // Label fills the rest, left-aligned.
        label.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        label.textSize = 14f
        label.setTextColor(0xFF_FFFFFF.toInt())
        label.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 12.dp
        }

        // Voice / video calls go through a confirmation first to avoid accidental triggers;
        // everything else forwards the tap straight to the icon's existing handler.
        val label0 = label.text?.toString().orEmpty()
        when {
            label0.contains("通话") -> {
                icon.isClickable = false
                ll.clickable { confirmCall(icon, label0) }
            }
            // 拍照 with in-app camera off → launch the system camera app instead.
            label0.contains("拍") && !Settings.useInAppCamera.value -> {
                icon.isClickable = false
                ll.clickable { launchSystemPhoto(this) }
            }
            else -> ll.clickable { icon.performClick() }
        }
    }

    private fun confirmCall(action: View, label: String) {
        runCatching {
            CallConfirmFragment("确定要发起$label 吗？", action)
                .show(parentFragmentManager, "qqpro_call_confirm")
        }.onFailure { Utils.log("call confirm show failed: $it") }
    }

    private fun iconFor(text: String) = when {
        text.contains("音频") -> audioFileIconDrawable()
        text.contains("录") -> recordIconDrawable()
        text.contains("相册") -> galleryIconDrawable()
        text.contains("拍") -> cameraIconDrawable()
        text.contains("视频") -> videoIconDrawable()
        text.contains("语音") || text.contains("通话") -> phoneIconDrawable()
        else -> galleryIconDrawable()
    }
}

/**
 * A panel item that records a video. Inserted after 拍照. When the in-app camera setting is on it
 * uses the built-in [VideoRecordFragment]; otherwise it falls back to the system camera app.
 */
class RecordMenuItem(
    private val fragment: androidx.fragment.app.Fragment
) : com.tencent.watch.aio_impl.ui.frames.MenuItem() {
    override fun a() = 0
    override fun b() = "录像"
    override fun d() = 2
    override fun e() {
        if (Settings.useInAppCamera.value) {
            runCatching {
                momoi.mod.qqpro.hook.view.VideoRecordFragment(fragment)
                    .show(fragment.parentFragmentManager, "qqpro_video_record")
            }.onFailure {
                Utils.log("record: show in-app recorder failed: $it")
                launchSystemVideo(fragment)
            }
        } else {
            launchSystemVideo(fragment)
        }
    }
}

/**
 * A panel item that picks an audio file from local storage and sends it as a voice (PTT) message.
 * Inserted right below 录像.
 */
class AudioMenuItem(
    private val fragment: androidx.fragment.app.Fragment
) : com.tencent.watch.aio_impl.ui.frames.MenuItem() {
    override fun a() = 0
    override fun b() = "音频文件"
    override fun d() = 2
    override fun e() {
        runCatching { launchPickAudio(fragment) }
            .onFailure { Utils.log("audio: launch from menu failed: $it") }
    }
}
