package com.example.huzz.huzzai.domain

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

/**
 * Enforces energy-conservation constraints during emergency situations.
 * When battery is below 20% or Power Save mode is active, heavy computation is prevented.
 */
class HuzzAiBatteryPolicy(private val context: Context) {

    companion object {
        const val LOW_BATTERY_THRESHOLD_PERCENT = 20
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    fun isLowBatteryOrPowerSave(): Boolean {
        // Check power save mode
        if (powerManager?.isPowerSaveMode == true) {
            return true
        }

        // Check battery percentage
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        if (level >= 0 && scale > 0) {
            val batteryPct = (level * 100) / scale
            return batteryPct < LOW_BATTERY_THRESHOLD_PERCENT
        }

        return false
    }

    fun getBatteryPercentage(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 100
    }
}
