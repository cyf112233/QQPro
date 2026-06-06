package momoi.mod.qqpro.hook.view

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.watch.aio_impl.data.WatchAIOMsgItem
import com.tencent.watch.aio_impl.ui.frames.SettingFrame
import loadPicUrl
import momoi.mod.qqpro.enums.ElementType
import momoi.mod.qqpro.msg.getImageUrl
import momoi.mod.qqpro.hook.action.CurrentMsgList
import momoi.mod.qqpro.hook.style.cardMargin
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.clickable
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.gravity
import momoi.mod.qqpro.lib.linearLayout
import momoi.mod.qqpro.lib.margin
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.text
import momoi.mod.qqpro.lib.textColor
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.lib.vertical
import momoi.mod.qqpro.lib.width
import momoi.mod.qqpro.util.Utils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ACCENT = 0xFF_4FC3F7.toInt()
private val BG = 0xF0_121212.toInt()
private const val ICON_SEARCH = 0x7e0805ca // R.drawable.icon_search

private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

/** Element types treated as "其他" (file / voice / card / forward / location …). */
private val OTHER_TYPES = setOf(
    ElementType.FILE,
    ElementType.PTT,
    ElementType.ARK,
    ElementType.STRUCT_LONG_MSG,
    ElementType.MARKDOWN,
    ElementType.MULTI_FORWARD,
    ElementType.WALLET,
    ElementType.LIVE_GIFT,
    ElementType.SHARE_LOCATION,
    ElementType.CALENDAR,
)

private const val ENTRY_LABEL = "搜索聊天记录"

private enum class SearchType(val label: String) {
    TEXT("文本"),
    MEDIA("图片 / 视频"),
    OTHER("其他文件"),
    DATE("按日期"),
}

/**
 * Add a "搜索聊天记录" entry to the chat's rightmost settings page ([SettingFrame]), shown for
 * both group and DM chats (alongside 群成员/群设置/退出群 or 消息设置/删除好友). Tapping it opens
 * [ChatSearchFragment]. The row reuses the native `setting_item` layout so it matches the other
 * entries visually. Called from the SettingFrame hook's onViewCreated.
 */
fun addChatSearchEntry(fragment: SettingFrame) {
    runCatching {
        val scroll = fragment.i ?: return
        val container = scroll.getChildAt(0) as? LinearLayout ?: return
        val ctx = fragment.requireContext()
        val res = ctx.resources
        val pkg = ctx.packageName
        val descId = res.getIdentifier("desc", "id", pkg)
        // onViewCreated may fire again (e.g. returning to the page) on the same container —
        // guard against appending a duplicate entry row each time.
        for (i in 0 until container.childCount) {
            val desc = container.getChildAt(i).findViewById<TextView>(descId)
            if (desc?.text?.toString() == ENTRY_LABEL) return
        }
        val layoutId = res.getIdentifier("setting_item", "layout", pkg)
        if (layoutId == 0) {
            Utils.log("ChatSearch: setting_item layout not found")
            return
        }
        val row = LayoutInflater.from(ctx).inflate(layoutId, container, false)
        row.findViewById<ImageView>(res.getIdentifier("icon", "id", pkg))?.setImageResource(ICON_SEARCH)
        row.findViewById<TextView>(descId)?.text = ENTRY_LABEL
        row.setOnClickListener {
            runCatching {
                ChatSearchFragment().show(fragment.childFragmentManager, "chat_search")
            }.onFailure { Utils.log("ChatSearch: open failed: $it") }
        }
        // Header views are avatar(0), nick(1), peerId(2), info(3); insert before the menu items.
        container.addView(row, minOf(4, container.childCount))
        // normalizeListCards() already ran in SettingFrameMargins.Y (onCreateView), before this
        // row existed — so apply the unified card margin here too, or it keeps the native
        // setting_item XML margins and looks slightly off vs the other entries.
        row.cardMargin()
        Utils.log("ChatSearch: entry added")
    }.onFailure { Utils.log("ChatSearch: addEntry failed: $it") }
}

