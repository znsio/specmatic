package `in`.specmatic.test

import `in`.specmatic.core.Result
import `in`.specmatic.core.Scenario
import `in`.specmatic.stub.testKafkaMessages
import kotlin.system.exitProcess
import `in`.specmatic.core.executeTest

class ScenarioTest(val scenario: Scenario, private val generativeTestingEnabled: Boolean = false) : ContractTest {
    override fun testResultRecord(result: Result): TestResultRecord {
        return TestResultRecord(scenario.path.replace(Regex("""\((.*):.*\)"""), "{$1}"), scenario.method, scenario.status, result.testResult())
    }

    override fun generateTestScenarios(
        testVariables: Map<String, String>,
        testBaseURLs: Map<String, String>
    ): List<ContractTest> {
        return scenario.generateContractTests(testVariables, testBaseURLs, generativeTestingEnabled)
    }

    override fun testDescription(): String {
        return scenario.testDescription()
    }

    override fun runTest(host: String?, port: String?, timeout: Int): Result {
        val kafkaMessagePattern = scenario.kafkaMessagePattern

        return when {
            kafkaMessagePattern != null -> runKafkaTest(scenario)
            else -> runHttpTest(timeout, host!!, port!!, scenario)
        }
    }

    override fun runTest(testBaseURL: String?, timeOut: Int): Result {
        val kafkaMessagePattern = scenario.kafkaMessagePattern

        return when {
            kafkaMessagePattern != null -> runKafkaTest(scenario)
            else -> {
                val httpClient = HttpClient(testBaseURL!!, timeout = timeOut)
                executeTest(scenario, httpClient).updateScenario(scenario)
            }
        }
    }

    private fun runKafkaTest(testScenario: Scenario): Result {
        if (System.getProperty("kafkaPort") == null) {
            println("The contract has a kafka message. Please specify the port of the Kafka instance to connect to.")
            exitProcess(1)
        }

        val commit = "true" == System.getProperty("commit")

        return testKafkaMessages(testScenario, getBootstrapKafkaServers(), commit)
    }

    private fun runHttpTest(timeout: Int, host: String, port: String, testScenario: Scenario): Result {
        val protocol = System.getProperty("protocol") ?: "http"

        return executeTest(protocol, host, port, timeout, testScenario).updateScenario(scenario)
    }

    private fun executeTest(protocol: String, host: String?, port: String?, timeout: Int, testScenario: Scenario): Result {
        val httpClient = HttpClient("$protocol://$host:$port", timeout = timeout)
        return executeTest(testScenario, httpClient)
    }

    private fun getBootstrapKafkaServers(): String {
        return when {
            System.getProperty("kafkaBootstrapServers") != null && System.getProperty("kafkaBootstrapServers").isNotEmpty() ->
                System.getProperty("kafkaBootstrapServers")
            else -> {
                val kafkaPort = System.getProperty("kafkaPort")?.toInt() ?: 9093
                val kafkaHost = System.getProperty("kafkaHost") ?: "localhost"
                """PLAINTEXT://$kafkaHost:$kafkaPort"""
            }
        }
    }
}
