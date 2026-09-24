package me.timeto.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.NoteFolderDb
import me.timeto.shared.db.ShortcutDb
import me.timeto.shared.db.TaskFolderDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CacheAccessorTest {

    private fun activity(
        id: Int,
        type_id: Int = ActivityDb.Type.general.id,
        parent_id: Int? = null,
    ) = ActivityDb(
        id = id, parent_id = parent_id, type_id = type_id, name = "a$id",
        goal_json = null, timer = 0, period_json = """{"type":2}""",
        symbol_raw = "", home_button_sort = "", color_rgba = "52,199,89,255",
        keep_screen_on = 0, pomodoro_timer = 0, checklist_hint = 0, timer_hints = "",
    )

    private fun taskFolder(id: Int) = TaskFolderDb(
        id = id, sort = 0, activity_id = null, name = "f$id", symbol_raw = "icon--inbox",
    )

    private fun noteFolder(id: Int) = NoteFolderDb(
        id = id, time = 1, sort = 0, onHome = true, symbol_raw = "icon--inbox", name = "n$id",
    )

    private fun checklist(id: Int) = ChecklistDb(id = id, name = "c$id", reset_day = 0)

    private fun shortcut(id: Int) = ShortcutDb(id = id, name = "s$id", uri = "https://example.com")

    @Test
    fun activityOrNull_hitAndMiss() {
        Cache.overrideListsForTesting(activitiesDb = listOf(activity(1)))
        assertEquals(1, Cache.activityOrNull(1)?.id)
        assertNull(Cache.activityOrNull(999))
    }

    @Test
    fun requireActivity_hitAndMiss() {
        Cache.overrideListsForTesting(activitiesDb = listOf(activity(1)))
        assertEquals(1, Cache.requireActivity(1).id)
        assertFailsWith<NoSuchElementException> { Cache.requireActivity(999) }
    }

    @Test
    fun indexRefreshesWhenListReplaced() {
        Cache.overrideListsForTesting(activitiesDb = listOf(activity(1)))
        assertEquals(1, Cache.requireActivity(1).id)

        Cache.overrideListsForTesting(activitiesDb = listOf(activity(2)))
        assertFailsWith<NoSuchElementException> { Cache.requireActivity(1) }
        assertEquals(2, Cache.requireActivity(2).id)

        Cache.overrideListsForTesting(checklistsDb = listOf(checklist(1)))
        assertEquals(1, Cache.checklistOrNull(1)?.id)
        Cache.overrideListsForTesting(checklistsDb = listOf(checklist(2)))
        assertNull(Cache.checklistOrNull(1))
        assertEquals(2, Cache.checklistOrNull(2)?.id)

        Cache.overrideListsForTesting(shortcutsDb = listOf(shortcut(1)))
        assertEquals(1, Cache.shortcutOrNull(1)?.id)
        Cache.overrideListsForTesting(shortcutsDb = listOf(shortcut(2)))
        assertNull(Cache.shortcutOrNull(1))
        assertEquals(2, Cache.shortcutOrNull(2)?.id)
    }

    @Test
    fun checklistOrNull_hitAndMiss() {
        Cache.overrideListsForTesting(checklistsDb = listOf(checklist(1)))
        assertEquals(1, Cache.checklistOrNull(1)?.id)
        assertNull(Cache.checklistOrNull(999))
    }

    @Test
    fun shortcutOrNull_hitAndMiss() {
        Cache.overrideListsForTesting(shortcutsDb = listOf(shortcut(1)))
        assertEquals(1, Cache.shortcutOrNull(1)?.id)
        assertNull(Cache.shortcutOrNull(999))
    }

    @Test
    fun overrideListsForTesting_setsOnlyPassedLists() {
        Cache.overrideListsForTesting(activitiesDb = listOf(activity(1)))
        Cache.overrideListsForTesting(kvDb = listOf(KvDb(KvDb.KEY.ZEN_MODE_ENABLED.name, "0")))

        // The second call must not have cleared the first list.
        assertEquals(1, Cache.activityOrNull(1)?.id)
        assertEquals("0", Cache.kvStringOrNull(KvDb.KEY.ZEN_MODE_ENABLED))

        Cache.overrideListsForTesting(activitiesDb = emptyList(), kvDb = emptyList())
        assertNull(Cache.activityOrNull(1))
        assertNull(Cache.kvOrNull(KvDb.KEY.ZEN_MODE_ENABLED))
    }

    @Test
    fun requireActivityByType_matchesType() {
        Cache.overrideListsForTesting(
            activitiesDb = listOf(activity(1), activity(2, type_id = ActivityDb.Type.other.id)),
        )
        assertEquals(2, Cache.requireActivityByType(ActivityDb.Type.other).id)
    }

    @Test
    fun activityDescendantsMap_includesDescendants() {
        Cache.overrideListsForTesting(
            activitiesDb = listOf(activity(1), activity(2, parent_id = 1), activity(3, parent_id = 2)),
        )
        val map = Cache.activityDescendantsMap()
        assertEquals(listOf(2, 3), map[1]?.map { it.id }?.sorted())
        assertEquals(listOf(3), map[2]?.map { it.id })
    }

    @Test
    fun taskFolderAccessors() {
        Cache.overrideListsForTesting(taskFoldersDbSorted = listOf(taskFolder(TaskFolderDb.ID_TODAY)))
        assertEquals(TaskFolderDb.ID_TODAY, Cache.taskFolderOrNull(TaskFolderDb.ID_TODAY)?.id)
        assertNull(Cache.taskFolderOrNull(999))
        assertFailsWith<NoSuchElementException> { Cache.requireTaskFolder(999) }
    }

    @Test
    fun noteFolderAccessors() {
        Cache.overrideListsForTesting(noteFoldersDb = listOf(noteFolder(5)))
        assertEquals(5, Cache.noteFolderOrNull(5)?.id)
        assertNull(Cache.noteFolderOrNull(999))
        assertFailsWith<NoSuchElementException> { Cache.requireNoteFolder(999) }
    }

    @Test
    fun checklistItems_filtersByListId() {
        Cache.overrideListsForTesting(
            checklistItemsDb = listOf(
                ChecklistItemDb(id = 1, text = "a", list_id = 7, check_time = 0, sort = 0),
                ChecklistItemDb(id = 2, text = "b", list_id = 8, check_time = 0, sort = 1),
                ChecklistItemDb(id = 3, text = "c", list_id = 7, check_time = 0, sort = 2),
            ),
        )
        assertEquals(listOf(1, 3), Cache.checklistItems(7).map { it.id })
        assertTrue(Cache.checklistItems(999).isEmpty())
    }

    @Test
    fun kvAccessors() {
        Cache.overrideListsForTesting(kvDb = listOf(KvDb(key = KvDb.KEY.ZEN_MODE_ENABLED.name, value = "0")))
        assertEquals("0", Cache.kvOrNull(KvDb.KEY.ZEN_MODE_ENABLED)?.value)
        assertEquals("0", Cache.kvStringOrNull(KvDb.KEY.ZEN_MODE_ENABLED))
        assertNull(Cache.kvOrNull(KvDb.KEY.IS_SENDING_REPORTS))
        assertNull(Cache.kvStringOrNull(KvDb.KEY.IS_SENDING_REPORTS))
    }

    @Test
    fun version_bumpsOnFillLateInit() = runBlocking {
        val before = Cache.version.first()
        val interval = IntervalDb(id = 1, time = 1, activityId = 1, note = null)
        Cache.fillLateInit(interval, interval)
        assertTrue(Cache.version.first() > before)
    }
}
