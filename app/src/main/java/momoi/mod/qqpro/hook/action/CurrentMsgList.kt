package momoi.mod.qqpro.hook.action

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tencent.aio.api.factory.IAIOFactory
import com.tencent.aio.api.list.IListUIOperationApi
import com.tencent.aio.base.chat.ChatPie
import com.tencent.aio.base.mvi.part.MsgListUiState
import com.tencent.aio.main.fragment.ChatFragment
import com.tencent.aio.part.root.panel.content.firstLevel.msglist.mvx.intent.MsgListDataIntent
import com.tencent.watch.aio_impl.coreImpl.vb.WatchAIOListVB
import com.tencent.watch.aio_impl.data.WatchAIOMsgItem
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.lib.Observable
import momoi.mod.qqpro.util.ThreadManager
import momoi.mod.qqpro.util.Utils
import java.util.LinkedList

object CurrentMsgList {
    lateinit var vb: WatchAIOListVB
        private set
    var msgList = Observable(mutableListOf<WatchAIOMsgItem>())
        private set

    fun getMsgIndex(msg: WatchAIOMsgItem): Int {
        return msgList.value.indexOf(msg)
    }

    private var isLoadingMsg = false
    private fun loadMoreMsg() {
        if (!isLoadingMsg) {
            msgList.observeOnce {
                isLoadingMsg = false
            }
            isLoadingMsg = true
            Utils.log("Load more msg. currentSize: ${msgList.value.size}")
            vb.L(MsgListDataIntent.LoadTopPage("WatchAIOListVB"))
        }
    }

    fun upwardMsg(current: Int, count: Int, callback: (Int) -> Unit) {
        val target = msgList.value.size - 1 - current + count
        upwardMsgInternal(target, callback)
    }

    private fun upwardMsgInternal(target: Int, callback: (Int) -> Unit) {
        if (msgList.value.size < target) {
            msgList.observeOnce {
                upwardMsgInternal(target, callback)
            }
            loadMoreMsg()
        } else {
            callback(msgList.value.size - target - 1)
        }
    }

    fun findMsg(
        seq: Long,
        result: (WatchAIOMsgItem?) -> Unit,
        repeatCount: Int = 1000
    ) {
        val msg = msgList.value.find { it.d.msgSeq == seq }
        if (msg != null) {
            result(msg)
            return
        }
        if (repeatCount <= 0) {
            Utils.log("findMsg: give up (repeat exhausted) seq=$seq")
            result(null)
            return
        }
        // Load older messages and retry. Two ways to stop instead of hanging forever:
        // 1) the list stopped growing after a load -> we reached the top of history;
        // 2) no update arrived within the timeout -> load is stuck / nothing to load.
        val sizeBefore = msgList.value.size
        var settled = false
        msgList.observeOnce {
            if (settled) return@observeOnce
            settled = true
            if (msgList.value.size <= sizeBefore) {
                // Reached the top of history without finding the target.
                Utils.log("findMsg: reached top of history, seq=$seq not found")
                result(null)
            } else {
                findMsg(seq, result, repeatCount - 1)
            }
        }
        ThreadManager.runOnUiThread({
            if (settled) return@runOnUiThread
            settled = true
            Utils.log("findMsg: timed out waiting for more msgs, seq=$seq")
            result(null)
        }, 3000L)
        loadMoreMsg()
    }

    @Mixin
    class Hook : WatchAIOListVB() {
        @Suppress("UNCHECKED_CAST")
        override fun n(state: MsgListUiState, uiHelper: IListUIOperationApi) {
            vb = this
            val msg = msgList.value
            val list = state as LinkedList<WatchAIOMsgItem>
            var insertIndex = -1
            while (true) {
                val last = list.pollLast()
                if (last == null) {
                    list.addAll(msg)
                    break
                }
                val index = msg.indexOfLast { last.d.msgId == it.d.msgId }
                if (index == -1) {
                    if (insertIndex == -1) {
                        msg.add(last)
                        insertIndex = msg.lastIndex
                    } else {
                        msg.add(insertIndex, last)
                    }
                } else {
                    msg[index] = last
                    //if (insertIndex == -1) {
                    //    insertIndex = 0
                    //}
                    //for (i in insertIndex until msg.size) {
                    //    msg[i].checkAndSetSameSender(msg.getOrNull(i-1))
                    //}
                    list.addAll(msg.subList(index, msg.size))
                    break
                }
            }
            msgList.update(list.toMutableList())
            super.n(list as MsgListUiState, uiHelper)
        }
    }

    @Mixin
    class Clear(p0: IAIOFactory) : ChatPie(p0) {
        override fun a(
            fragment: ChatFragment,
            inflater: LayoutInflater,
            container: ViewGroup,
            isPreload: Boolean
        ): View {
            msgList = Observable(ArrayList())
            return super.a(fragment, inflater, container, isPreload)
        }
    }

}