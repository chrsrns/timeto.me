package me.timeto.app

import android.content.Intent
import android.os.BatteryManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.BatteryInfo
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

// I194: level * 100 / scale, charging when plugged != 0
@RunWith(RobolectricTestRunner::class)
class BatteryReceiverTest {

    private fun send(level: Int, scale: Int, plugged: Int) {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, level)
            .putExtra(BatteryManager.EXTRA_SCALE, scale)
            .putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
        BatteryReceiver().onReceive(null, intent)
    }

    @Test
    fun emitsPercentAndCharging(): Unit = runBlocking {
        send(level = 42, scale = 84, plugged = 1)
        assertEquals(50, withTimeout(5_000) { BatteryInfo.levelFlow.first { it == 50 } })
        withTimeout(5_000) { BatteryInfo.isChargingFlow.first { it } }
    }

    @Test
    fun unplugged_notCharging(): Unit = runBlocking {
        send(level = 7, scale = 10, plugged = 0)
        assertEquals(70, withTimeout(5_000) { BatteryInfo.levelFlow.first { it == 70 } })
        withTimeout(5_000) { BatteryInfo.isChargingFlow.first { !it } }
    }
}
