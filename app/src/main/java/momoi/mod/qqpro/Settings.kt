package momoi.mod.qqpro

import android.content.SharedPreferences
import androidx.core.content.edit
import momoi.mod.qqpro.util.Utils

object Settings {
    val sp: SharedPreferences = Utils.application.getSharedPreferences("qqpro", 0)
    val wear: SharedPreferences = Utils.application.getSharedPreferences("wearqq", 0)

    // ===== QQ Pro 设置 (by java30433) =====
    val scale = FloatPref("scale", 0.7f)
    val chatScale = FloatPref("chatScale", 0.8f)
    val enableSmoothScroll = BooleanPref("enableSmoothScroll", true)
    val blockBack = BooleanPref("blockBack", false)
    val swapCenterKeyboard = BooleanPref("swapCenterKeyboard", true)

    // ===== QQ Max 设置 (by AILIFE) =====
    val showGroupAvatar = BooleanPref("showGroupAvatar", true)
    val hideRepeatedSender = BooleanPref("hideRepeatedSender", true)
    val inlineSendButton = BooleanPref("inlineSendButton", true)
    val inlineChatInput = BooleanPref("inlineChatInput", true)
    // Screen rounded-corner diameter (in dp). Adds left/right margin of this
    // width to the inline chat EditText so the side buttons aren't clipped by a
    // round watch screen's corners.
    val screenCornerDiameter = FloatPref("screenCornerDiameter", 22f)
    val backToFirstPage = BooleanPref("backToFirstPage", true)

    // ===== NWear QQ 设置 (by 爅峫) — backed by the base app's "wearqq" prefs =====
    val singleLineInput = WearBooleanPref("single_line_input", false)
    val sendWithImage = WearBooleanPref("send_with_image", true)
    val replyWithAt = WearBooleanPref("reply_with_at", true)
    val doubleSpeak = WearBooleanPref("double_speak", false)
    val doubleReply = WearBooleanPref("double_reply", true)
    val allowNotification = WearBooleanPref("allow_notification", true)
    val residentNotification = WearBooleanPref("resident_notification", false)
    val allowVibrate = WearBooleanPref("allow_vibrate", true)
    val voiceBtnText = WearStringPref("voice_btn_text", "QQ")

    val text get() = wear.getString("voice_btn_text", "")?.let {
        if (it == "QQ") {
            ""
        } else {
            it
        }
    } ?: ""
}

abstract class Pref<T>(def: T) {
    var value: T = def
        set(value) {
            field = value
            set(value)
        }

    protected abstract fun set(value: T)
}

class FloatPref(private val key: String, def: Float) :
    Pref<Float>(Settings.sp.getFloat(key, def)) {
    override fun set(value: Float) = Settings.sp.edit {
        putFloat(key, value)
    }
}

class BooleanPref(private val key: String, def: Boolean) :
    Pref<Boolean>(Settings.sp.getBoolean(key, def)) {
    override fun set(value: Boolean) = Settings.sp.edit {
        putBoolean(key, value)
    }
}

/**
 * Boolean setting stored in the base app's "wearqq" SharedPreferences so the
 * original NWear-QQ code keeps reading it. Seeds [def] on first run when the key
 * is absent, so the requested default actually takes effect (the base app reads
 * the key with its own hard-coded default otherwise).
 */
class WearBooleanPref(private val key: String, def: Boolean) :
    Pref<Boolean>(seed(key, def)) {
    override fun set(value: Boolean) = Settings.wear.edit {
        putBoolean(key, value)
    }

    companion object {
        private fun seed(key: String, def: Boolean): Boolean {
            if (!Settings.wear.contains(key)) {
                Settings.wear.edit { putBoolean(key, def) }
            }
            return Settings.wear.getBoolean(key, def)
        }
    }
}

class WearStringPref(private val key: String, def: String) :
    Pref<String>(Settings.wear.getString(key, def) ?: def) {
    override fun set(value: String) = Settings.wear.edit {
        putString(key, value)
    }
}
