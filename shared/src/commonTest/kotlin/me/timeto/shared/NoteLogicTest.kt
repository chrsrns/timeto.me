package me.timeto.shared

import me.timeto.shared.db.NoteDb
import me.timeto.shared.db.NoteFolderDb
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteLogicTest {

    @Test
    fun noteFolderSymbolOrDefault_blankFallsBackToQuestion() {
        assertEquals(
            Symbol.Icon(Symbol.Icon.IconEnum.question),
            testNoteFolderDb(symbol_raw = "").symbolOrDefault(),
        )
    }

    @Test
    fun noteFolderSymbolOrDefault_validRaw_parses() {
        assertEquals(
            Symbol.Icon(Symbol.Icon.IconEnum.book),
            testNoteFolderDb(symbol_raw = "icon--book").symbolOrDefault(),
        )
    }
}

private fun testNoteFolderDb(
    id: Int = 1,
    time: Int = 0,
    sort: Int = 0,
    onHome: Boolean = false,
    symbol_raw: String = "",
    name: String = "folder",
): NoteFolderDb = NoteFolderDb(
    id = id, time = time, sort = sort, onHome = onHome,
    symbol_raw = symbol_raw, name = name,
)

private fun testNoteDb(
    id: Int = 1,
    time: Int = 0,
    sort: Int = 0,
    folderId: Int = 1,
    text: String,
): NoteDb = NoteDb(
    id = id, time = time, sort = sort, folderId = folderId, text = text,
)
