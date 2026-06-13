package momoi.mod.qqpro.hook

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.View
import com.tencent.watch.qzone_impl.frame.IAdapterHost
import com.tencent.watch.qzone_impl.frame.contentViewHolder.FeedCommentViewHolder
import momoi.mod.qqpro.util.Utils
import momoi.anno.mixin.Mixin

/**
 * QZone 动态(说说)点赞按钮：已赞(按下)状态太接近未赞状态。
 * n(context, resId) 负责把点赞图标 drawable 取出来设置到 tvLike。
 * 当 resId 是 icon_feed_like(已赞图标)时给它染成蓝色，让按下状态一目了然。
 */
@Mixin
class FeedLikeBlue(p0: View, p1: Int, p2: IAdapterHost) : FeedCommentViewHolder(p0, p1, p2) {

    override fun n(context: Context, resId: Int): Drawable? {
        val drawable = super.n(context, resId)
        if (drawable != null) {
            val likedId = context.resources.getIdentifier(
                "icon_feed_like", "drawable", context.packageName
            )
            if (resId == likedId) {
                Utils.log("FeedLikeBlue: tint liked icon blue")
                drawable.mutate().setColorFilter(LIKED_BLUE, PorterDuff.Mode.SRC_IN)
            }
        }
        return drawable
    }

    companion object {
        // 醒目的蓝色,区分已赞/未赞
        private const val LIKED_BLUE = 0xFF0A8DFF.toInt()
    }
}
