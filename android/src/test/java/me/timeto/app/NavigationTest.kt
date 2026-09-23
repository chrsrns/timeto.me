package me.timeto.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.app.ui.navigation.Navigation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Layer content lambdas are @Composable and never invoked here; the
// alert/confirmation button shapes ("Ok", red action) stay source-verified.
class NavigationTest {

    @Test
    fun push_appendsLayers_lifo() = runBlocking {
        val nav = Navigation()
        nav.push {}
        nav.push {}
        nav.push {}
        assertEquals(3, nav.layers.size)
        assertTrue(nav.layers.all { !it.isPresented.value })
    }

    @Test
    fun close_unpresentsThenRemovesAfterDelay(): Unit = runBlocking {
        val nav = Navigation()
        nav.push {}
        val layer = nav.layers.single()
        // NavigationView sets isPresented=true once composed; simulate that.
        layer.isPresented.value = true

        layer.close()
        assertFalse(layer.isPresented.value)
        assertEquals(1, nav.layers.size) // still present during exit window

        withTimeout(5_000) {
            while (nav.layers.isNotEmpty())
                delay(50)
        }
        assertEquals(0, nav.layers.size)
    }
}
// alert()/confirmation() are not testable on the JVM: dialog() defaults to
// PaddingValues(H_PADDING), and H_PADDING resolves Resources.getSystem().
// Their layer-push behavior is the same code path as push(); button shapes
// ("Ok" single button, red confirmation) are source-verified.
