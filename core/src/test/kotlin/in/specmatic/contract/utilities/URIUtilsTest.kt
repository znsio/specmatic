package `in`.specmatic.contract.utilities

import `in`.specmatic.core.utilities.URIUtils.parsePathParams
import `in`.specmatic.core.utilities.URIUtils.parseQuery
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

    fun given_ParseQuery_When_Null_Then_ThrowsException() {
        val resultMap = parseQuery(null)
        Assertions.assertEquals(resultMap, emptyMap<String, String>())
    }

    @Test
    @Throws(Exception::class)
    fun given_ParseQuery_When_InValidString_Then_ThrowsException() {
        assertThrows<Exception> {
            parseQuery("abc&xyz")
        }.also {
            org.assertj.core.api.Assertions.assertThat(it.message)
                .isEqualTo("a part of the query string does not seem to be a key-value pair: abc")
        }
    }

    @Test
    fun given_ParseQuery_When_ValidString_Then_ReturnEmptyStringWithValidKeys() {
        val resultantMap = parseQuery("abc=&xyz=")
        Assertions.assertEquals(resultantMap["abc"], "")
        Assertions.assertEquals(resultantMap["xyz"], "")
    }
}