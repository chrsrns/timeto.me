package me.timeto.shared

import io.ktor.client.request.HttpRequestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class UrlAppendSystemInfoTest {

    @Test
    fun urlAppendSystemInfo_appendsAllParams() {
        SystemInfo.instance = SystemInfo(
            build = 123,
            version = "1.0",
            os = SystemInfo.Os.Android("36"),
            device = "pixel-test",
            flavor = "fdroid",
        )
        val builder = HttpRequestBuilder()
        builder.urlAppendSystemInfo(token = "tok123")
        val params = builder.url.parameters
        assertEquals("tok123", params["__token"])
        assertEquals("123", params["__build"])
        assertEquals("android-36", params["__os"])
        assertEquals("pixel-test", params["__device"])
        assertEquals("fdroid", params["__flavor"])
    }

    @Test
    fun urlAppendSystemInfo_nullTokenAndFlavor_emptyStrings() {
        SystemInfo.instance = SystemInfo(
            build = 1,
            version = "1.0",
            os = SystemInfo.Os.Ios("17.0"),
            device = "iphone",
            flavor = null,
        )
        val builder = HttpRequestBuilder()
        builder.urlAppendSystemInfo(token = null)
        val params = builder.url.parameters
        assertEquals("", params["__token"])
        assertEquals("", params["__flavor"])
        assertEquals("ios-17.0", params["__os"])
    }
}
