package me.timeto.app

import android.content.Intent
import me.timeto.app.TimerNotificationReceiver.Companion.liveDataDecodeOrNull
import me.timeto.app.TimerNotificationReceiver.Companion.liveDataEncode
import me.timeto.shared.LiveActivity
import me.timeto.shared.db.IntervalDb
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

// V247: live-data roundtrip preserves Timer vs Stopwatch polarity.
// Extra key literals are pinned to the wire contract, not the private consts.
@RunWith(RobolectricTestRunner::class)
class LiveDataEncodeTest {

    private fun interval(note: String?, time: Int = 1_000) =
        IntervalDb(id = 1, time = time, activityId = 1, note = note)

    @Test
    fun timer_roundTrip() {
        val intent = Intent()
        liveDataEncode(intent, LiveActivity(interval("run #t600")))
        val liveData = liveDataDecodeOrNull(intent)
        assertIs<LiveUpdatesUtils.LiveData.Timer>(liveData)
        assertEquals("run", liveData.title)
        assertEquals(1_600, liveData.finishTime)
        assertEquals("10 minutes have expired", liveData.expiredString)
    }

    @Test
    fun stopwatch_roundTrip() {
        val intent = Intent()
        liveDataEncode(intent, LiveActivity(interval("sw", time = 777)))
        val liveData = liveDataDecodeOrNull(intent)
        assertIs<LiveUpdatesUtils.LiveData.Stopwatch>(liveData)
        assertEquals("sw", liveData.title)
        assertEquals(777, liveData.startTime)
        assertNull(intent.getStringExtra("live_expired_string"))
    }

    @Test
    fun decode_missingTitle_null() {
        val intent = Intent().putExtra("live_time", 42)
        assertNull(liveDataDecodeOrNull(intent))
    }

    @Test
    fun decode_zeroTime_null() {
        val intent = Intent()
            .putExtra("live_title", "t")
            .putExtra("live_time", 0)
        assertNull(liveDataDecodeOrNull(intent))
    }

    @Test
    fun decode_polarityFlag() {
        val timerIntent = Intent()
            .putExtra("live_title", "t")
            .putExtra("live_time", 42)
            .putExtra("live_is_timer_or_stopwatch", true)
        assertIs<LiveUpdatesUtils.LiveData.Timer>(liveDataDecodeOrNull(timerIntent))

        val stopwatchIntent = Intent()
            .putExtra("live_title", "t")
            .putExtra("live_time", 42)
            .putExtra("live_is_timer_or_stopwatch", false)
        assertIs<LiveUpdatesUtils.LiveData.Stopwatch>(liveDataDecodeOrNull(stopwatchIntent))
    }
}
