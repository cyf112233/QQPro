package momoi.mod.qqpro.hook

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.tencent.qqnt.watch.mainframe.MainFragment
import com.tencent.qqnt.watch.ui.componet.tablayout.CircleIndicator
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.findAll
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils

/**
 * Home/main page (conversation list). When [Settings.bottomMainNav] is on, move the page-indicator
 * dots to the bottom and scale the whole indicator (icons + dots) by the titlebar height relative
 * to the default 16dp — so raising 标题栏高度 also enlarges the main-page navigation.
 *
 * Position is set via translationY (post-layout) rather than ConstraintLayout constraints: the
 * app's bundled ConstraintLayout.LayoutParams is R8-minified, so its constraint fields (topToTop…)
 * don't exist at runtime.
 */
@Mixin
class MainPageNav : MainFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!Settings.bottomMainNav.value) return
        runCatching {
            val indicator = (view as? ViewGroup)?.findAll { it is CircleIndicator } as? CircleIndicator
                ?: run { Utils.log("MainPageNav: indicator not found"); return }

            // Enlarge dots/icons by scaling the whole indicator view (avoids the obfuscated Config).
            val scale = (Settings.titlebarHeight.value / 16f).coerceIn(1f, 3f)
            indicator.scaleX = scale
            indicator.scaleY = scale
            // Don't clip the scaled/moved indicator.
            (indicator.parent as? ViewGroup)?.clipChildren = false
            (indicator.parent as? ViewGroup)?.clipToPadding = false

            // Move to the bottom once laid out (translationY, no constraint fields needed).
            indicator.post {
                val parent = indicator.parent as? View ?: return@post
                // Pull the page content up into the now-empty top band so the indicator's old space
                // isn't left blank — the freed band moves to the bottom where the indicator now sits.
                // The home page uses androidx.viewpager.widget.ViewPager (v1) — match either.
                val vp = (view as? ViewGroup)?.findAll { it.javaClass.name.contains("ViewPager") }
                // Reserve a bottom band the size of the scaled indicator; shrink the ViewPager to
                // end where the band begins. Setting a fixed height re-lays-out (and may re-center)
                // the pager, so read its settled top in a nested post and pull it flush to the top.
                val band = (indicator.height * scale).toInt()
                vp?.let {
                    it.layoutParams = it.layoutParams.apply { height = parent.height - band }
                    it.requestLayout()
                    it.post { it.translationY = -it.top.toFloat() }
                }

                // Center the indicator within the bottom band [height-band, height].
                val bandCenter = parent.height - band / 2f
                val ty = bandCenter - (indicator.top + indicator.height / 2f)
                indicator.translationY = ty
                Utils.log("MainPageNav: ty=$ty parentH=${parent.height} vpTop=${vp?.top} band=$band indH=${indicator.height} scale=$scale")
            }
        }.onFailure { Utils.log("MainPageNav failed: $it") }
    }
}
