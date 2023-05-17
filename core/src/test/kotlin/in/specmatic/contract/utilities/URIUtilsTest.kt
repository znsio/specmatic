package `in`.specmatic.contract.utilities

import `in`.specmatic.core.utilities.URIUtils.parsePathParams
import `in`.specmatic.core.utilities.URIUtils.parseQuery
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URISyntaxException


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
    fun testParseQuery() {
        val query = "name=John&age=30&country=USA"
        val result = parseQuery(query)
        val expectedValidMap = mapOf(
                "name" to "John",
                "age" to "30",
                "country" to "USA"
        )
        Assertions.assertEquals(expectedValidMap,result)

    }
}