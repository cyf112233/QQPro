package momoi.mod.qqpro.hook

import android.view.View
import androidx.fragment.app.Fragment
import com.tencent.qqnt.watch.selftab.item.AboutItem
import com.tencent.qqnt.watch.ui.componet.tips.TipsUtils
import momoi.anno.mixin.Mixin

const val VERSION_CODE = 11
const val VERSION_NAME = "v1.5.1"

/**
 * Hooks the dedicated "关于" (About) entry on the self tab. Tapping it shows a Tips dialog;
 * we replace the original "NWearQQ系列…" text with the QQ Pro credits for all three layers.
 */
@Mixin
class AboutItemHook(fragment: Fragment) : AboutItem(fragment) {
    override fun onClick(v: View?) {
        val text = buildString {
            appendLine("QQ Max")
            appendLine(VERSION_NAME)
            appendLine()
            appendLine("NWear QQ · 爅峫")
            appendLine("QQ Pro · java30433")
            append("QQ Max · AILIFE")
        }
        // Mirror AboutItem.onClick's TipsUtils.h(...) call (icon = R.drawable.icon_update_avatar
        // 0x7e0805d4, trailing 1048552 is the Kotlin default-args mask).
        TipsUtils.h(
            TipsUtils.a, b, 0, text, 0, 0x7e0805d4,
            null, null, 0, null, 0, null, 0, null, 0,
            null, null, null, null, null, null, 1048552
        )
    }
}
