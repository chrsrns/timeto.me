package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.ShortcutDb
import me.timeto.shared.vm.shortcuts.ShortcutsPickerVm
import kotlin.test.Test
import kotlin.test.assertEquals

class ShortcutsPickerVmTest {

    @Test
    fun initialSelection_allSelected() = runBlocking {
        initTestDb()
        val shortcutsDb = insertThree()
        val vm = ShortcutsPickerVm(shortcutsDb)
        assertEquals(setOf(0, 1, 2), vm.state.value.selectedIds)
        assertEquals(shortcutsDb, vm.getSelectedShortcutsDb())
    }

    @Test
    fun toggle_removesAndReadds() = runBlocking {
        initTestDb()
        val shortcutsDb = insertThree()
        val vm = ShortcutsPickerVm(shortcutsDb)

        vm.toggleShortcut(shortcutsDb[1])
        assertEquals(setOf(0, 2), vm.state.value.selectedIds)
        assertEquals(
            listOf(0, 2),
            vm.getSelectedShortcutsDb().map { it.id },
        )

        vm.toggleShortcut(shortcutsDb[1])
        assertEquals(setOf(0, 1, 2), vm.state.value.selectedIds)
    }

    @Test
    fun sorted_selectedFirst() = runBlocking {
        initTestDb()
        val shortcutsDb = insertThree()
        val vm = ShortcutsPickerVm(shortcutsDb)

        vm.toggleShortcut(shortcutsDb[0])
        vm.toggleShortcut(shortcutsDb[2])
        // Only id=1 selected -> it leads; unselected keep db order
        assertEquals(
            listOf(1, 0, 2),
            vm.state.value.shortcutsDbSorted.map { it.id },
        )
    }

    ///

    private suspend fun insertThree(): List<ShortcutDb> = listOf(
        ShortcutDb.insertWithValidation(name = "A", uri = "u1"),
        ShortcutDb.insertWithValidation(name = "B", uri = "u2"),
        ShortcutDb.insertWithValidation(name = "C", uri = "u3"),
    )
}