/**
 * Full-screen chat-history search for the watch. Flow:
 *  1. pick a type (文本 / 图片视频 / 其他 / 按日期);
 *  2. 文本 lets you type an optional keyword via the soft keyboard; 按日期 first lists the days
 *     that have messages so you can pick one;
 *  3. the whole chat history is paged into memory ([CurrentMsgList.loadAll]) and filtered;
 *  4. a results list is shown — tapping a hit closes search and scrolls the chat to that message
 *     (same jump infra as reply-source / first-unread).
 */
class ChatSearchFragment : MyDialogFragment() {

    private lateinit var root: LinearLayout

    /** Cleared when the dialog goes away, so an in-flight loadAll stops instead of leaking. */
    private var active = true

    override fun onDestroyView() {
        active = false
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        root = LinearLayout(inflater.context).vertical()
        root.layoutParams = ViewGroup.LayoutParams(FILL, FILL)
        root.setBackgroundColor(BG)
        showMenu()
        return root
    }

    // ---- screens -------------------------------------------------------------

    private fun showMenu() {
        root.removeAllViews()
        root.gravity = Gravity.NO_GRAVITY
        scrollColumn {
            title("搜索聊天记录")
            for (type in SearchType.values()) {
                button(type.label, 0xFF_2A2A2A.toInt(), 0xFF_FFFFFF.toInt()) {
                    when (type) {
                        SearchType.TEXT -> showTextInput()
                        SearchType.DATE -> startDateFlow()
                        else -> startSearch(type, null, null)
                    }
                }
            }
            button("取消", 0xFF_1A1A1A.toInt(), 0xFF_999999.toInt()) { dismiss() }
        }
    }

