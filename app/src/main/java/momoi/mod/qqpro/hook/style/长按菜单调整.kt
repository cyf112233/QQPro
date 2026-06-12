package momoi.mod.qqpro.hook.style

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.graphics.drawable.Drawable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MemberRole
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.msg.KernelServiceUtil
import com.tencent.watch.aio_impl.data.WatchAIOMsgItem
import com.tencent.watch.aio_impl.ui.cell.base.WatchAIOGroupWidgetItemCell
import com.tencent.watch.aio_impl.ui.menu.AIOLongClickMenuFragment
import com.tencent.watch.aio_impl.ui.menu.MenuItemFactory
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.hook.HistoryMsgRegistry
import momoi.mod.qqpro.hook.forwardText
import momoi.mod.qqpro.hook.forwardMsgRecord
import momoi.mod.qqpro.hook.shareMessage
import momoi.mod.qqpro.asGroup
import momoi.mod.qqpro.drawable.editIconDrawable
import momoi.mod.qqpro.forEachAll
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.hook.action.CurrentGroupMembers
import momoi.mod.qqpro.hook.action.CurrentMsgList
import momoi.mod.qqpro.hook.action.MessageEdit
import momoi.mod.qqpro.hook.action.SelfContact
import momoi.mod.qqpro.hook.action.isGroup
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.LinearScope
import momoi.mod.qqpro.lib.background
import momoi.mod.qqpro.lib.height
import momoi.mod.qqpro.lib.paddingHorizontal
import momoi.mod.qqpro.lib.vh
import momoi.mod.qqpro.lib.width
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.util.runOnUi

val menuSort = arrayOf(
    "回复",
    "@Ta",
    "复制文本",
    "复读文本",
    "去聊天",
    "加好友",
    "删除",
)

// Clone the native menu item layout (icon + desc + hidden switch) so injected items
// (转发 / 编辑) look identical to the built-in ones.
private fun cloneMenuItem(
    parent: ViewGroup,
    label: String,
    icon: Drawable?,
    onClick: () -> Unit
): View {
    val ctx = parent.context
    val res = ctx.resources
    val pkg = ctx.packageName
    val itemView = LayoutInflater.from(ctx).inflate(
        res.getIdentifier("item_setting_with_switch", "layout", pkg), parent, false
    )
    itemView.findViewById<ImageView>(res.getIdentifier("icon", "id", pkg))
        ?.setImageDrawable(icon)
    itemView.findViewById<TextView>(res.getIdentifier("desc", "id", pkg))
        ?.text = label
    itemView.findViewById<View>(res.getIdentifier("button", "id", pkg))
        ?.visibility = View.GONE
    itemView.setOnClickListener { onClick() }
    return itemView
}

