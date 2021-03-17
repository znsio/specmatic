package `in`.specmatic.test

import org.assertj.core.api.AbstractAssert
import `in`.specmatic.core.Result
import `in`.specmatic.core.resultReport

class ResultAssert(result: Result) : AbstractAssert<ResultAssert, Result>(result, ResultAssert::class.java) {
    companion object {
        fun assertThat(actual: Result): ResultAssert {
            return ResultAssert(actual)
        }
    }

    fun isSuccess() {
        isNotNull

        if(actual is Result.Failure) {
            failWithMessage(resultReport(actual, "Testing scenario"))
        }
    }
}