    private fun showTextInput() {
        root.removeAllViews()
        root.gravity = Gravity.NO_GRAVITY
        val ctx = requireContext()
        lateinit var input: EditText
        scrollColumn {
            title("搜索文本")
            input = add<EditText>()
                .textSize(14f)
                .textColor(0xFF_FFFFFF)
                .width(FILL)
                .padding(10.dp)
                .apply {
                    hint = "关键词(可留空)"
                    setHintTextColor(0xFF_777777.toInt())
                    setSingleLine()
                    background = GradientDrawable().apply {
                        setColor(0xFF_222222.toInt())
                        cornerRadius = 12.dp.toFloat()
                    }
                }
                .margin(bottom = 10.dp)
            button("搜索", ACCENT, 0xFF_000000.toInt()) {
                hideKeyboard(input)
                startSearch(SearchType.TEXT, input.text?.toString()?.trim().orEmpty(), null)
            }
            button("返回", 0xFF_1A1A1A.toInt(), 0xFF_999999.toInt()) {
                hideKeyboard(input)
                showMenu()
            }
        }
        input.requestFocus()
        input.post {
            (ctx.getSystemService(InputMethodManager::class.java))
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun startDateFlow() {
        showLoading()
        CurrentMsgList.loadAll(onProgress = ::updateLoading, shouldContinue = { isAdded && active }) {
            if (!isAdded || !active) return@loadAll // dialog dismissed while loading
            val days = CurrentMsgList.msgList.value
                .asSequence()
                .filter { it.d.elements.isNotEmpty() }
                .map { dayFmt.format(Date(it.d.msgTime * 1000)) }
                .distinct()
                .sortedDescending()
                .toList()
            showDateList(days)
        }
    }

    private fun showDateList(days: List<String>) {
        root.removeAllViews()
        root.gravity = Gravity.NO_GRAVITY
        if (days.isEmpty()) {
            showEmpty("没有可选日期")
            return
        }
        root.content {
            title("选择日期")
            val list = add<androidx.recyclerview.widget.RecyclerView>().linearLayout()
            (list.layoutParams as LinearLayout.LayoutParams).apply {
                width = FILL; height = 0; weight = 1f
            }
            list.content(
                data = days,
                factory = { simpleRow() },
                update = { day ->
                    (getChildAt(0) as TextView).text = day
                    clickable { startSearch(SearchType.DATE, null, day) }
                }
            )
        }
    }

    private fun startSearch(type: SearchType, keyword: String?, day: String?) {
        showLoading()
        CurrentMsgList.loadAll(onProgress = ::updateLoading, shouldContinue = { isAdded && active }) {
            if (!isAdded || !active) return@loadAll // dialog dismissed while loading
            val hits = CurrentMsgList.msgList.value.filter { matches(it, type, keyword, day) }
            Utils.log("ChatSearch: type=$type keyword=$keyword day=$day hits=${hits.size}")
            showResults(hits, withPreview = type == SearchType.MEDIA)
        }
    }

    private fun showResults(hits: List<WatchAIOMsgItem>, withPreview: Boolean) {
        root.removeAllViews()
        root.gravity = Gravity.NO_GRAVITY
        if (hits.isEmpty()) {
            showEmpty("没有匹配的消息")
            return
        }
        // Newest first.
        val data = hits.reversed()
        root.content {
            title("搜索结果 (${data.size})")
            val list = add<androidx.recyclerview.widget.RecyclerView>().linearLayout()
            (list.layoutParams as LinearLayout.LayoutParams).apply {
                width = FILL; height = 0; weight = 1f
            }
            list.content(
                data = data,
                factory = { resultRow(withPreview) },
                update = { item ->
                    if (withPreview) {
                        bindPreview(getChildAt(0) as ImageView, item.d)
                        val texts = getChildAt(1) as LinearLayout
                        // stub field names: l = showNickName, k = time
                        (texts.getChildAt(0) as TextView).text = "${item.l}  ·  ${item.k}"
                        (texts.getChildAt(1) as TextView).text = buildSnippet(item.d)
                    } else {
                        // stub field names: l = showNickName, k = time
                        (getChildAt(0) as TextView).text = "${item.l}  ·  ${item.k}"
                        (getChildAt(1) as TextView).text = buildSnippet(item.d)
                    }
                    clickable { jumpTo(item) }
                }
            )
        }
    }

    /** Load the first image thumbnail of [rec] into [iv] (video / unresolvable → dark placeholder). */
    private fun bindPreview(iv: ImageView, rec: MsgRecord) {
        iv.setImageDrawable(null)
        iv.setBackgroundColor(0xFF_222222.toInt())
        val pic = rec.elements.firstOrNull { it.elementType == ElementType.PIC }?.picElement
        // getImageUrl() throws if originImageUrl is null (Kotlin non-null intrinsic), so guard it.
        val url = pic?.let { runCatching { it.getImageUrl() }.getOrNull() }
        if (!url.isNullOrEmpty()) {
            runCatching { iv.loadPicUrl(url, pic.md5HexStr ?: url) }
                .onFailure { Utils.log("ChatSearch: preview load failed: $it") }
        } else {
            iv.contentDescription = "视频" // video or no downloadable thumb
        }
    }

    private fun showLoading() {
        root.removeAllViews()
        root.gravity = Gravity.CENTER
        root.content {
            add<TextView>()
                .text("加载聊天记录…")
                .textSize(14f)
                .textColor(0xFF_FFFFFF)
                .gravity(Gravity.CENTER)
                .apply { tag = "loading" }
        }
    }

    private fun updateLoading(count: Int) {
        if (!isAdded || !active) return
        (root.findViewWithTag<TextView>("loading"))?.text = "加载聊天记录… ($count)"
    }

    private fun showEmpty(msg: String) {
        root.removeAllViews()
        root.gravity = Gravity.NO_GRAVITY
        scrollColumn {
            title(msg)
            button("返回", 0xFF_2A2A2A.toInt(), 0xFF_FFFFFF.toInt()) { showMenu() }
        }
    }

    /** A vertically-centred, scrollable column (so menus fit a square screen). */
    private fun scrollColumn(block: momoi.mod.qqpro.lib.LinearScope.() -> Unit) {
        val ctx = requireContext()
        val sv = ScrollView(ctx)
        sv.isFillViewport = true
        sv.layoutParams = LinearLayout.LayoutParams(FILL, 0, 1f)
        val col = LinearLayout(ctx).vertical()
        col.gravity = Gravity.CENTER
        col.setPadding(16.dp, 8.dp, 16.dp, 16.dp)
        col.layoutParams = ViewGroup.LayoutParams(FILL, ViewGroup.LayoutParams.WRAP_CONTENT)
        sv.addView(col)
        root.addView(sv)
        col.content(block)
    }

    // ---- actions -------------------------------------------------------------

    private fun jumpTo(item: WatchAIOMsgItem) {
        // Capture the decor view before dismiss() detaches us (activity becomes null afterwards).
        val decor = activity?.window?.decorView
        val rv = runCatching { CurrentMsgList.vb.H }.getOrNull()
        val index = CurrentMsgList.getMsgIndex(item)
        dismiss()
        runCatching {
            // The search entry lives on the rightmost settings page; bring the chat page (page 0)
            // back to the front, same as a photo/video send does (see WatchAIOPageReset / InputMethodFragmentHook).
            if (decor != null) switchToChatPage(decor)
            BubbleTextView.beginJumpUp()
            // Instant jump — smoothScrollToStart animates item-by-item and crawls over long distances.
            if (rv != null && index >= 0) rv.post { rv.scrollToStartInstant(index) }
        }.onFailure { Utils.log("ChatSearch: jump failed: $it") }
    }

    // ---- ui helpers ----------------------------------------------------------

    private fun momoi.mod.qqpro.lib.GroupScope.title(label: String) {
        add<TextView>()
            .text(label)
            .textSize(15f)
            .textColor(0xFF_FFFFFF)
            .gravity(Gravity.CENTER)
            .width(FILL)
            .padding(top = 14.dp, bottom = 12.dp)
    }

    private fun momoi.mod.qqpro.lib.GroupScope.button(
        label: String,
        bg: Int,
        fg: Int,
        onClick: () -> Unit
    ) {
        add<TextView>()
            .text(label)
            .textSize(14f)
            .textColor(fg)
            .gravity(Gravity.CENTER)
            .width(FILL)
            .padding(top = 12.dp, bottom = 12.dp)
            .apply {
                background = GradientDrawable().apply {
                    setColor(bg)
                    cornerRadius = 22.dp.toFloat()
                }
            }
            .margin(top = 6.dp)
            .clickable(onClick)
    }

    /** A single-line tappable row (used for the date list). */
    private fun simpleRow(): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).vertical()
        row.layoutParams = LinearLayout.LayoutParams(FILL, ViewGroup.LayoutParams.WRAP_CONTENT)
        row.setPadding(18.dp, 12.dp, 18.dp, 12.dp)
        val tv = TextView(ctx)
        tv.textSize = 14f
        tv.setTextColor(0xFF_FFFFFF.toInt())
        row.addView(tv)
        return row
    }

