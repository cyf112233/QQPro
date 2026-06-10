package momoi.mod.qqpro.hook.contact

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.tencent.qqnt.watch.contact.mvi.ContactListIntent
import com.tencent.qqnt.watch.contact.ui.ContactListFragment
import com.tencent.qqnt.watch.contact.ui.item.AddFriendItem
import com.tencent.qqnt.watch.contact.ui.item.ContactBaseItem
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.hook.findNavControllerFromTree
import momoi.mod.qqpro.util.Utils

/**
 * Contacts page (2nd main page) redesign for a small screen — see [Settings.contactSections].
 *
 * The stock list is a flat [加好友/群聊, 我的通知, ...friends..., ...groups...] with a single
 * combined notification entry whose navigation opens an ambiguous selector. This hook:
 *  - inserts "好友" / "群聊" section headers so friends and groups are visually grouped
 *    (which is why [GroupItemHook] can drop the redundant trailing icon on every group row);
 *  - replaces the single "我的通知" with two entries, "好友通知" and "群通知", each carrying its own
 *    unread count and navigating straight to the right screen (handled in [e0]).
 *
 * Implemented by observing the same view-model state and re-submitting a rebuilt list to the
 * adapter (subclassing the ListAdapter/ViewModel directly is unsafe — their generic bridge methods
 * collide under the bytecode patcher). The stock observer still runs first; our submitList wins.
 *
 * The target classes are R8-minified (no usable Kotlin metadata), so obfuscated fields are read by
 * their raw single-letter names via reflection:
 *  - ContactListFragment.h = viewModel (BaseViewModel), .i = adapter (ListAdapter)
 *  - BaseViewModel.c = mUiState (LiveData<ContactListState>)
 *  - ContactListState.a = friends (List<ContactItem>), .b = groups (List<GroupItem>), .c = combined count
 *  - ContactVM.e = repo (ContactRepo); ContactRepo.h = friend unread, .i = group unread
 *  - ContactListIntent.OnUseClick.a = the clicked item
 */
@Mixin
class ContactListFragmentHook : ContactListFragment() {

