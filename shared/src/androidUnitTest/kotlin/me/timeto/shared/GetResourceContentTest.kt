package me.timeto.shared

import kotlin.test.Test
import kotlin.test.assertFailsWith

// Partial coverage: the androidMain actual reads a raw resource via
// `androidApplication`, which is uninitialized in unit tests — so the only
// executable assertion is that the actual exists and delegates to the app
// resources. The iosMain bundled lookup and watchosMain TODO() cannot be
// compiled or run on Linux.
class GetResourceContentTest {

    @Test
    fun androidActual_requiresInitializedApplication() {
        assertFailsWith<UninitializedPropertyAccessException> {
            getResourceContent("emojis", "json")
        }
    }
}
