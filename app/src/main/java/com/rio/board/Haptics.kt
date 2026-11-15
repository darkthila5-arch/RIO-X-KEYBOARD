package com.rio.board

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/** Key vibration with a strength: 0 off, 1 light, 2 medium, 3 strong. */
object Haptics {
    val names = listOf("Off", "Light", "Medium", "Strong")

    private val ms = longArrayOf(0, 8, 14, 26)          // how long
    private val amp = intArrayOf(0, 40, 110, 230)        // how hard (1..255), phones without amplitude control use duration only
    private var vib: Vibrator? = null

    private fun vibrator(ctx: Context): Vibrator? {
        vib?.let { return it }
        val v: Vibrator? = try {
            if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            null
        }
        vib = v
        return v
    }

    @Suppress("DEPRECATION")
    fun buzz(ctx: Context, level: Int, v: View?) {
        if (level <= 0) return
        val l = level.coerceIn(1, 3)
        try {
            val vb = vibrator(ctx)
            if (vb != null && vb.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    val a = if (vb.hasAmplitudeControl()) amp[l] else VibrationEffect.DEFAULT_AMPLITUDE
                    vb.vibrate(VibrationEffect.createOneShot(ms[l], a))
                } else {
                    vb.vibrate(ms[l])
                }
                return
            }
        } catch (e: Exception) {
            // no vibrator access: use the system tap below
        }
        v?.performHapticFeedback(
            when (l) {
                1 -> HapticFeedbackConstants.CLOCK_TICK
                2 -> HapticFeedbackConstants.KEYBOARD_TAP
                else -> HapticFeedbackConstants.LONG_PRESS
            }
        )
    }
}
