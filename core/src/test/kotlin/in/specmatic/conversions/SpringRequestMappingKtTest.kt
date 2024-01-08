package `in`.specmatic.conversions

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpringRequestMappingKtTest {
    @Test
    fun `convert common brackets to braces when designating sigle path parameters in url`(): Unit {
        val path = "/(country:string)/state"
        assertEquals("/{country}/state", convertPathParameterStyle(path))
    }

    @Test
    fun `convert common brackets to braces when designating double path parameters in url`(): Unit {
        val path = "/(country:string)/state/(state:string)"
        assertEquals("/{country}/state/{state}", convertPathParameterStyle(path))
    }

    @Test
    fun `convert common brackets to braces when designating multiple path parameters in url`(): Unit {
        val path = "/(country:string)/state/(state:string)/(pincode:number)"
        assertEquals("/{country}/state/{state}/{pincode}", convertPathParameterStyle(path))
    }
}

