package byd.cxkcxkckx

import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.msg.api.impl.MsgServiceImpl
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.util.Utils

@Mixin
class AntiRecall : MsgServiceImpl() {
    override fun deleteRecallMsg(
        contact: Contact,
        msgId: Long,
        callback: IOperateCallback?
    ) {
        if (Settings.antiRecall.value) {
            Utils.log("AntiRecall: block deleteRecallMsg peer=${contact.peerUid} msgId=$msgId")
            callback?.onResult(0, "")
            return
        }
        super.deleteRecallMsg(contact, msgId, callback)
    }

    override fun deleteRecallMsgForLocal(
        contact: Contact,
        msgId: Long,
        callback: IOperateCallback?
    ) {
        if (Settings.antiRecall.value) {
            Utils.log("AntiRecall: block deleteRecallMsgForLocal peer=${contact.peerUid} msgId=$msgId")
            callback?.onResult(0, "")
            return
        }
        super.deleteRecallMsgForLocal(contact, msgId, callback)
    }
}