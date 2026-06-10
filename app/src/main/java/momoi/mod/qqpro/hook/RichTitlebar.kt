package momoi.mod.qqpro.hook

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.tencent.qqnt.kernel.nativeinterface.DeleteRecentContactInfo
import com.tencent.qqnt.kernel.nativeinterface.IKernelRecentContactListener
import com.tencent.qqnt.kernel.nativeinterface.NotificationCommonInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactExtra
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactListChangedInfo
import com.tencent.qqnt.msg.KernelServiceUtil
import com.tencent.qqnt.watch.ui.componet.tablayout.CircleIndicator
import com.tencent.watch.aio_impl.ui.WatchAIOFragment
import java.util.concurrent.atomic.AtomicBoolean
import momoi.mod.qqpro.QQNT
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.enums.ChatType
import momoi.mod.qqpro.findAll
import momoi.mod.qqpro.hook.action.RecentContacts
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils

/**
 * Builds the chat titlebar (opt-in via [Settings.enableTitlebar]): a single full-width line
 * showing the group/contact name and, for groups, the member count — e.g. "群名 (233)".
 * A red unread badge (sum of other chats' unread) is overlaid at the top-left. Replaces the
 * top page-indicator strip of [WatchAIOFragment].
 */
object RichTitlebar {
    // Marks the titlebar bar so a rebuild can find and remove the previous one (idempotency).
    private const val BAR_TAG = "qqpro_rich_titlebar"

    // Current bar's badge + chat, plus the live unread map, updated by the kernel listener.
    private var badge: TextView? = null
    private var peer: String = ""
    private val unread = HashMap<String, Int>()
    private val listenerRegistered = AtomicBoolean(false)

