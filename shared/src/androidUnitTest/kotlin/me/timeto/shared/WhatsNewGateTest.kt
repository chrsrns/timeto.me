package me.timeto.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.vm.home.HomeVm
import me.timeto.shared.vm.whats_new.WhatsNewVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhatsNewGateTest {

    private suspend fun seedHome(): HomeVm {
        initTestDb()
        seedTaskFolders()
        insertActivitySq(id = 1, name = "Other", typeId = ActivityDb.Type.other.id)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1)
        refreshCache()
        return HomeVm()
    }

    private suspend fun awaitWhatsNewMessage(vm: HomeVm, expected: String?) {
        withTimeout(5_000) { vm.state.first { it.whatsNewMessage == expected } }
    }

    @Test
    fun whatsNewMessage_nullStored_shows() = runBlocking {
        val vm = seedHome()
        try {
            awaitWhatsNewMessage(vm, "What's New")
        } finally {
            vm.onDestroy()
        }
    }

    @Test
    fun whatsNewMessage_storedBehind_shows_storedCurrent_hides() = runBlocking {
        val firstDay = WhatsNewVm.historyItemsUi.first().unixDay

        initTestDb()
        seedTaskFolders()
        insertActivitySq(id = 1, name = "Other", typeId = ActivityDb.Type.other.id)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1)
        KvDb.KEY.WHATS_NEW_CHECK_UNIX_DAY.upsertInt(firstDay - 1)
        refreshCache()

        val vm = HomeVm()
        try {
            awaitWhatsNewMessage(vm, "What's New")

            // Marking the latest entry as seen suppresses the badge.
            KvDb.KEY.WHATS_NEW_CHECK_UNIX_DAY.upsertInt(firstDay)
            awaitWhatsNewMessage(vm, null)
        } finally {
            vm.onDestroy()
        }
    }

    @Test
    fun whatsNewVm_init_seedsCheckDay_suppressesBadge() = runBlocking {
        initTestDb()
        seedTaskFolders()
        insertActivitySq(id = 1, name = "Other", typeId = ActivityDb.Type.other.id)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1)
        refreshCache()

        val whatsNewVm = WhatsNewVm()
        try {
            withTimeout(5_000) {
                whatsNewVm.state.first() // VM constructed; init writes kv async
            }
            withTimeout(5_000) {
                while (KvDb.KEY.WHATS_NEW_CHECK_UNIX_DAY.selectOrNull() == null)
                    kotlinx.coroutines.delay(50)
            }
            assertEquals(
                WhatsNewVm.historyItemsUi.first().unixDay,
                KvDb.KEY.WHATS_NEW_CHECK_UNIX_DAY.selectOrNull()!!.value.toInt(),
            )
        } finally {
            whatsNewVm.onDestroy()
        }

        val homeVm = HomeVm()
        try {
            awaitWhatsNewMessage(homeVm, null)
        } finally {
            homeVm.onDestroy()
        }
    }
}