    /**
     * A result row. Without preview: two stacked text lines (sender·time, snippet).
     * With preview: a thumbnail on the left + the two text lines in a column on the right
     * (children: [0]=ImageView, [1]=LinearLayout of the two TextViews).
     */
    private fun resultRow(withPreview: Boolean): View {
        val ctx = requireContext()

        fun head() = TextView(ctx).apply {
            textSize = 11f; setTextColor(ACCENT); setSingleLine()
        }
        fun body() = TextView(ctx).apply {
            textSize = 14f; setTextColor(0xFF_EEEEEE.toInt()); maxLines = 2
        }

        if (!withPreview) {
            val row = LinearLayout(ctx).vertical()
            row.layoutParams = LinearLayout.LayoutParams(FILL, ViewGroup.LayoutParams.WRAP_CONTENT)
            row.setPadding(16.dp, 10.dp, 16.dp, 10.dp)
            row.addView(head())
            row.addView(body().apply {
                layoutParams = LinearLayout.LayoutParams(FILL, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = 3.dp }
            })
            return row
        }

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = LinearLayout.LayoutParams(FILL, ViewGroup.LayoutParams.WRAP_CONTENT)
        row.setPadding(14.dp, 8.dp, 14.dp, 8.dp)

        val thumb = ImageView(ctx)
        thumb.layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp)
        thumb.scaleType = ImageView.ScaleType.CENTER_CROP
        thumb.maxHeight = 48.dp // loadPicElement requires maxHeight != 0
        thumb.adjustViewBounds = false
        row.addView(thumb)