private fun process(group: ViewGroup, msg: MsgRecord?, msgItem: WatchAIOMsgItem?, dismiss: () -> Unit) {
    group.removeViewAt(0)
    val linear = group.getChildAt(0).asGroup()
        .getChildAt(0).asGroup()
        .getChildAt(0) as LinearLayout
    linear.background(0x44_000000)
    val items = mutableMapOf<String, View>()
    linear.forEach { item ->
        item.asGroup().forEachAll {
            if (it is AppCompatTextView) {
                items[it.text.toString()] = item
            }
        }
    }
    linear.removeAllViews()
    LinearScope(linear).add<View>()
        .width(FILL)
        .height(if (Utils.isRoundScreen) 0.16f.vh else 0)
    if (Utils.isRoundScreen) {
        linear.paddingHorizontal(0.1f.vh)
    }
    menuSort.forEach {
        items[it]?.let { item ->
            linear.addView(item)
        }
    }
    items.values.forEach {
        if (it.parent == null) {
            linear.addView(it, 1)
        }
    }
    // 我们能用 sendMsg 重发的自包含消息：表情/小黄脸 / 大表情 / 视频 / 语音 / 图片 / giphy / 气泡表情。
    // 文件 / 合并转发聊天记录 / 群邀请(ark) 无法用重发实现，不提供入口。
    val forwardable = msg?.elements?.any {
        it.faceElement != null || it.marketFaceElement != null || it.videoElement != null ||
            it.pttElement != null || it.picElement != null || it.giphyElement != null ||
            it.faceBubbleElement != null
    } == true
    // 图片消息的"分享"就是转发到其它会话，改名为"转发"。
    var hasShare = false
    linear.forEachAll {
        if (it is AppCompatTextView && it.text?.toString() == "分享") {
            it.text = "转发"
            hasShare = true
        }
    }
    // 原生"分享/转发"(图片等)在本表上对部分消息会失败(感叹号)：原图本地文件缺失、秒传不触发。
    // 对我们能处理的类型，把原生分享行的点击改接到自己的 forwardMsgRecord(下载原图重建后重发)。
    if (forwardable && hasShare) {
        items["分享"]?.setOnClickListener {
            linear.forwardMsgRecord(msg!!)
            dismiss()
        }
    }
    // 文本消息没有分享按钮。注入一个真正的"转发"(打开好友选择器把文本发到其它会话)，
    // 注意复读文本只能在当前会话重发，不能转发到别处。
    val fwdText = msg?.elements
        ?.mapNotNull { it.textElement?.content }
        ?.joinToString("")
        ?.takeIf { it.isNotBlank() }
    // 原生"复制文本"用 android.widget.Toast 提示"已复制"，在超大 DPI 下显示异常。
    // 改为自己处理复制并用 QQ 原生 toast 提示。
    if (fwdText != null) {
        items["复制文本"]?.setOnClickListener {
            Utils.copyToClipboard(it.context, fwdText)
            dismiss()
        }
    }
    // 删除(本地删除，非撤回)：原生删除只更新数据库，当前会话列表不会实时刷新(需重进会话)。
    // 自行调用 deleteMsg(与原生一致：本地删除、无确认框)，并在成功回调里把该消息实时移除。
    val deleteId = msg?.msgId
    if (deleteId != null && deleteId != 0L) {
        items["删除"]?.setOnClickListener { v ->
            val contact = Contact(msg.chatType, msg.peerUid, "")
            runCatching {
                MsgUtil.msgService.deleteMsg(contact, arrayListOf(deleteId), IOperateCallback { code, reason ->
                    Utils.log("menu delete: id=$deleteId code=$code reason=$reason")
                    runOnUi {
                        if (code == 0) {
                            CurrentMsgList.removeLive(setOf(deleteId))
                            Utils.toast(v.context, "删除成功")
                        } else {
                            Utils.toast(v.context, "删除失败")
                        }
                    }
                })
            }.onFailure { Utils.log("menu delete failed: $it"); Utils.toast(v.context, "删除失败") }
            dismiss()
        }
    }
    Utils.log("menu inject: hasShare=$hasShare fwdText=${fwdText?.take(20)} elems=${msg?.elements?.map { it.elementType }}")
    if (!hasShare && fwdText != null) {
        // Clone the native menu item layout (icon + desc + switch) so it matches the other items.
        val shareIcon = ContextCompat.getDrawable(linear.context, 0x7e0805cd) // R.drawable.icon_share
        linear.addView(cloneMenuItem(linear, "转发", shareIcon) {
            linear.forwardText(fwdText)
            dismiss()
        }, 1)
    }
    // 没有原生分享行的可重发类型(视频/表情/语音等)：注入我们自己的"转发"。
    // 原生 forwardMsg 在本表产品上被网关静默拦截不投递，所以一律走 sendMsg 重发。
    Utils.log("menu inject: forwardable=$forwardable hasShare=$hasShare fwdTextNull=${fwdText == null}")
    if (forwardable && !hasShare && fwdText == null) {
        val shareIcon = ContextCompat.getDrawable(linear.context, 0x7e0805cd) // R.drawable.icon_share
        linear.addView(cloneMenuItem(linear, "转发", shareIcon) {
            linear.forwardMsgRecord(msg!!)
            dismiss()
        }, 1)
    }
    // 系统分享：把选中消息的文本/链接/图片/视频/语音通过系统分享面板发送到其它应用。
    val hasShareableMedia = msg?.elements?.any {
        it.picElement != null || it.videoElement != null || it.pttElement != null
    } == true
    if (fwdText != null || hasShareableMedia) {
        val sysShareIcon = ContextCompat.getDrawable(linear.context, 0x7e0805cd) // R.drawable.icon_share
        linear.addView(cloneMenuItem(linear, "系统分享", sysShareIcon) {
            linear.shareMessage(msg!!, msgItem)
            dismiss()
        }, 1)
    }
    // 编辑：仅对自己发出的文本消息生效。打开输入法页面（同复读）预填原文，发送时先撤回原消息。
    val isSelf = msg != null && msg.senderUid == SelfContact.peerUid
    if (isSelf && fwdText != null) {
        val editId = msg!!.msgId
        linear.addView(cloneMenuItem(linear, "编辑", editIconDrawable()) {
            MessageEdit.begin(editId, fwdText)
            dismiss()
        }, 1)
    }
    // 撤回(群管理员/群主)：撤回其它成员的消息。自己的消息由原生菜单的撤回处理，这里只对他人消息生效。
    // 注意"删除"是本地删除(非撤回)，无法替代此功能。
    val recallId = msg?.msgId
    if (recallId != null && recallId != 0L && !isSelf && CurrentContact.isGroup) {
        CurrentGroupMembers.get(SelfContact.peerUid) { self ->
            if (self.role == MemberRole.OWNER || self.role == MemberRole.ADMIN) {
                runOnUi {
                    val recallIcon = ContextCompat.getDrawable(linear.context, 0x7e080b3d) // R.drawable.qui_recall
                    linear.addView(cloneMenuItem(linear, "撤回", recallIcon) {
                        runCatching {
                            KernelServiceUtil.c()?.recallMsg(CurrentContact, recallId, null)
                        }.onFailure { Utils.log("menu recall failed: $it") }
                        dismiss()
                    }, 1)
                }
            }
        }
    }
    if (Utils.isRoundScreen) {
        LinearScope(linear).add<View>()
            .width(FILL)
            .height(0.16f.vh)
    }
    // Unify the menu item card margins with the rest of the app.
    linear.normalizeListCards()
}

