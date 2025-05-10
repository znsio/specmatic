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
        println("Running contract test with filter: $filter")
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
                Arguments.of("PATH='/findAvailableProducts' && METHOD='GET' && STATUS!='4xx' && STATUS!='5xx'", 2),
                Arguments.of("PATH='/orders' && STATUS!='4xx' && STATUS!='5xx'", 3),
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