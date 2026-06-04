package momoi.mod.qqpro.hook.style

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.tencent.qqnt.watch.selftab.ui.SelfFragment
import com.tencent.qqnt.watch.troop.ui.member.ui.GroupMemberFragment
import com.tencent.qqnt.watch.troop.ui.setting.TroopSettingFragment
import com.tencent.qqnt.watch.selftab.ui.edit.EditAvatarFragment
import com.tencent.qqnt.watch.selftab.ui.edit.SelfEditProfileFragment
import com.tencent.watch.aio_impl.ui.frames.SettingFrame
import com.tencent.watch.ime.input.ChooseInputFragment
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.asGroupOrNull
import momoi.mod.qqpro.findAll
import momoi.mod.qqpro.forEachAll
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.onChildAttached
import momoi.mod.qqpro.util.Utils

/** The single, unified gap used between every card across the app. */
const val CARD_MARGIN_DP = 2

/** Set an even [dp] margin on all four sides, if this view supports margins. */
fun View.uniformMargin(dp: Int) {
    (layoutParams as? ViewGroup.MarginLayoutParams)?.let {
        it.setMargins(dp.dp, dp.dp, dp.dp, dp.dp)
        requestLayout()
    }
}

/**
 * Card spacing that looks visually even everywhere. Adjacent cards' margins add up between
 * stacked cards (bottom + top = 2 margins) while a card's edge gap is only 1 margin, so the
 * horizontal margin is set to 2x the vertical one — making every visible gap == 2*CARD_MARGIN_DP.
 */
fun View.cardMargin() {
    val v = CARD_MARGIN_DP.dp
    val h = (2 * CARD_MARGIN_DP).dp
    (layoutParams as? ViewGroup.MarginLayoutParams)?.let {
        it.setMargins(h, v, h, v)
        it.marginStart = h
        it.marginEnd = h
        requestLayout()
    }
}

/**
 * Normalize every "card" in this view tree to CARD_MARGIN_DP. A card is any view that has a
 * background and sits as a direct child of a LinearLayout list (the shape shared by
 * setting_item / item_self_operation / item_setting_with_switch across the app). Chat message
 * lists are RecyclerView-based and are intentionally not touched here.
 */
fun ViewGroup.normalizeListCards() {
    forEachAll { view ->
        if (view.background != null &&
            view.parent is LinearLayout &&
            view.layoutParams is ViewGroup.MarginLayoutParams
        ) {
            view.cardMargin()
        }
    }
}

/**
 * The gender card and birthday card (inside a CustomInfoView) sit side-by-side with a
 * 4dp+4dp = 8dp horizontal gap, far larger than the vertical gap down to the next card.
 * Shrink each side to half of CARD_MARGIN_DP so the horizontal gap equals the vertical one.
 */
