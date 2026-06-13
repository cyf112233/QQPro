package momoi.mod.qqpro.hook.privacy.bugly

import android.content.Context
import com.tencent.bugly.proguard.aj
import com.tencent.bugly.proguard.au
import momoi.anno.mixin.StaticHook
import momoi.mod.qqpro.util.Utils

/**
 * Neutralizes Bugly's running-environment detection so it reports a clean device.
 *
 * Bugly (Tencent's crash/telemetry SDK embedded in QQ) probes for root and
 * emulator signals and uploads the verdict with crash/RMonitor reports:
 *
 *  - [aj.q] / [aj.r] : root indicators — non-empty result of a su-binary / root
 *                      probe list (`new i().a()` / `new k().a()`).
 *  - [aj.s]          : root — true if any path in the su list exists OR
 *                      `Build.TAGS` contains "test-keys".
 *  - [au.a]          : emulator scorer — scans device model ("mumu"), CPU abi
 *                      ("x86") and /system|/sys paths ("vbox","virtio") and adds
 *                      to a confidence score persisted to SP_EMULATOR_CONFIDENCE.
 *
 * We force the root probes to report "clean" and turn the emulator scorer into a
 * no-op so the confidence score is never incremented (stays 0). The beacon-side
 * `isRooted` value is handled separately in AntiDetectionBeacon.
 *
 * Kept in its own sub-package so the `au.a(Context)` hook does not collide at
 * Kotlin compile time with the `Virgo.a(Context)` hook in the turing package
 * (top-level functions with the same signature conflict within one package).
 */

@StaticHook(aj::class)
fun q(): Boolean = false

@StaticHook(aj::class)
fun r(): Boolean = false

@StaticHook(aj::class)
fun s(): Boolean = false

/**
 * Original [au.a] walks the model/cpu/filesystem feature lists and bumps the
 * emulator confidence score + reason string. No-op it: the score field keeps its
 * default of 0, so SP_EMULATOR_CONFIDENCE is reported as a non-emulator.
 */
@StaticHook(au::class)
fun a(context: Context) {
    Utils.log("AntiDetection: bugly emulator scorer (au.a) neutralized")
}
