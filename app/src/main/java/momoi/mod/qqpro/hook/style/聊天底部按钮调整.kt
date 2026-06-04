package momoi.mod.qqpro.hook.style

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import androidx.core.widget.doAfterTextChanged
import com.tencent.mobileqq.app.ThreadManagerV2
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.watch.aio_impl.coreImpl.vb.`InputBarController$inputContent$2`
import com.tencent.watch.aio_impl.coreImpl.vb.InputBarControllerKt
import com.tencent.watch.ime.util.ImeTextUtil
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.asGroup
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.drawable.sendIconDrawable
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.GroupScope
import momoi.mod.qqpro.lib.adjustViewBounds
import momoi.mod.qqpro.lib.background
import momoi.mod.qqpro.lib.bitmapDecodeAssets
import momoi.mod.qqpro.lib.clickable
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.create
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.dpf
import momoi.mod.qqpro.lib.gravity
import momoi.mod.qqpro.lib.height
import momoi.mod.qqpro.lib.hint
import momoi.mod.qqpro.lib.imageResource
import momoi.mod.qqpro.lib.margin
import momoi.mod.qqpro.lib.marginHorizontal
import momoi.mod.qqpro.lib.onFocusChange
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.paddingHorizontal
import momoi.mod.qqpro.lib.scaleType
import momoi.mod.qqpro.lib.size
import momoi.mod.qqpro.lib.text
import momoi.mod.qqpro.lib.textColor
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.util.Utils

@Mixin
class 聊天底部按钮调整() : `InputBarController$inputContent$2`() {
    @SuppressLint("ResourceType", "ClickableViewAccessibility")
    override fun invoke(): Any = (super.invoke() as ConstraintLayout).apply {
        forEach {
            it.visibility = View.INVISIBLE
        }
        val emoji = getChildAt(0)
        val keyboard = getChildAt(2)
        GroupScope(this).apply {
            val roundBg = roundCornerDrawable(0xFF_1B9AF7.toInt(), 9999f)
            val sideSpaceDp = Settings.screenCornerDiameter.value.toInt()
            add<LinearLayout>().size(FILL, FILL).apply {
                    if (Utils.isRoundScreen) {
                        paddingHorizontal((14.dp / Settings.scale.value).toInt())
                    }
                }.content {
                    val voice =
                        create<ImageView>().height(FILL).adjustViewBounds()
                            .bitmapDecodeAssets("pro/ic_voice.png").padding(6.dp)
                            .scaleType(ImageView.ScaleType.FIT_CENTER)
                    ThreadManagerV2.getUIHandlerV2().post {
                        b.e.invoke(voice)
                    }

                    if (Settings.inlineChatInput.value) {
                        // One large pill holds the emoji button (left), the EditText
                        // (middle) and the mic/send button (right). The emoji button
                        // hides while the keyboard is open; the mic turns into a send
                        // button whenever there is text to send.
                        val pill = create<LinearLayout>().height(FILL).weight(1f)
                            .background(roundCornerDrawable(0x22_FFFFFF, 9999f))
                            .gravity(Gravity.CENTER_VERTICAL)
                        val send = create<ImageView>().height(FILL).adjustViewBounds()
                            .padding(8.dp).scaleType(ImageView.ScaleType.FIT_CENTER).apply {
                                setImageDrawable(sendIconDrawable())
                                visibility = View.GONE
                            }
                        lateinit var emojiBtn: ImageView
                        lateinit var editText: EditText
                        pill.content {
                            emojiBtn = add<ImageView>().height(FILL).adjustViewBounds()
                                .scaleType(ImageView.ScaleType.FIT_CENTER)
                                .bitmapDecodeAssets("pro/ic_emoji.png").padding(8.dp)
                                .clickable { emoji.callOnClick() }
                            editText = add<EditText>().height(FILL).weight(1f)
                                .background(null)
                                .paddingHorizontal(4.dp)
                                .textColor(0xFF_FFFFFF).textSize(14f)
                                .gravity(Gravity.CENTER_VERTICAL)
                                .hint("说点什么...").apply {
                                    setHintTextColor(0x80_FFFFFF.toInt())
                                }
                            add(voice.background(null))
                            add(send)
                        }
                        send.clickable { sendInline(editText) }
                        editText.doAfterTextChanged {
                            val hasText = !it.isNullOrBlank()
                            emojiBtn.visibility = if (hasText) View.GONE else View.VISIBLE
                            voice.visibility = if (hasText) View.GONE else View.VISIBLE
                            send.visibility = if (hasText) View.VISIBLE else View.GONE
                        }
                        add(pill.marginHorizontal(sideSpaceDp.dp))
                    } else {
                        add<ImageView>().height(FILL).adjustViewBounds()
                            .scaleType(ImageView.ScaleType.FIT_CENTER).background(roundBg)
                            .bitmapDecodeAssets("pro/ic_emoji.png").padding(8.dp).clickable {
                                emoji.callOnClick()
                            }
                        voice.background(roundBg)
                        val input = if (Settings.text.isEmpty()) {
                            create<ImageView>().bitmapDecodeAssets("pro/ic_keyboard.png")
                                .scaleType(ImageView.ScaleType.FIT_CENTER).padding(8.dp)
                        } else {
                            create<TextView>().gravity(Gravity.CENTER).textSize(14f)
                                .textColor(0xFF_FFFFFF).text(Settings.text)
                        }.height(FILL).weight(1f)
                            .background(ContextCompat.getDrawable(context, 2114457248)).clickable {
                                keyboard.callOnClick()
                            }

                        if (Settings.swapCenterKeyboard.value) {
                            add(input.marginHorizontal(2.dp))
                            add(voice)
                        } else {
                            add(voice.marginHorizontal(2.dp))
                            add(input)
                        }
                    }
                }
        }
    }

    private fun sendInline(editText: EditText) {
        val text = editText.text?.toString().orEmpty()
        if (text.isBlank()) return
        runCatching {
            val elements = ImeTextUtil.a.b(text)
            val contact = Contact(
                CurrentContact.chatType,
                CurrentContact.peerUid,
                CurrentContact.guildId
            )
            MsgUtil.msgService.sendMsg(contact, 0L, elements, IOperateCallback { code, msg ->
                Utils.log("inline chat send result=$code msg=$msg")
            })
            editText.setText("")
        }.onFailure { Utils.log("inline chat send failed: $it") }
    }
}