fun ViewGroup.fixGenderBirthdayGap() {
    val customInfo = findAll { it.javaClass.simpleName == "CustomInfoView" }?.asGroupOrNull() ?: return
    // The block has no background, so normalizeListCards skips it — give it the same card spacing
    // so its side inset (and the gap below it) match the cards.
    customInfo.cardMargin()
    val inner = customInfo.getChildAt(0)?.asGroupOrNull() ?: return
    if (inner.childCount < 2) return
    // Gap between the two side-by-side cards = marginEnd + marginStart = 2*CARD_MARGIN_DP,
    // equal to every other visible gap.
    (inner.getChildAt(0).layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = CARD_MARGIN_DP.dp
    (inner.getChildAt(1).layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart = CARD_MARGIN_DP.dp
}

/**
 * Chat settings panel (friend/group info: avatar + gender/birthday + 群成员/群聊设置/退出群 cards).
 * Unify the entry-card margins and the gender/birthday gap.
 */
@Mixin
class SettingFrameMargins : SettingFrame() {
    override fun Y(p0: LayoutInflater, p1: ViewGroup?, p2: Bundle?): View {
        val root = super.Y(p0, p1, p2)!!
        (root as? ViewGroup)?.let {
            it.normalizeListCards()
            it.fixGenderBirthdayGap()
        }
        Utils.log("CardMarginUnify: chat settings panel normalized")
        return root
    }
}

/** Self / profile page (自我页): same gender/birthday + operation-card unification. */
@Mixin
class SelfInfoMargins : SelfFragment() {
    override fun Y(p0: LayoutInflater, p1: ViewGroup?, p2: Bundle?): View {
        val root = super.Y(p0, p1, p2)!!
        (root as? ViewGroup)?.let {
            it.normalizeListCards()
            it.fixGenderBirthdayGap()
        }
        Utils.log("CardMarginUnify: self page normalized")
        return root
    }
}

/**
 * Two side-by-side cards/buttons get a doubled gap between them (each contributes a margin) vs a
 * single edge margin. Give the facing edges half the gap so the gap between them and the gap to
 * the outer edges both come out to 2*CARD_MARGIN_DP. [left] is the start view, [right] the end.
 */
fun fixSideBySide(left: View, right: View) {
    val edge = (2 * CARD_MARGIN_DP).dp
    val face = CARD_MARGIN_DP.dp
    (left.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
        leftMargin = edge; marginStart = edge; rightMargin = face; marginEnd = face
        topMargin = edge; bottomMargin = edge
    }
    (right.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
        leftMargin = face; marginStart = face; rightMargin = edge; marginEnd = edge
        topMargin = edge; bottomMargin = edge
    }
    left.requestLayout()
    right.requestLayout()
}

/** Edit profile / settings list (昵称 / 头像 / 性别 / 生日 cards). */
@Mixin
class SelfEditProfileMargins : SelfEditProfileFragment() {
    override fun Y(p0: LayoutInflater, p1: ViewGroup?, p2: Bundle?): View {
        val root = super.Y(p0, p1, p2)!!
        (root as? ViewGroup)?.normalizeListCards()
        Utils.log("CardMarginUnify: edit profile normalized")
        return root
    }
}

/** Change-avatar page (头像: avatar + 相册 / 拍照 buttons). */
@Mixin
class EditAvatarMargins : EditAvatarFragment() {
    override fun Y(p0: LayoutInflater, p1: ViewGroup?, p2: Bundle?): View {
        val root = super.Y(p0, p1, p2)!!
        (root as? ViewGroup)?.let { rootVg ->
            val buttons = ArrayList<View>()
            rootVg.forEachAll { if (it.javaClass.simpleName == "WatchButton") buttons.add(it) }
            if (buttons.size >= 2) fixSideBySide(buttons[0], buttons[1])
        }
        Utils.log("CardMarginUnify: edit avatar normalized")
        return root
    }
}

/** Input-method picker (打字 / 语音 cards). */
@Mixin
class ChooseInputMargins : ChooseInputFragment() {
    override fun Y(p0: LayoutInflater, p1: ViewGroup?, p2: Bundle?): View {
        val root = super.Y(p0, p1, p2)!!
        (root as? ViewGroup)?.normalizeListCards()
        Utils.log("CardMarginUnify: input picker normalized")
        return root
    }
}

/** Group settings (群聊设置): switch-option cards. */
@Mixin
class GroupSettingMargins : TroopSettingFragment() {
    override fun Y(p0: LayoutInflater, p1: ViewGroup?, p2: Bundle?): View {
        val root = super.Y(p0, p1, p2)!!
        (root as? ViewGroup)?.normalizeListCards()
        Utils.log("CardMarginUnify: group settings normalized")
        return root
    }
}

/** Group member list (查看群成员): rows are inflated lazily into a RecyclerView. */
@Mixin
class GroupMemberMargins : GroupMemberFragment() {
    override fun Y(p0: LayoutInflater, p1: ViewGroup?, p2: Bundle?): View {
        val root = super.Y(p0, p1, p2)!!
        val list = (root as? ViewGroup)?.findAll { it is RecyclerView } as? RecyclerView
        list?.onChildAttached { it.cardMargin() }
        Utils.log("CardMarginUnify: group member list hooked")
        return root
    }
}
