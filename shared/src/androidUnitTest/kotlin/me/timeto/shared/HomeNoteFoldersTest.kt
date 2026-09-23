package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.NoteFolderDb
import me.timeto.shared.vm.home.HomeVm
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeNoteFoldersTest {

    private val symbol = Symbol.Icon.IconEnum.inbox.toIcon()

    @Test
    fun homeNoteFoldersUi_filtersOffHome() = runBlocking {
        initTestDb()
        seedTaskFolders()
        insertActivitySq(id = 1, name = "Other", typeId = me.timeto.shared.db.ActivityDb.Type.other.id)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1)
        NoteFolderDb.insertNoValidation(id = 1, sort = 0, onHome = true, symbol = symbol, name = "Shown")
        NoteFolderDb.insertNoValidation(id = 2, sort = 1, onHome = false, symbol = symbol, name = "Hidden")
        refreshCache()

        val vm = HomeVm()
        try {
            val names = vm.state.value.homeNoteFoldersUi.map { it.noteFolderDb.name }
            assertEquals(listOf("Shown"), names)
        } finally {
            vm.onDestroy()
        }
    }
}
