package run.qontract.test

import org.assertj.core.api.AbstractAssert
import run.qontract.core.Result
import run.qontract.core.resultReport

class ResultAssert(actual: Result) : AbstractAssert<ResultAssert, Result>(actual, ResultAssert::class.java) {
    companion object {
        fun assertThat(actual: Result): ResultAssert {
            return ResultAssert(actual)
        }
    }

    fun isSuccess() {
        isNotNull

        if(actual is Result.Failure)
            failWithMessage(resultReport(actual, "Testing scenario"))
    }
}
