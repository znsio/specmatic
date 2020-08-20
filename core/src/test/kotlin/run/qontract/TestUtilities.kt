package run.qontract

import org.assertj.core.api.Assertions
import run.qontract.core.*
import run.qontract.core.pattern.AnyPattern
import run.qontract.core.pattern.DeferredPattern
import run.qontract.core.pattern.Pattern
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.core.value.Value
import run.qontract.mock.ScenarioStub
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
}

infix fun String.notBackwardCompatibleWith(oldContractGherkin: String) {
    val results = testBackwardCompatibility(oldContractGherkin)
    Assertions.assertThat(results.success()).isFalse()
    Assertions.assertThat(results.failureCount).isPositive()
}

fun String.testBackwardCompatibility(oldContractGherkin: String): Results {
    val oldFeature = Feature(oldContractGherkin)
    val newFeature = Feature(this)
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

fun testStub(contractGherkin: String, stubRequest: HttpRequest, stubResponse: HttpResponse): HttpResponse {
    val feature = Feature(contractGherkin)
    val stub = ScenarioStub(stubRequest, stubResponse)
    val matchingStub = feature.matchingStub(stub)

    return run.qontract.stub.stubResponse(stubRequest, listOf(feature), listOf(matchingStub), true).let {
        it.copy(headers = it.headers - QONTRACT_RESULT_HEADER)
    }
}

fun stub(stubRequest: HttpRequest, stubResponse: HttpResponse): TestHttpStubData =
        TestHttpStubData(stubRequest, stubResponse)
