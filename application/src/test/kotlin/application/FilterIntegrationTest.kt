package application

import io.specmatic.core.TestResult
import io.specmatic.core.utilities.Flags
import io.specmatic.stub.ContractStub
import io.specmatic.stub.createStub
import io.specmatic.test.SpecmaticJUnitSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class FilterIntegrationTest {

    @ParameterizedTest
    @MethodSource("filterProvider")
    fun contractTestWithDifferentFilters(filter: String, expectedSuccessfulTestCount: Int) {
        System.setProperty("filter", filter)

        SpecmaticJUnitSupport().contractTest().forEach { it.executable.execute() }

        val count = SpecmaticJUnitSupport.openApiCoverageReportInput.generate().testResultRecords.count {
            it.result == TestResult.Success
        }
        assertEquals(expectedSuccessfulTestCount, count)
    }

    companion object {
        @JvmStatic
        fun filterProvider(): Stream<Arguments> {
            return Stream.of(
//                TODO: Need to fix all the commented out test cases
//                Arguments.of("EXAMPLE_NAME='SUCCESS'", 4),
                Arguments.of("EXAMPLE_NAME!='SUCCESS'", 1),
//                Arguments.of("EXAMPLE_NAME='SUCCESS,TIMEOUT'", 5),
                Arguments.of("EXAMPLE_NAME!='SUCCESS,TIMEOUT'", 0),
                Arguments.of("QUERY='type'", 2),
                Arguments.of("QUERY!='type'", 3),
                Arguments.of("QUERY='type,sortBy'", 2),
                Arguments.of("QUERY!='type,sortBy'", 3),
                Arguments.of("HEADERS='request-id'", 2),
                Arguments.of("HEADERS!='request-id'", 3),
                Arguments.of("HEADERS='request-id,pageSize'", 2),
                Arguments.of("HEADERS!='request-id,pageSize'", 3),
                Arguments.of("PATH='/findAvailableProducts' && METHOD='GET' && STATUS<'400'", 1),
                Arguments.of("PATH='/orders' && STATUS<'400'", 2),
                Arguments.of("PATH='/findAvailableProducts' && STATUS>='200' && STATUS <='299'", 1),
                Arguments.of("(PATH='/findAvailableProducts' || PATH='/orders') && STATUS>='200' && STATUS <='299'", 3),
                Arguments.of("PATH='/findAvailableProducts,/orders' && STATUS>='200' && STATUS <='299'", 3),
                Arguments.of("PATH='/*' && STATUS>='200' && STATUS <='299' && METHOD='POST'", 2),
                Arguments.of("STATUS>='200' && STATUS <='299' && METHOD='DELETE'", 0),
                Arguments.of("STATUS>='200' && STATUS <='299' && METHOD='GET'", 2),
                Arguments.of("STATUS>'199' && STATUS<'300' && METHOD='GET'", 2),
                Arguments.of("STATUS>='200' && STATUS<='300' && METHOD='GET'", 2),
                Arguments.of("PATH='/findAvailableProducts' && QUERY='type' && STATUS='200'", 1),
                Arguments.of("PATH='/findAvailableProducts' && QUERY!='type' && STATUS='200'", 0),
                Arguments.of("PATH='/findAvailableProducts' && EXAMPLE_NAME!='TIMEOUT' && STATUS='200'", 1),
//                Arguments.of("PATH='/findAvailableProducts' && EXAMPLE_NAME='SUCCESS' && STATUS='2xx'", 1),
                Arguments.of("PATH='/orders,/products' && STATUS<'400'", 3),
                Arguments.of("PATH='/orders' && STATUS<'400'", 2),
                Arguments.of("PATH='/findAvailableProducts' && METHOD='GET' && STATUS<'400'", 1),
                Arguments.of("PATH='/orders,/products' && STATUS<'400' && QUERY='orderId'", 1),
                Arguments.of("PATH='/orders' && STATUS<'400' && QUERY!='orderId'", 1),
                Arguments.of("PATH='/findAvailableProducts' && HEADERS='request-id' && STATUS<'400'", 1),
                Arguments.of("PATH='/findAvailableProducts' && HEADERS!='request-id' && STATUS<'400'", 0),
            )
        }

        private lateinit var httpStub: ContractStub

        @JvmStatic
        @BeforeAll
        fun setUp() {
            System.setProperty("testBaseURL", "http://localhost:9000")
            System.setProperty(Flags.CONFIG_FILE_PATH, "src/test/resources/filter_test/specmatic_filter.yaml")

            // Start Specmatic Http Stub
            httpStub = createStub()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            // Shutdown Specmatic Http Stub
            httpStub.close()
        }
    }
}
