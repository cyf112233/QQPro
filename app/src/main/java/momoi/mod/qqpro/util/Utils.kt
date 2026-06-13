package momoi.mod.qqpro.util

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.tencent.mobileqq.widget.QQToast
import com.tencent.qphone.base.util.QLog
import com.tencent.mobileqq.utils.TimeFormatterUtils
import androidx.core.net.toUri
import momoi.mod.qqpro.safeCacheDir

object Utils {
    @SuppressLint("PrivateApi")
    val application = Class.forName("android.app.ActivityThread").getMethod("currentApplication")
        .invoke(null) as Application
    val isDebug =
        try {
            val info = application.applicationInfo
            (info.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }

    /**
     * Show QQ's native toast (QQToast) instead of Android's [Toast], whose layout
     * breaks under this watch ROM's ultra-large DPI.
     */
    fun toast(context: Context, text: CharSequence, longDuration: Boolean = false) {
        val duration = if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        QQToast.i(context, text, duration).l()
    }

    /** Copy [text] to the system clipboard and show a native QQ toast (no Android toast). */
    fun copyToClipboard(context: Context, text: CharSequence, toastText: CharSequence = "已复制") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("label", text))
        toast(context, toastText)
    }

    fun formatTime(timestamp: Long): CharSequence =
        TimeFormatterUtils.a(application, 3, timestamp, true, true)!!

    private var debugWatcher: Any? = null
    fun debugger(catch: Any?) {
        debugWatcher = catch
        Log.e("QQQQQQQQQQ", "debugger!")
    }

    private val debugLogFile by lazy {
        // externalCacheDir can be null on some ROMs (external storage unmounted); fall back
        // so the log still lands somewhere writable instead of a relative (unwritable) path.
        java.io.File(application.safeCacheDir, "qqpro_debug.log")
    }

    fun log(msg: String) {
        // Logging is enabled only in debug builds; release builds (android:debuggable=0) skip it.
        if (!isDebug) return
        Log.e("QQ Max", msg)
        // This watch ROM strips app android.util.Log; QLog reliably reaches logcat
        try {
            QLog.e("QQ Max", 1, msg)
        } catch (e: Throwable) {
        }
        // QLog output is gated by UIN_REPORTLOG_LEVEL and may be dropped, so also
        // persist to a file we can `adb pull` regardless of logcat gating.
        try {
            debugLogFile.appendText("${System.currentTimeMillis()} $msg\n")
        } catch (e: Throwable) {
        }
    }

    val heightPixels = Resources.getSystem().displayMetrics.heightPixels
    val isRoundScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Resources.getSystem().configuration.isScreenRound
    } else {
        isDebug
    }

    fun openUrl(url: String) {
        val normalized = if (url.contains("://")) url else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, normalized.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(application.packageManager) != null) {
            application.startActivity(intent)
        }
    }
}