package me.timeto.shared

import me.timeto.shared.db.KvDb
import me.timeto.shared.db.db
import kotlin.test.Test
import kotlin.test.assertEquals

class DbSmokeTest {

    @Test
    fun kvUpsert_selectRoundtrip() {
        initTestDb()
        db.kVQueries.upsert(key = "SMOKE_TEST", value_ = "42")
        val rows = db.kVQueries.selectAll().executeAsList()
        assertEquals("42", rows.first { it.key == "SMOKE_TEST" }.value_)
    }

    @Test
    fun reinit_givesFreshDatabase() {
        initTestDb()
        db.kVQueries.upsert(key = "SMOKE_TEST", value_ = "1")
        initTestDb()
        val rows = db.kVQueries.selectAll().executeAsList()
        assertEquals(0, rows.count { it.key == "SMOKE_TEST" })
    }

    @Test
    fun cacheInit_runs() {
        initTestDb()
        // Cache.init populates list caches from the empty db
        assertEquals(emptyList(), Cache.kvDb)
    }
}
