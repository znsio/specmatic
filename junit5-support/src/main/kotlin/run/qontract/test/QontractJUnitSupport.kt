package run.qontract.test

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import run.qontract.core.*
import run.qontract.core.utilities.readFile
import java.util.*

open class QontractJUnitSupport {

    @TestFactory()
    fun contractAsTest(): Collection<DynamicTest> {
        var testScenarios: List<Scenario>
        val path = System.getProperty("path")
        val suggestionsPath = System.getProperty("suggestions")

        val contractGherkin = try {
            readFile(path)
        } catch (exception: Throwable) {
            println("Exception (Class=${exception.javaClass.name}, Message=${exception.message ?: exception.localizedMessage})")
            throw exception
        }

        val contractBehaviour = try {
            ContractBehaviour(contractGherkin)
        } catch (exception: Throwable) {
            println("Exception (Class=${exception.javaClass.name}, Message=${exception.message ?: exception.localizedMessage})")
            throw exception
        }

        testScenarios = if (suggestionsPath.isNullOrEmpty()) {
            contractBehaviour.generateTestScenarios(LinkedList())
        } else {
            val suggestionsGherkin = readFile(suggestionsPath)
            val suggestions = Suggestions(suggestionsGherkin).scenarios
            contractBehaviour.generateTestScenarios(suggestions)
        }
        return testScenarios.map {
            DynamicTest.dynamicTest("$it") {
                val host = System.getProperty("host")
                val port = System.getProperty("port")
                val httpClient = HttpClient("http://$host:$port")
                var result: Result
                httpClient.setServerState(it.serverState)
                val request = it.generateHttpRequest()
                var response: HttpResponse? = null
                result = try {
                    response = httpClient.execute(request)
                    it.matches(response)
                } catch (exception: Throwable) {
                    Result.Failure("Exception (Class=${exception.javaClass.name}, Message=${exception.message ?: exception.localizedMessage})")
                            .also { failure -> failure.updateScenario(it) }
                }
                ResultAssert.assertThat(result).isSuccess(request, response)
            }
        }.toList()
    }

}