        val texts = LinearLayout(ctx).vertical()
        texts.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = 12.dp }
        texts.addView(head())
        texts.addView(body().apply {
            layoutParams = LinearLayout.LayoutParams(FILL, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = 3.dp }
        })
        row.addView(texts)
        return row
    }

    private fun hideKeyboard(view: View) {
        runCatching {
            (requireContext().getSystemService(InputMethodManager::class.java))
                ?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}

// ---- page switching ----------------------------------------------------------

/** Bring the AIO chat page (page 0 of the chat ViewPager2) to the front. */
private fun switchToChatPage(root: View) {
    val vp = findViewPager2(root) ?: run { Utils.log("ChatSearch: ViewPager2 not found"); return }
    // The bundled (R8-minified) ViewPager2 only exposes setCurrentItem(int).
    runCatching { vp.javaClass.getMethod("setCurrentItem", Int::class.java).invoke(vp, 0) }
        .onFailure { Utils.log("ChatSearch: switchToChatPage failed: $it") }
}

private fun findViewPager2(root: View): View? {
    if (root.javaClass.name.endsWith("ViewPager2")) return root
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) findViewPager2(root.getChildAt(i))?.let { return it }
    }
    return null
}

// ---- filtering ---------------------------------------------------------------

private fun matches(
    item: WatchAIOMsgItem,
    type: SearchType,
    keyword: String?,
    day: String?
): Boolean {
    val rec = item.d
    val elements = rec.elements
    if (elements.isEmpty()) return false
    if (elements[0].elementType == ElementType.GREY_TIP) return false // skip recall/poke tips
    return when (type) {
        SearchType.TEXT -> {
            val text = textOf(rec)
            if (text.isBlank()) false
            else if (keyword.isNullOrBlank()) true
            else text.contains(keyword, ignoreCase = true)
        }
        SearchType.MEDIA -> elements.any {
            it.elementType == ElementType.PIC || it.elementType == ElementType.VIDEO
        }
        SearchType.OTHER -> elements.any { it.elementType in OTHER_TYPES }
        SearchType.DATE -> day != null && dayFmt.format(Date(rec.msgTime * 1000)) == day
    }
}

/** Concatenated text of all TEXT elements (for keyword matching). */
private fun textOf(rec: MsgRecord): String {
    val sb = StringBuilder()
    for (e in rec.elements) {
        if (e.elementType == ElementType.TEXT) sb.append(e.textElement?.content ?: "")
    }
    return sb.toString()
}

/**
 * Short human snippet for a result row. Does NOT mutate the elements (unlike MsgUtil.summary,
 * which nulls them out — that would corrupt the live messages in CurrentMsgList).
 */
private fun buildSnippet(rec: MsgRecord): String {
    val sb = StringBuilder()
    for (e in rec.elements) {
        when (e.elementType) {
            ElementType.TEXT -> sb.append(e.textElement?.content ?: "")
            ElementType.PIC -> sb.append("[图片]")
            ElementType.VIDEO -> sb.append("[视频]")
            ElementType.FILE -> sb.append("[文件]")
            ElementType.PTT -> sb.append("[语音]")
            ElementType.ARK -> sb.append("[卡片]")
            ElementType.MULTI_FORWARD -> sb.append("[聊天记录]")
            ElementType.MFACE, ElementType.FACE -> sb.append(e.marketFaceElement?.faceName ?: "[表情]")
            ElementType.SHARE_LOCATION -> sb.append("[位置]")
            ElementType.WALLET -> sb.append("[红包]")
            else -> {}
        }
    }
    val s = sb.toString().replace('\n', ' ').trim()
    return if (s.length > 60) s.take(60) + "…" else s
}
