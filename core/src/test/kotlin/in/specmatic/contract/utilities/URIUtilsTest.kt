package `in`.specmatic.contract.utilities

import `in`.specmatic.core.utilities.URIUtils.parsePathParams
import `in`.specmatic.core.utilities.URIUtils.parseQuery
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.net.URISyntaxException
import java.util.*

internal class URIUtilsTest {
    @Test
    @Throws(URISyntaxException::class)
    fun shouldExtractPathParam() {
        val uri = URI("/pets/(petid:number)")
        val pathParameters = parsePathParams(uri.rawPath)
        Assertions.assertEquals(pathParameters["petid"], "(number)")
    }

    @Test
    @Throws(URISyntaxException::class)
    fun shouldExtractMultiplePathParam() {
        val uri = URI("/pets/(petid:number)/owners/(owner:string)")
        val pathParameters = parsePathParams(uri.rawPath)
        Assertions.assertEquals(pathParameters["petid"], "(number)")
        Assertions.assertEquals(pathParameters["owner"], "(string)")
    }

    @Test
    fun shouldReturnEmptyMapWhenQueryNull() {
        Assertions.assertEquals(Collections.EMPTY_MAP, parseQuery(null))
    }

    @Test
    fun shouldReturnMapWhenQueryPassed() {
        val queryParamsMap = HashMap<String, String>()

        queryParamsMap.put(" test", "10")
        queryParamsMap.put(" some query", "11")
        Assertions.assertEquals(queryParamsMap[" test"], parseQuery("some query = 11 & test=10")[" test"])
        Assertions.assertEquals(queryParamsMap["some query"], parseQuery("some query = 11 & test=10")[" some query"])
    }

    @Test
    fun shouldReturnMapWhenQueryInvalid() {
        assertThrows<Exception> { parseQuery("some query = 11 & test10")[" test"] }
    }
}