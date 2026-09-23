package me.timeto.shared

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import me.timeto.appdbsq.TimetomeDB

/**
 * Initializes a fresh in-memory database for unit tests.
 * Safe to call per test: `db` is reassigned and `Cache.init`
 * cancels the previous collector scope.
 */
fun initTestDb(): SqlDriver {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    TimetomeDB.Schema.create(driver)
    initKmp(
        sqlDriver = driver,
        systemInfo = SystemInfo(
            build = 0,
            version = "test",
            os = SystemInfo.Os.Android("test"),
            device = "test",
            flavor = null,
        ),
    )
    runBlocking { initKmpDeferred.await() }
    return driver
}
