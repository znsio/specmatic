package `in`.specmatic.conversions

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpringRequestMappingKtTest {
    @Test
    fun `convert common brackets to braces when designating path parameters in url`(): Unit {
        val path = "/(country:string)/state/(state:number)";
        assertEquals("/{country}/state/{state}", convertPathParameterStyle(path))
    }
}