@Mixin
class 长按菜单调整(p0: (MenuItemFactory.ItemEnum) -> Unit, p1: String?) :
    AIOLongClickMenuFragment(p0, p1) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // For menus we create ourselves (e.g. the forward-history view) the callback
        // is not the native cell callback, so resolving the cell may fail — degrade
        // gracefully and just skip the cell-dependent parts (the 撤回 button).
        val item = runCatching {
            val field = this.b.javaClass.getDeclaredField("b")
            field.isAccessible = true
            val cell = field.get(this.b) as WatchAIOGroupWidgetItemCell<*, *>
            cell.f()
        }.getOrNull() ?: runCatching {
            val msgId = arguments?.getLong("key_msg_id") ?: 0L
            CurrentMsgList.msgList.value.find { it.d.msgId == msgId }
        }.getOrNull()
        // Inside a 合并转发聊天记录 viewer the inner records aren't in CurrentMsgList; fall back to
        // the history registry (no WatchAIOMsgItem available there, so msgItem stays null).
        val msg = item?.d ?: HistoryMsgRegistry.find(arguments?.getLong("key_msg_id") ?: 0L)
        Utils.log("menu: msg=${msg != null} msgId=${arguments?.getLong("key_msg_id")}")
        return super.onCreateView(inflater, container, savedInstanceState).apply {
            this.asGroup().getChildAt(0).asGroup().let { group ->
                process(group, msg, item) { dismiss() }
            }
        }
    }
}