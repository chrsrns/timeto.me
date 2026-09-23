package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.vm.doc.DocVm
import me.timeto.shared.vm.home.HomeVm
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocVmTest {

    @Test
    fun onRead_setsDocForceReadTime_clearsForceOpenDoc(): Unit = runBlocking {
        initTestDb()
        seedTaskFolders()
        insertActivitySq(id = 1, name = "Other", typeId = ActivityDb.Type.other.id)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1)
        refreshCache()
        assertNull(KvDb.KEY.DOC_FORCE_READ_TIME.selectOrNull())

        // HomeVm forces the doc screen while the guide is unread.
        val homeVm = HomeVm()
        try {
            withTimeout(5_000) { homeVm.state.first { it.forceOpenDoc } }
        } finally {
            homeVm.onDestroy()
        }

        val docVm = DocVm()
        try {
            docVm.onRead()
            withTimeout(5_000) {
                while (KvDb.KEY.DOC_FORCE_READ_TIME.selectOrNull() == null)
                    delay(50)
            }
            assertTrue(KvDb.KEY.DOC_FORCE_READ_TIME.selectOrNull()!!.value.toInt() > 0)
        } finally {
            docVm.onDestroy()
        }

        val homeVm2 = HomeVm()
        try {
            withTimeout(5_000) { homeVm2.state.first { !it.forceOpenDoc } }
        } finally {
            homeVm2.onDestroy()
        }
    }
}
