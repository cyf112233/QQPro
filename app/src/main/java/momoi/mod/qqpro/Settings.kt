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
    // Group chat avatar size, as a multiple of the nickname text size. Default 3x.
    val avatarSizeScale = FloatPref("avatarSizeScale", 3f)
    val hideRepeatedSender = BooleanPref("hideRepeatedSender", true)
    val inlineSendButton = BooleanPref("inlineSendButton", true)
    val inlineChatInput = BooleanPref("inlineChatInput", true)
    // Screen rounded-corner diameter (in dp). Adds left/right margin of this
    // width to the inline chat EditText so the side buttons aren't clipped by a
    // round watch screen's corners.
    val screenCornerDiameter = FloatPref("screenCornerDiameter", 22f)
    val backToFirstPage = BooleanPref("backToFirstPage", true)
    // Replace the input bar's emoji button with a "+" button that opens the attachment
    // list as an overlay over the chat (like the long-press menu). Removes the attachment
    // ViewPager page (友: 聊天+设置 两页; 非好友: 仅聊天), and moves 表情 into the overlay list.
    val attachmentOverlay = BooleanPref("attachmentOverlay", false)
    // Rich chat titlebar: replaces the top page-indicator strip with a bar holding a back
    // button, the indicator dots, other-chats unread count, group member count and the
    // group/contact name. titlebarHeight (dp) defaults to the current strip height (16).
    val enableTitlebar = BooleanPref("enableTitlebar", false)
    // Show the other-chats unread count badge in the chat titlebar. When off, the
    // titlebar shows only the name + member count (no red badge).
    val titlebarShowUnread = BooleanPref("titlebarShowUnread", true)
    val titlebarHeight = FloatPref("titlebarHeight", 16f)
    // Move the home/main page's page-indicator dots to the bottom and scale their size by
    // titlebarHeight (relative to the default 16dp).
    val bottomMainNav = BooleanPref("bottomMainNav", false)
    // Use the in-app camera for 拍照. When off, launch the system camera app (third-party)
    // via an intent for photos. Video recording always uses the system app (the in-app
    // camera can't record video).
    val useInAppCamera = BooleanPref("useInAppCamera", true)
    // Sort the image/gallery picker by date taken (EXIF capture time) instead of
    // the default date_modified. Falls back to date_modified when a file has no
    // capture time recorded.
    val gallerySortByDateTaken = BooleanPref("gallerySortByDateTaken", false)
    // Use the system image picker (Android photo picker if available, otherwise the
    // SAF document picker) for 相册 instead of QQ's in-app gallery. Avoids needing
    // storage permission and works around in-app picker problems on some devices.
    // Supports selecting multiple images at once.
    val useSystemImagePicker = BooleanPref("useSystemImagePicker", false)
    // Ask before opening a tapped link in the browser.
    val confirmOpenLink = BooleanPref("confirmOpenLink", true)
    // Also detect links without an http(s):// prefix (e.g. "example.com/x").
    val wideUrlMatch = BooleanPref("wideUrlMatch", true)
    // Try to resolve a client-side preview (icon/title/description) for links in
    // messages and show it below the text. Makes a network request per unique link.
    val enableLinkPreview = BooleanPref("enableLinkPreview", true)
    // Rounded-corner radius (in dp) for chat bubbles, the merged-forward/chat-history
    // blocks and the reply block. 0 = square.
    val bubbleCornerRadius = FloatPref("bubbleCornerRadius", 10f)
    // Override chat-bubble fill color, as a hex string (#RRGGBB or #AARRGGBB / with or
    // without the leading #). Blank keeps the original bubble color (sampled per side).
    val bubbleColorSelf = StringPref("bubbleColorSelf", "")
    val bubbleColorOther = StringPref("bubbleColorOther", "")
    // Contacts page (2nd main page): show "好友"/"群聊" section headers, split the single
    // "我的通知" entry into separate friend/group notification entries (each with its own
    // count and direct navigation), and drop the trailing group icon on every group row.
    val contactSections = BooleanPref("contactSections", true)
    // How much to darken the chat background image for readability.
    // 0 = original image, 0.9 = almost black. Applied as a black overlay on top
    // of the picked image. Takes effect the next time a chat is opened.
    val chatBgDarken = FloatPref("chatBgDarken", 0.35f)

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

class StringPref(private val key: String, def: String) :
    Pref<String>(Settings.sp.getString(key, def) ?: def) {
    override fun set(value: String) = Settings.sp.edit {
        putString(key, value)
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
