package me.timeto.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.ShortcutDb
import kotlin.test.Test
import kotlin.test.assertEquals

class ShortcutPerformerTest {

    @Test
    fun perform_emitsShortcutToFlow() = runBlocking {
        val shortcutDb = ShortcutDb(id = 1, name = "A", uri = "app://x")

        // SharedFlow has no replay: the collector must be subscribed
        // before perform() emits or the value is dropped.
        val received = async(Dispatchers.IO) { ShortcutPerformer.flow.first() }
        withTimeout(5_000) {
            while (ShortcutPerformer.flow.subscriptionCount.value == 0)
                delay(10)
        }

        shortcutDb.performUi()
        assertEquals(shortcutDb, withTimeout(5_000) { received.await() })
    }
}
