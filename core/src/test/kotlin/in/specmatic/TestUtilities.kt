package `in`.specmatic

import org.assertj.core.api.Assertions
import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.AnyPattern
import `in`.specmatic.core.pattern.DeferredPattern
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.value.Value
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.HttpStubData
import `in`.specmatic.stub.HttpStubResponse
import `in`.specmatic.stub.ThreadSafeListOfStubs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun optionalPattern(pattern: Pattern): AnyPattern = AnyPattern(listOf(DeferredPattern("(empty)"), pattern))

infix fun Value.shouldMatch(pattern: Pattern) {
    val result = pattern.matches(this, Resolver())
    if(!result.isTrue()) println(resultReport(result))
    assertTrue(result.isTrue())
}

infix fun Value.shouldNotMatch(pattern: Pattern) {
    assertFalse(pattern.matches(this, Resolver()).isTrue())
}

fun emptyPattern() = DeferredPattern("(empty)")

infix fun String.backwardCompatibleWith(oldContractGherkin: String) {
    val results = testBackwardCompatibility(oldContractGherkin)
    Assertions.assertThat(results.success()).isTrue()
    Assertions.assertThat(results.failureCount).isZero()

    stubsFrom(oldContractGherkin).workWith(this)
}

infix fun String.notBackwardCompatibleWith(oldContractGherkin: String) {
    val results = testBackwardCompatibility(oldContractGherkin)
    Assertions.assertThat(results.success()).isFalse()
    Assertions.assertThat(results.failureCount).isPositive()

    stubsFrom(oldContractGherkin).breakOn(this)
}

fun String.testBackwardCompatibility(oldContractGherkin: String): Results {
    val oldFeature = parseGherkinStringToFeature(oldContractGherkin)
    val newFeature = parseGherkinStringToFeature(this)
    return testBackwardCompatibility(oldFeature, newFeature)
}

fun stubShouldNotBreak(stubRequest: HttpRequest, stubResponse: HttpResponse, oldContract: String, newContract: String) {
    val responseFromOldContract = testStub(oldContract, stubRequest, stubResponse)
    Assertions.assertThat(responseFromOldContract).isEqualTo(stubResponse)

    val responseFromNewContract = testStub(newContract, stubRequest, stubResponse)
    Assertions.assertThat(responseFromNewContract.status).isEqualTo(200)
}

fun stubShouldBreak(stubRequest: HttpRequest, stubResponse: HttpResponse, oldContract: String, newContract: String) {
    val responseFromOldContract = testStub(oldContract, stubRequest, stubResponse)
    Assertions.assertThat(responseFromOldContract).isEqualTo(stubResponse)

    val responseFromNewContract = try {
        testStub(newContract, stubRequest, stubResponse)
    } catch(e: Throwable) {
        println(exceptionCauseMessage(e))
        return
    }

    Assertions.assertThat(responseFromNewContract.status).isEqualTo(400)
}

fun stubResponse(httpRequest: HttpRequest, features: List<Feature>, threadSafeStubs: List<HttpStubData>, strictMode: Boolean): HttpStubResponse {
    return `in`.specmatic.stub.getHttpResponse(
        httpRequest,
        features,
        ThreadSafeListOfStubs(threadSafeStubs.toMutableList()),
        strictMode
    )
}

fun testStub(contractGherkin: String, stubRequest: HttpRequest, stubResponse: HttpResponse): HttpResponse {
    val feature = parseGherkinStringToFeature(contractGherkin)
    val stub = ScenarioStub(stubRequest, stubResponse)
    val matchingStub = feature.matchingStub(stub)

    return stubResponse(stubRequest, listOf(feature), listOf(matchingStub), true).let {
        it.response.copy(headers = it.response.headers - SPECMATIC_RESULT_HEADER)
    }
}

fun stub(stubRequest: HttpRequest, stubResponse: HttpResponse): TestHttpStub =
        TestHttpStub(stubRequest, stubResponse)

private fun stubsFrom(oldContract: String): TestHttpStubData {
    val oldFeature = parseGherkinStringToFeature(oldContract)

    val testScenarios = oldFeature.generateBackwardCompatibilityTestScenarios()

    return TestHttpStubData(oldContract, testScenarios.map { scenario ->
        val request = scenario.generateHttpRequest()
        val response = scenario.generateHttpResponse(emptyMap())

        TestHttpStub(stubRequest = request, stubResponse = response.copy(headers = response.headers.minus(SPECMATIC_RESULT_HEADER)))
    })

}

private class TestHttpStubData(val oldContract: String, val stubs: List<TestHttpStub>) {
    fun breakOn(newContract: String) {
        for(stub in stubs) {
            stub.shouldWorkWith(oldContract)
            stub.shouldBreakWith(newContract)
        }
    }

    fun workWith(newContract: String) {
        for(stub in stubs) {
            stub.shouldWorkWith(oldContract)
            stub.shouldWorkWith(newContract)
        }
    }
}
