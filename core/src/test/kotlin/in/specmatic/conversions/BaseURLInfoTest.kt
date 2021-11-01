package `in`.specmatic.conversions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class BaseURLInfoTest {
    @Test
    fun `should convert base url info with no port into a url fragment`() {
        val info = `in`.specmatic.conversions.BaseURLInfo("localhost", -1, "http", "http://localhost")
        assertThat(`in`.specmatic.conversions.toFragment(info)).isEqualTo("localhost")
    }

    @Test
    fun `should convert base url info with a port into a url fragment`() {
        val info = `in`.specmatic.conversions.BaseURLInfo("localhost", 9000, "http", "http://localhost")
        assertThat(`in`.specmatic.conversions.toFragment(info)).isEqualTo("localhost:9000")
    }
}