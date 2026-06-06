package momoi.mod.qqpro.hook.action

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tencent.aio.api.factory.IAIOFactory
import com.tencent.aio.base.chat.ChatPie
import com.tencent.aio.main.fragment.ChatFragment
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.MemberInfo
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.QQNT
import momoi.mod.qqpro.enums.ChatType

val Contact.isGroup get() = this.chatType == ChatType.GROUP
val CurrentContact = Contact(0, "", "")

object CurrentGroupMembers {
    var info: Map<String, MemberInfo>? = null
    val callbacks = mutableListOf<()-> Unit>()
    fun get(id: String, callback: (MemberInfo)-> Unit) {
        if (!CurrentContact.isGroup) {
            return
        }
        if (info == null) {
            callbacks.add {
                info?.get(id)?.let { callback(it) }
            }
            return
        }
        info?.get(id)?.let { callback(it) }
    }
}

@Mixin
class Hook(p0: IAIOFactory) : ChatPie(p0) {
    override fun a(
        fragment: ChatFragment,
        inflater: LayoutInflater,
        container: ViewGroup,
        isPreload: Boolean
    ): View {
        e?.b?.b?.let {
            CurrentContact.chatType = it.b
            CurrentContact.peerUid = it.c
            CurrentContact.guildId = it.d
            CurrentGroupMembers.info = null
            CurrentGroupMembers.callbacks.clear()
            if (it.b == ChatType.GROUP) {
                QQNT.Group.getMemberList(CurrentContact.peerUid.toLong()) {
                    CurrentGroupMembers.info = it.infos
                    CurrentGroupMembers.callbacks.forEach { it() }
                    CurrentGroupMembers.callbacks.clear()
                }
            }
        }
        return super.a(fragment, inflater, container, isPreload)
    }
}