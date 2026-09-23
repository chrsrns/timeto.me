package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.KvDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Partial coverage: ping() performs real Ktor HTTP with no injection seam.
// Observable offline: no-throw contract + TOKEN_PASSWORD getsert, which runs
// inside the request URL builder before the network call. Success-path
// TOKEN/FEEDBACK_SUBJECT upserts and the non-"success" throw are not
// executable without an HTTP seam.
class PingTest {

    @Test
    fun ping_doesNotThrowAndPersistsPassword() = runBlocking {
        initTestDb()
        assertEquals(null, KvDb.KEY.TOKEN_PASSWORD.selectStringOrNull())

        ping(NotificationsPermission.notAsked) // must not throw outward

        val password = KvDb.KEY.TOKEN_PASSWORD.selectStringOrNull()
        assertNotNull(password)
        assertEquals(15, password.length)
        assertTrue(password.all { it in "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@#%^&*()_+" })

        // Second call reuses the stored password
        ping(NotificationsPermission.notAsked)
        assertEquals(password, KvDb.KEY.TOKEN_PASSWORD.selectStringOrNull())
    }
}
