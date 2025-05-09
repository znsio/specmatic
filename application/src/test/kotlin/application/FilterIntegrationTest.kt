package application

import io.specmatic.core.TestResult
import io.specmatic.core.utilities.Flags
import io.specmatic.stub.ContractStub
import io.specmatic.stub.createStub
import io.specmatic.test.SpecmaticContractTest
import io.specmatic.test.SpecmaticJUnitSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll

class FilterIntegrationTest: SpecmaticContractTest {

    companion object {
        private lateinit var httpStub: ContractStub

        @JvmStatic
        @BeforeAll
        fun setUp() {
            System.setProperty(Flags.CONFIG_FILE_PATH, "src/test/resources/filter_test/specmatic_filter.yaml")
            System.setProperty("testBaseURL", "http://localhost:9000")
            System.setProperty("filter", "PATH='/findAvailableProducts' && METHOD='GET' && STATUS!='4xx' && STATUS!='5xx'")
            // Start Specmatic Http Stub
            httpStub = createStub()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            // Shutdown Specmatic Http Stub
            httpStub.close()

            val count = SpecmaticJUnitSupport.openApiCoverageReportInput.generate().testResultRecords.count {
                it.result == TestResult.Success
            }
            assertEquals(2, count)
        }
    }
}