    // The list RecyclerView, captured in Y(), plus a one-shot flag to reset scroll to the top.
    private var recycler: RecyclerView? = null
    private var initialTopPending = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Settings.contactSections.value) return
        runCatching {
            val viewModel = field("h")!!
            val adapter = field("i")!!
            @Suppress("UNCHECKED_CAST")
            val liveData = viewModel.field("c") as LiveData<Any?>
            liveData.observe(this, Observer { state ->
                state ?: return@Observer
                runCatching {
                    val list = rebuild(state, viewModel)
                    // Items beyond the fixed header (加好友 + 两条通知) mean real contacts arrived.
                    val hasContent = list.size > 3
                    submitList(adapter, list) {
                        // The rebuilt list is submitted after the stock one; DiffUtil otherwise keeps
                        // the prior anchor item, leaving a fresh open scrolled into the middle. Force
                        // the top once, on the first content-bearing submit.
                        if (initialTopPending) {
                            recycler?.scrollToPosition(0)
                            if (hasContent) initialTopPending = false
                        }
                    }
                }.onFailure { Utils.log("ContactListFragmentHook: rebuild/submit failed: $it") }
            })
        }.onFailure { Utils.log("ContactListFragmentHook onCreate: $it") }
    }

    override fun Y(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = super.Y(inflater, container, savedInstanceState)
        if (Settings.contactSections.value && v is RecyclerView) {
            recycler = v
            runCatching { v.addOnChildAttachStateChangeListener(HeaderStyler(v, field("i")!!)) }
                .onFailure { Utils.log("ContactListFragmentHook Y: $it") }
        }
        return v
    }

    private fun rebuild(state: Any, viewModel: Any): List<ContactBaseItem> {
        @Suppress("UNCHECKED_CAST")
        val friends = state.field("a") as List<ContactBaseItem>
        @Suppress("UNCHECKED_CAST")
        val groups = state.field("b") as List<ContactBaseItem>
        val (friendCount, groupCount) = readNotifyCounts(state, viewModel)

        val out = ArrayList<ContactBaseItem>(friends.size + groups.size + 5)
        out.add(AddFriendItem())                       // 加好友/群聊
        out.add(FriendNotifyItem(friendCount))         // replaces the combined 我的通知 ...
        out.add(GroupNotifyItem(groupCount))           // ... with two split entries
        if (friends.isNotEmpty()) {
            out.add(SectionHeaderItem("好友"))
            out.addAll(friends)
        }
        if (groups.isNotEmpty()) {
            out.add(SectionHeaderItem("群聊"))
            out.addAll(groups)
        }
        return out
    }

    /** Friend vs group notification counts. The repo (ContactVM.e) tracks them separately as
     *  ContactRepo.h / .i; fall back to the combined state count (ContactListState.c) if unreadable. */
    private fun readNotifyCounts(state: Any, viewModel: Any): Pair<Int, Int> = runCatching {
        val repo = viewModel.field("e")!!
        (repo.field("h") as Int) to (repo.field("i") as Int)
    }.getOrElse { (state.field("c") as? Int ?: 0) to 0 }

    override fun e0(intent: ContactListIntent) {
        if (Settings.contactSections.value && intent is ContactListIntent.OnUseClick) {
            when (runCatching { intent.field("a") }.getOrNull()) {
                is FriendNotifyItem -> {
                    // add_friend graph, type=5 → friend notification screen
                    navigate("select_fragment_to_add_friend", Bundle().apply { putInt("type", 5) })
                    return
                }
                is GroupNotifyItem -> {
                    // troop graph, NAVIGATE_TYPE=3 → group notification screen
                    navigate("aio_fragment_to_troop_nav", Bundle().apply { putInt("NAVIGATE_TYPE", 3) })
                    return
                }
            }
        }
        super.e0(intent)
    }

    /** Navigate a nav-graph action (resolved by resource name) via the obfuscated NavController. */
    private fun navigate(actionResName: String, args: Bundle) {
        runCatching {
            val v = view ?: return
            val nav = v.findNavControllerFromTree() ?: run {
                Utils.log("ContactListFragmentHook: NavController not found"); return
            }
            val actionId = v.context.resources.getIdentifier(actionResName, "id", v.context.packageName)
            if (actionId == 0) { Utils.log("ContactListFragmentHook: id $actionResName not found"); return }
            // navigate(int destId, Bundle args, NavOptions options) — name obfuscated.
            val navigate = nav.javaClass.methods.firstOrNull { m ->
                val p = m.parameterTypes
                p.size == 3 && p[0] == Int::class.javaPrimitiveType && p[1] == Bundle::class.java
            } ?: run { Utils.log("ContactListFragmentHook: navigate(int,Bundle,..) not found"); return }
            navigate.invoke(nav, actionId, args, null)
        }.onFailure { Utils.log("ContactListFragmentHook navigate $actionResName: $it") }
    }

    private fun submitList(adapter: Any, list: List<ContactBaseItem>, commit: Runnable? = null) {
        if (commit != null) {
            // ListAdapter.submitList(List, Runnable) runs the callback after the diff is applied.
            val ok = runCatching {
                adapter.javaClass.getMethod("submitList", List::class.java, Runnable::class.java)
                    .invoke(adapter, list, commit)
            }.isSuccess
            if (ok) return
            // R8 may have stripped the 2-arg overload — submit then post the callback instead.
            adapter.javaClass.getMethod("submitList", List::class.java).invoke(adapter, list)
            recycler?.post(commit)
            return
        }
        adapter.javaClass.getMethod("submitList", List::class.java).invoke(adapter, list)
    }

    /** Read an obfuscated public field by its raw name (searches the class hierarchy). */
    private fun Any.field(name: String): Any? = javaClass.getField(name).get(this)
}
