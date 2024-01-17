package `in`.specmatic.test

import `in`.specmatic.core.TestConfig
import `in`.specmatic.test.reports.coverage.Endpoint
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class SpecmaticJunitSupportTest {

    @Test
    fun `should retain open api path parameter convention for parameterized endpoints`(){
        val result: Pair<List<ContractTest>, List<Endpoint>> = SpecmaticJUnitSupport().loadTestScenarios(
            "./src/test/resources/spec_with_parameterized_paths.yaml",
            "",
            "",
            TestConfig(emptyMap(), emptyMap()),
            filterName = null,
            filterNotName = null
        )
        val specEndpoints = result.second
        Assertions.assertThat(specEndpoints.count()).isEqualTo(2)
        Assertions.assertThat(specEndpoints.all { it.path == "/sayHello/{name}" })
    }
}