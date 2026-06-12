package momoi.mod.qqpro.hook

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings as ASettings
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.util.Utils

/**
 * Plays the new-message alert (vibration + sound) for QQPro-posted notifications.
 *
 * QQPro's [NotificationReply] posts its own notification and bypasses QQ's native
 * `NotifyProcessor`, which is where the original app did its vibration + tone. So the alert has to
 * be re-driven here. We post the visual notification to a dedicated **silent** channel (no channel
 * sound, no channel vibration) so that everything is under our control — otherwise, on SDK > 28 the
 * native `CHANNEL_ID_SHOW_BADGE` channel vibrates on its own, which would fire even in 关闭 mode and
 * double up with our manual vibration.
 *
 * Sound and vibration are chosen independently via [Settings.notifySoundMode] and
 * [Settings.notifyVibrateMode] (each 0=关闭, 1=应用内, 2=系统):
 * - 应用内 → QQ's own message tone (`R.raw.office`) / the app's vibration pattern {100,200,200,100}.
 * - 系统 → the system default notification ringtone / a standard system-style vibration pattern.
 */
object NotificationAlert {
    const val CHANNEL_ID = "qqpro_message_alert"

    private const val MODE_OFF = 0
    private const val MODE_IN_APP = 1
    private const val MODE_SYSTEM = 2

    /** App message tone (in-app mode). Matches the native NotifyProcessor's R.raw.office. */
    private const val IN_APP_SOUND_RES = "office"
    private val IN_APP_VIBRATE = longArrayOf(100, 200, 200, 100)
    // A standard "double buzz" used for system mode (there is no public API to read the user's
    // configured notification vibration pattern, so we use a typical default-style one).
    private val SYSTEM_VIBRATE = longArrayOf(0, 250, 250, 250)

    /** Held so the previous tone can be released before a new one starts (avoids overlap/leak). */
    private var player: MediaPlayer? = null

    /**
     * Ensure the silent high-importance channel QQPro posts to exists. No-op below Oreo (channels
     * don't exist there; the notification itself carries no sound/vibration, so it's silent and we
     * still alert manually).
     */
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "消息提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            setShowBadge(true)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    /** Fire the configured alert once. Call after posting the notification (not on re-posts). */
    fun fire(ctx: Context) {
        val vibrateMode = Settings.notifyVibrateMode.value
        if (vibrateMode != MODE_OFF) vibrate(ctx, vibrateMode)
        val soundMode = Settings.notifySoundMode.value
        if (soundMode != MODE_OFF) playSound(ctx, soundMode)
    }

    private fun vibrate(ctx: Context, mode: Int) {
        val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val pattern = if (mode == MODE_SYSTEM) SYSTEM_VIBRATE else IN_APP_VIBRATE
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, -1),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build(),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Throwable) {
            Utils.log("NotificationAlert: vibrate failed: ${e.message}")
        }
    }

    private fun playSound(ctx: Context, mode: Int) {
        if (mode == MODE_SYSTEM) {
            playSystemNotification(ctx)
        } else {
            playInAppTone(ctx)
        }
    }

    /** Play the system default notification ringtone (system mode). */
    private fun playSystemNotification(ctx: Context) {
        try {
            val uri = ASettings.System.DEFAULT_NOTIFICATION_URI ?: return
            val ringtone = RingtoneManager.getRingtone(ctx, uri) ?: return
            ringtone.play()
        } catch (e: Throwable) {
            Utils.log("NotificationAlert: system ringtone failed: ${e.message}")
        }
    }

    /** Play QQ's own message tone (R.raw.office) for in-app mode. */
    @Synchronized
    private fun playInAppTone(ctx: Context) {
        try {
            val resId = ctx.resources.getIdentifier(IN_APP_SOUND_RES, "raw", ctx.packageName)
            if (resId == 0) {
                Utils.log("NotificationAlert: in-app tone resource not found")
                return
            }
            runCatching { player?.release() }
            player = null
            val mp = MediaPlayer.create(ctx, resId) ?: return
            mp.setOnCompletionListener { p ->
                player = null
                runCatching { p.release() }
            }
            mp.isLooping = false
            mp.start()
            player = mp
        } catch (e: Throwable) {
            Utils.log("NotificationAlert: in-app tone failed: ${e.message}")
        }
    }
}
