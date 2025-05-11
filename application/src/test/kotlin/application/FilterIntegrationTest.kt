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
                Arguments.of("PATH='/findAvailableProducts' && METHOD='GET' && (STATUS!='4xx' && STATUS!='5xx')", 2),
                Arguments.of("PATH='/orders' && STATUS!='4xx' && STATUS!='5xx'", 3),
                Arguments.of("PATH='/findAvailableProducts' && STATUS='2xx'", 2),
                Arguments.of("(PATH='/findAvailableProducts' || PATH='/orders')  && STATUS='2xx'", 5),
                Arguments.of("PATH='/findAvailableProducts,/orders'  && STATUS='2xx'", 5),
                Arguments.of("PATH='/*'  && STATUS='2xx' && METHOD='POST'", 13),
                Arguments.of("STATUS='2xx' && METHOD='DELETE'", 0),
                Arguments.of("STATUS='2xx' && METHOD='GET'", 4),
                Arguments.of("STATUS>'199' && STATUS<'300' && METHOD='GET'", 4),
//                Arguments.of("PATH='/findAvailableProducts' && QUERY='type'", 1), - TODO: need to fix this
                Arguments.of("PATH='/findAvailableProducts' && QUERY!='type'", 1),
                Arguments.of("PATH='/findAvailableProducts' && HEADERS='pageSize' && STATUS='2xx'", 2),
                Arguments.of("PATH='/findAvailableProducts' && HEADERS!='pageSize' && STATUS='2xx'", 0),
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