package `in`.specmatic.test

import org.assertj.core.api.AbstractAssert
import `in`.specmatic.core.Result
import `in`.specmatic.core.toReport

class ResultAssert(result: Result) : AbstractAssert<ResultAssert, Result>(result, ResultAssert::class.java) {
    companion object {
        fun assertThat(actual: Result): ResultAssert {
            return ResultAssert(actual)
        }
    }

    fun isSuccess() {
        isNotNull

        if(actual is Result.Failure) {
            failWithMessage(actual.toFailureReport("Testing scenario").toText())
        }
    }
}
