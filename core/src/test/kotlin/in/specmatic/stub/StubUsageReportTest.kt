package `in`.specmatic.stub

import `in`.specmatic.stub.report.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class StubUsageReportTest {

    companion object {
        const val CONFIG_FILE_PATH = "./specmatic.json"
    }

    @Test
    fun `test generates stub usage report based on stub request logs`() {
        val stubApis = mutableListOf(
            StubApi("/route1", "GET", 200, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route1.yaml", "HTTP"),
            StubApi("/route1", "POST", 200, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route1.yaml", "HTTP"),
            StubApi("/route2", "GET", 200, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route2.yaml", "HTTP"),
            StubApi("/route2", "POST", 200, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route2.yaml", "HTTP"),
        )

        val stubRequestLogs = mutableListOf(
            StubApi("/route1", "GET", 200, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route1.yaml", "HTTP"),
            StubApi("/route1", "GET", 200, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route1.yaml", "HTTP"),
            StubApi("/route1", "POST", 200, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route1.yaml", "HTTP"),
            StubApi("/route1", "POST", 200, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route1.yaml", "HTTP"),
            StubApi("/route2", "GET", 200, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route2.yaml", "HTTP"),
            StubApi("/route2", "GET", 200, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route2.yaml", "HTTP"),
        )

        val stubUsageJsonReport = StubUsageReport(CONFIG_FILE_PATH, stubApis, stubRequestLogs).generate()
        Assertions.assertThat(stubUsageJsonReport).isEqualTo(
            StubUsageJsonReport(
                CONFIG_FILE_PATH, listOf(
                    StubUsageJsonRow(
                        "git",
                        "https://github.com/znsio/specmatic-order-contracts.git",
                        "main",
                        "in/specmatic/examples/store/route1.yaml",
                        "HTTP",
                        listOf(
                            StubUsageOperation("/route1", "GET",200, 2),
                            StubUsageOperation( "/route1", "POST",200, 2)
                        )
                    ),
                    StubUsageJsonRow(
                        "git",
                        "https://github.com/znsio/specmatic-order-contracts.git",
                        "main",
                        "in/specmatic/examples/store/route2.yaml",
                        "HTTP",
                        listOf(
                            StubUsageOperation( "/route2", "GET",200, 2),
                            StubUsageOperation( "/route2", "POST",200, 0)
                        )
                    )
                )
            )
        )
    }
}