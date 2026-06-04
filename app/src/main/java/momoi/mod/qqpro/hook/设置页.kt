package momoi.mod.qqpro.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.tencent.widget.Switch
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.Pref
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.hook.style.CARD_MARGIN_DP
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.lib.background
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.gravity
import momoi.mod.qqpro.lib.height
import momoi.mod.qqpro.lib.margin
import momoi.mod.qqpro.lib.onCheckedChange
import momoi.mod.qqpro.lib.onClick
import momoi.mod.qqpro.lib.onProgressChanged
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.progressMax
import momoi.mod.qqpro.lib.text
import momoi.mod.qqpro.lib.textColor
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.lib.vertical
import momoi.mod.qqpro.lib.width
import momoi.mod.qqpro.util.ChatBackground
import momoi.mod.qqpro.util.Utils
import moye.wearqq.SettingsActivity
import kotlin.math.roundToInt

private val ACCENT = 0xFF_4FC3F7.toInt()
private val TRACK_INACTIVE = 0xFF_3A3A3A.toInt()
private const val REQ_PICK_CHAT_BG = 0x9B01

// Kept at file scope (not a @Mixin field — those can't have initializers) so
// onActivityResult can refresh the picker's status text after picking/clearing.
private var bgStatusLabel: TextView? = null

@Mixin
class 设置页 : SettingsActivity() {
    @SuppressLint("ResourceType", "SetTextI18n", "UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(0xFF_121212.toInt())
        }
        val root = LinearLayout(this)
            .vertical()
            .padding(left = (2 * CARD_MARGIN_DP).dp, top = 10.dp, right = (2 * CARD_MARGIN_DP).dp, bottom = 10.dp)
        scroll.addView(root, FILL, WRAP)
        setContentView(scroll)

