package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.KvDb
import kotlin.test.Test
import kotlin.test.assertEquals

class DayStartOffsetFlowTest {

    @Test
    fun buildTodayFlow_recomputesOnKvChange() = runBlocking {
        initTestDb()

        val emitted = mutableListOf<Int>()
        val job = launch {
            DayStartOffsetUtils.buildTodayFlow().collect { emitted.add(it) }
        }
        try {
            withTimeout(5_000) { while (emitted.isEmpty()) delay(10) }
            val first = emitted.first()
            assertEquals(
                DayStartOffsetUtils.calcDay(time = time(), dayStartOffsetSeconds = 0),
                first,
            )

            // +86400s offset shifts localDay back exactly one day,
            // so the recompute always survives distinctUntilChanged.
            KvDb.KEY.DAY_START_OFFSET_SECONDS.upsertInt(86_400)
            withTimeout(10_000) { while (emitted.size < 2) delay(10) }
            assertEquals(first - 1, emitted.last())
        } finally {
            job.cancel()
        }
    }
}
