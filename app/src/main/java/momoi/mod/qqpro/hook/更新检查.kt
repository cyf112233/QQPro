package momoi.mod.qqpro.hook

import android.os.Bundle
import com.tencent.qqnt.watch.mainframe.MainActivity
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.ota.OTAManager2
import momoi.mod.qqpro.watchdog.Watchdog

/**
 * Update check on launch. Delegates to [OTAManager2], which queries the GitLab Releases API of
 * https://gitlab.com/ailife8881/qqmax, compares the latest release tag against this app's own
 * versionName, and (if newer) prompts to download+install the release APK in-app. Respects the
 * user's "不再提醒" choice (stored by OTAManager2 itself).
 */
@Mixin
class 更新检查 : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Watchdog.install(this)
        OTAManager2(this).checkUpdate(false)
    }
}