    fun build(fragment: WatchAIOFragment, root: ViewGroup) {
        runCatching {
            val ctx = root.context

            // Idempotency: onViewCreated can fire again when returning from the image viewer /
            // avatar preview (same or recreated view tree). Drop any titlebar we built earlier so
            // we don't stack a second bar (doubled gradient + badge) on top of the first.
            while (true) {
                val old = root.findAll { it.tag == BAR_TAG } ?: break
                (old.parent as? ViewGroup)?.removeView(old) ?: break
            }
            val args = fragment.arguments
            val chatType = args?.getInt("key_bundle_chat_type") ?: 0
            val isGroup = chatType == ChatType.GROUP
            val peerId = args?.getString("key_bundle_peer_id").orEmpty()
            val nick = args?.getString("key_bundle_chat_nick")?.takeIf { it.isNotEmpty() }
            val rc = RecentContacts.get(peerId)
            val name = nick
                ?: rc?.raw?.remark?.takeIf { it.isNotEmpty() }
                ?: rc?.raw?.peerName?.takeIf { it.isNotEmpty() }
                ?: peerId

            Utils.log("RichTitlebar build: name=$name peer=$peerId type=$chatType")
            if (name.isEmpty()) {
                Utils.log("RichTitlebar: blank name, skip")
                return
            }

            // Remove the page-indicator dots — the titlebar shows only the name + member count.
            val indicator = root.findAll { it is CircleIndicator }
            (indicator?.parent as? ViewGroup)?.removeView(indicator)

            val heightPx = Settings.titlebarHeight.value.toInt().dp
            val corner = Settings.screenCornerDiameter.value.toInt().dp
            val screenW = ctx.resources.displayMetrics.widthPixels

            // Centered name + member count. The name truncates within (screen width - both
            // rounded corners - room reserved for the member count); the count stays visible.
            val countReserve = 56.dp
            val nameMax = (screenW - 2 * corner - countReserve).coerceAtLeast(40.dp)
            val nameTv = TextView(ctx).apply {
                setTextColor(0xFF_FFFFFF.toInt())
                textSize = 13f
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = nameMax
                text = name
            }
            val countTv = TextView(ctx).apply {
                setTextColor(0xFF_FFFFFF.toInt())
                textSize = 13f
                isSingleLine = true
                text = ""
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            row.addView(nameTv)
            row.addView(countTv)

            if (isGroup) {
                runCatching {
                    QQNT.Group.getMemberList(peerId.toLong()) { res ->
                        countTv.post { countTv.text = " (${res.infos.size})" }
                    }
                }.onFailure { Utils.log("RichTitlebar member fetch failed: $it") }
            }

            val bar = FrameLayout(ctx)
            bar.tag = BAR_TAG
            // Darken the header behind the title so the white text/badge stay readable over the chat.
            bar.background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xAA_000000.toInt(), 0x00_000000)
            )
            bar.addView(row, FrameLayout.LayoutParams(FILL, FILL))

            val badgeView = TextView(ctx).apply {
                setTextColor(0xFF_FFFFFF.toInt())
                textSize = 10f
                gravity = Gravity.CENTER
                background = roundCornerDrawable(0xFF_E5443C.toInt(), 9999f)
                setPadding(5.dp, 1.dp, 5.dp, 1.dp)
                visibility = View.GONE
                // Keep the unread badge above the (centered) name text regardless of relayout
                // caused by the async member-count arriving or the badge toggling visibility.
                elevation = 10.dp.toFloat()
                translationZ = 10.dp.toFloat()
            }
            // A bit further right than the title inset (screen corner x2) to clear the corner fully.
            val badgeInset = corner * 2 + 12.dp
            bar.addView(badgeView, FrameLayout.LayoutParams(WRAP, WRAP,
                Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = badgeInset })

            // Tapping the badge exits the current chat.
            badgeView.setOnClickListener {
                runCatching { fragment.requireActivity().onBackPressed() }
                    .onFailure { Utils.log("RichTitlebar badge back failed: $it") }
            }

            // Track this bar for live unread updates; seed from the cached conversation list.
            // Don't clear `unread`: it's a singleton the kernel listener keeps live across rebuilds.
            // On a rebuild (returning from the image/avatar viewer) the recent-contact list isn't
            // re-rendered, so RecentContacts.map is stale/empty — clearing would blank the badge.
            // Seed only keys we don't already have, preserving the live values.
            badge = badgeView
            peer = peerId
            RecentContacts.map.forEach { (k, v) -> if (!unread.containsKey(k)) unread[k] = v.unreadCntCached }
            badgeView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) { if (badge === badgeView) badge = null }
            })
            ensureListener()
            applyUnread()

            root.addView(bar, FrameLayout.LayoutParams(FILL, heightPx))
            Utils.log("RichTitlebar: built (name=$name group=$isGroup badgeInset=$badgeInset nameMax=$nameMax)")
        }.onFailure { Utils.log("RichTitlebar.build failed: $it") }
    }

    private fun applyUnread() {
        val b = badge ?: return
        if (!Settings.titlebarShowUnread.value) {
            b.post { b.visibility = View.GONE }
            return
        }
        val other = unread.entries.filter { it.key != peer }.sumOf { it.value }
        b.post {
            if (other > 0) {
                b.text = if (other > 99) "99+" else other.toString()
                b.visibility = View.VISIBLE
                b.bringToFront()
                (b.parent as? View)?.invalidate()
            } else {
                b.visibility = View.GONE
            }
        }
    }

    private fun ensureListener() {
        if (!listenerRegistered.compareAndSet(false, true)) return
        runCatching {
            KernelServiceUtil.g()?.recentContactService?.addKernelRecentContactListener(UnreadListener())
            Utils.log("RichTitlebar: unread listener registered")
        }.onFailure { listenerRegistered.set(false); Utils.log("RichTitlebar listener reg failed: $it") }
    }

    private fun put(c: RecentContactInfo?) {
        c ?: return
        if (!c.peerUid.isNullOrEmpty()) unread[c.peerUid] = c.unreadCnt.toInt()
    }

    /** Live unread updates pushed by the kernel; we recompute and refresh the badge in place. */
    private class UnreadListener : IKernelRecentContactListener {
        override fun onMsgUnreadCountUpdate(map: HashMap<String, Int>?) {
            if (map != null) { unread.putAll(map); applyUnread() }
        }
        override fun onRecentContactListChanged(
            sorted: ArrayList<Long>?, changed: ArrayList<RecentContactInfo>?, extra: RecentContactExtra?,
        ) { changed?.forEach { put(it) }; applyUnread() }
        override fun onRecentContactListChangedVer2(list: ArrayList<RecentContactListChangedInfo>?, seq: Int) {
            list?.forEach { it.changedList?.forEach { c -> put(c) } }; applyUnread()
        }
        override fun onRecentContactNotification(
            list: ArrayList<RecentContactInfo>?, common: NotificationCommonInfo?, seq: Int,
        ) { list?.forEach { put(it) }; applyUnread() }
        override fun onGuildDisplayRecentContactListChanged(list: ArrayList<RecentContactListChangedInfo>?) {}
        override fun onDeletedContactsNotify(list: ArrayList<DeleteRecentContactInfo>?) {}
    }
}
