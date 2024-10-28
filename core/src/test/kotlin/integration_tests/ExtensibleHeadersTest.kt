package integration_tests

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.test.TestExecutor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExtensibleHeadersTest {

//    @Test
//    fun `should assert that additional 'X-Custom-Header' (not in spec) is present in the request`() {
//        val feature = OpenApiSpecification.fromFile("src/test/resources/openapi/specs_for_additional_headers_in_examples/additional_headers_test.yaml").toFeature().loadExternalisedExamples()
//
//        var customHeaderValue: String? = null
//
//        feature.executeTests(object : TestExecutor {
//            override fun execute(request: HttpRequest): HttpResponse {
//                println(request.toLogString())
//
//                customHeaderValue = request.headers["X-Custom-Header"]
//
//                return HttpResponse.OK
//            }
//        })
//
//        assertNotNull(customHeaderValue, "'X-Custom-Header' must be present in the request.")
//        assertEquals("Custom---Value", customHeaderValue, "Unexpected value for 'X-Custom-Header'.")
//
//        println("'X-Custom-Header' is present and has the correct value.")
//    }
}