        root.content {
            section("NWear QQ 设置", "基础版 by 爅峫")
            switch("单行输入", "输入框固定为单行显示", Settings.singleLineInput)
            switch("图片随消息发送", "发送文字时一并发送已选图片", Settings.sendWithImage)
            switch("回复带艾特", "回复消息时自动艾特对方", Settings.replyWithAt)
            switch("双击朗读", "双击消息朗读文本", Settings.doubleSpeak)
            switch("双击回复", "双击消息进入回复", Settings.doubleReply)
            switch("允许通知", "允许显示消息通知", Settings.allowNotification)
            switch("常驻通知", "保留常驻通知（更耗电）", Settings.residentNotification)
            switch("震动提醒", "新消息时震动提醒", Settings.allowVibrate)
            textInput("语音键文字", "聊天页语音键上显示的文字", Settings.voiceBtnText)

            section("QQ Pro 设置", "增强版 by java30433")
            slider("缩放倍数", "整体界面缩放，重启后生效", Settings.scale)
            slider("聊天文本缩放", "聊天气泡内文字大小", Settings.chatScale)
            switch("平滑表冠滚动", "表冠滚动没有动画时开启", Settings.enableSmoothScroll)
            switch("屏蔽返回键", "用于把右滑当作返回的手表（如米兔）", Settings.blockBack)
            switch("输入键居中", "在聊天页面将输入键居中放置", Settings.swapCenterKeyboard)

            section("QQ Max 设置", "终极版 by AILIFE")
            switch("群聊显示头像", "在群聊消息中显示用户头像和两行昵称", Settings.showGroupAvatar)
            switch("合并连续消息头", "同一人连发多条时，只在第一条显示头像和昵称", Settings.hideRepeatedSender)
            switch("行内发送按钮", "输入页将发送键移到输入框右侧，左侧加关闭键取消发送", Settings.inlineSendButton)
            switch("聊天页直接输入", "在聊天页用输入框替换键盘键，有文字时麦克风键变发送键", Settings.inlineChatInput)
            slider("屏幕圆角直径", "在输入框左右各留出此宽度的空白，避免圆屏圆角裁切两侧按钮", Settings.screenCornerDiameter, min = 0f, max = 48f)
            switch("返回先回首页", "不在首页时按返回先滑回第一页，已在首页才退出", Settings.backToFirstPage)
            switch("点击链接确认", "点击消息中的链接时，弹窗询问是否用浏览器打开", Settings.confirmOpenLink)
            switch("识别无前缀链接", "同时识别不带 http(s):// 的网址，如 example.com/x", Settings.wideUrlMatch)
            switch("链接预览", "消息含链接时尝试解析网站图标、标题与简介，显示在消息下方", Settings.enableLinkPreview)
            slider("气泡圆角半径", "聊天气泡、合并转发/聊天记录块与回复块的圆角半径(dp)", Settings.bubbleCornerRadius, min = 0f, max = 24f)
            textInput("我的气泡颜色", "16进制如 #2B6CF6，留空为默认", Settings.bubbleColorSelf)
            textInput("对方气泡颜色", "16进制如 #2B6CF6，留空为默认", Settings.bubbleColorOther)
            chatBackgroundPicker()
            slider("背景变暗程度", "调暗背景图以便看清文字，重进聊天页生效", Settings.chatBgDarken, min = 0f, max = 0.9f)

            add<View>()
                .height(64.dp)
        }
    }

    private fun GroupScopeFix.section(title: String, subtitle: String) {
        add<TextView>()
            .text(title)
            .textSize(15f)
            .textColor(ACCENT)
            .padding(left = 4.dp, top = 14.dp, right = 4.dp, bottom = 0.dp)
        add<TextView>()
            .text(subtitle)
            .textSize(10f)
            .textColor(0xFF_888888)
            .padding(left = 4.dp, top = 0.dp, right = 4.dp, bottom = 6.dp)
    }

    private fun GroupScopeFix.switch(
        title: String,
        desc: String,
        pref: Pref<Boolean>
    ) = card { card ->
        card.content {
            titleColumn(title, desc).weight(1f)
            // The base app's own switch — the nicer styled toggle used by the native NWear settings.
            val sw = Switch(this@设置页, null)
            sw.isChecked = pref.value
            sw.onCheckedChange { pref.value = it }
            add(sw)
        }
    }

    private fun updateBgStatus() {
        bgStatusLabel?.text = if (ChatBackground.isSet()) "已设置背景图片" else "未设置（使用默认背景）"
    }

    private fun GroupScopeFix.chatBackgroundPicker() = card { card ->
        card.vertical()
        card.content {
            titleColumn("聊天背景图片", "选择一张图片作为聊天页背景").width(FILL)
            bgStatusLabel = add<TextView>()
                .textSize(11f)
                .textColor(0xFF_A1A1A1)
                .padding(top = 4.dp)
            updateBgStatus()
            add<LinearLayout>()
                .width(FILL)
                .padding(top = 8.dp)
                .content {
                    pillButton("选择图片", ACCENT) { pickChatBackground() }
                        .weight(1f)
                        .margin(right = 4.dp)
                    pillButton("清除", 0xFF_E57373.toInt()) {
                        ChatBackground.clear()
                        updateBgStatus()
                        Utils.toast(this@设置页, "已清除聊天背景")
                    }.weight(1f).margin(left = 4.dp)
                }
        }
    }

    private fun GroupScopeFix.pillButton(
        label: String,
        color: Int,
        onTap: () -> Unit
    ): TextView {
        val btn = add<TextView>()
            .text(label)
            .textSize(13f)
            .textColor(0xFF_FFFFFF)
            .gravity(Gravity.CENTER)
            .padding(top = 8.dp, bottom = 8.dp)
        btn.background(GradientDrawable().apply {
            setColor(color)
            cornerRadius = 18.dp.toFloat()
        })
        btn.onClick(onTap)
        return btn
    }

    private fun pickChatBackground() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "选择背景图片"), REQ_PICK_CHAT_BG)
        } catch (e: Exception) {
            Utils.log("pickChatBackground failed: ${e.javaClass.simpleName}: ${e.message}")
            Utils.toast(this, "无法打开图片选择器")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK_CHAT_BG) return
        val uri = data?.data
        if (resultCode == Activity.RESULT_OK && uri != null && ChatBackground.save(this, uri)) {
            Utils.toast(this, "已设置聊天背景")
        } else {
            Utils.toast(this, "设置失败")
        }
        updateBgStatus()
    }

    private fun GroupScopeFix.textInput(
        title: String,
        desc: String,
        pref: Pref<String>
    ) = card { card ->
        // Full-width input below the title/description — the right-aligned field is too
        // narrow to use on the watch screen.
        card.vertical()
        card.content {
            titleColumn(title, desc).width(FILL)
            add<EditText>()
                .text(pref.value)
                .textSize(13f)
                .textColor(0xFF_FFFFFF)
                .width(FILL)
                .doAfterTextChanged { pref.value = it?.toString() ?: "" }
        }
    }

    /** Value slider rendered full-width below the title row, for watch use. */
    private fun GroupScopeFix.slider(
        title: String,
        desc: String,
        pref: Pref<Float>,
        min: Float = 0.1f,
        max: Float = 1.2f
    ) = card { card ->
        card.vertical()
        lateinit var valueLabel: TextView
        card.content {
            add<LinearLayout>()
                .width(FILL)
                .content {
                    titleColumn(title, desc).weight(1f)
                    valueLabel = add<TextView>()
                        .text(format(pref.value))
                        .textSize(14f)
                        .textColor(ACCENT)
                        .gravity(Gravity.CENTER_VERTICAL)
                }
        }
        val steps = ((max - min) * 100).roundToInt()
        val seek = mdSeekBar()
            .progressMax(steps)
            .onProgressChanged { p, fromUser ->
                val v = min + p / 100f
                valueLabel.text = format(v)
                if (fromUser) pref.value = v
            }
        seek.progress = ((pref.value - min) * 100).roundToInt().coerceIn(0, steps)
        card.addView(seek, LinearLayout.LayoutParams(FILL, 36.dp).apply {
            topMargin = 4.dp
        })
    }

    /** Stock SeekBar dressed up MD3-style: thin rounded two-tone track + vertical pill thumb. */
    private fun mdSeekBar(): SeekBar {
        val trackH = 2.dp
        val inactive = GradientDrawable().apply {
            setColor(TRACK_INACTIVE)
            cornerRadius = trackH / 2f
            setSize(0, trackH)
        }
        val activeShape = GradientDrawable().apply {
            setColor(ACCENT)
            cornerRadius = trackH / 2f
            setSize(0, trackH)
        }
        val active = ClipDrawable(activeShape, Gravity.START, ClipDrawable.HORIZONTAL)
        val progress = LayerDrawable(arrayOf(inactive, active)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
        // MD3 vertical pill thumb: taller than wide, fully rounded.
        val thumbW = 5.dp
        val thumbH = 22.dp
        val thumb = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(ACCENT)
            cornerRadius = thumbW / 2f
            setSize(thumbW, thumbH)
        }
        return SeekBar(this@设置页).apply {
            progressDrawable = progress
            setThumb(thumb)
            thumbOffset = 0
            val v = thumbH / 2
            setPadding(10.dp, v, 10.dp, v)
        }
    }

    private fun format(v: Float) = String.format("%.2f", v)

    private fun GroupScopeFix.titleColumn(title: String, desc: String): LinearLayout {
        val column = add<LinearLayout>().vertical()
        column.content {
            add<TextView>()
                .text(title)
                .textSize(13f)
                .textColor(0xFF_FFFFFF)
            if (desc.isNotEmpty()) {
                add<TextView>()
                    .text(desc)
                    .textSize(10f)
                    .textColor(0xFF_A1A1A1)
            }
        }
        return column
    }

    /** A rounded dark card row with consistent margins. */
    private inline fun GroupScopeFix.card(block: (LinearLayout) -> Unit) {
        val card = add<LinearLayout>()
            .width(FILL)
            .padding(12.dp)
        card.background(GradientDrawable().apply {
            setColor(0xFF_242424.toInt())
            cornerRadius = 14.dp.toFloat()
        })
        card.margin(top = CARD_MARGIN_DP.dp, bottom = CARD_MARGIN_DP.dp)
        block(card)
    }
}

private typealias GroupScopeFix = momoi.mod.qqpro.lib.LinearScope
