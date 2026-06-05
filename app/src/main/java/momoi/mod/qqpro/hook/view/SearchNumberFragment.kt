package momoi.mod.qqpro.hook.view

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.clickable
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.gravity
import momoi.mod.qqpro.lib.margin
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.text
import momoi.mod.qqpro.lib.textColor
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.lib.vertical
import momoi.mod.qqpro.lib.width

private val ACCENT = 0xFF_4FC3F7.toInt()

/**
 * Full-screen confirmation shown when a bare number (6–15 digits) is tapped in a
 * message. Confirming runs [onConfirm], which opens the add-friend/group search
 * pad prefilled with the number. Mirrors [LinkOpenFragment] (a windowed
 * AlertDialog renders broken on the round watch screen).
 */
class SearchNumberFragment(
    private val number: String,
    private val onConfirm: () -> Unit
) : MyDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = inflater.context
        val root = LinearLayout(ctx)
            .vertical()
            .padding(20.dp)
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(0xF0_121212.toInt())

        root.content {
            add<TextView>()
                .text("搜索该号码")
                .textSize(16f)
                .textColor(0xFF_FFFFFF)
                .gravity(Gravity.CENTER)
                .padding(bottom = 10.dp)
            add<TextView>()
                .text(number)
                .textSize(15f)
                .textColor(0xFF_BBBBBB.toInt())
                .gravity(Gravity.CENTER)
                .padding(bottom = 14.dp)

            button("搜索好友/群", ACCENT, 0xFF_000000.toInt()) {
                onConfirm()
                dismiss()
            }
            button("取消", 0xFF_2A2A2A.toInt(), 0xFF_FFFFFF.toInt()) {
                dismiss()
            }
        }
        return root
    }

    private fun momoi.mod.qqpro.lib.LinearScope.button(
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
            .padding(top = 10.dp, bottom = 10.dp)
            .apply {
                background = GradientDrawable().apply {
                    setColor(bg)
                    cornerRadius = 22.dp.toFloat()
                }
            }
            .margin(top = 6.dp)
            .clickable(onClick)
    }
}
