package `in`.specmatic.contract.utilities

import `in`.specmatic.core.utilities.URIUtils.parsePathParams
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
    @Throws(URISyntaxException::class)
    fun shouldParseQueryToExtractSingleParam() {
        val query = "name='Manoj'"
        val pathParameters = parsePathParams(query)
        Assertions.assertTrue(pathParameters.size == 1);
        Assertions.assertEquals(pathParameters["name"], "'Manoj'")
    }

    @Test
    @Throws(URISyntaxException::class)
    fun shouldParseQueryToExtractMultipleParam() {
        val query = "name='Manoj'&age=26&gender='MALE'"
        val pathParameters = parsePathParams(query)
        Assertions.assertTrue(pathParameters.size > 1);
        Assertions.assertEquals(pathParameters["name"], "'Manoj'")
        Assertions.assertEquals(pathParameters["age"], "26")
        Assertions.assertEquals(pathParameters["gender"], "'MALE'")
    }

    @Test
    @Throws(URISyntaxException::class)
    fun shouldReturnEmptyMapForEmptyQuery() {
        val query = ""
        val pathParameters = parsePathParams(query)
        Assertions.assertTrue(pathParameters.isEmpty());
    }
}