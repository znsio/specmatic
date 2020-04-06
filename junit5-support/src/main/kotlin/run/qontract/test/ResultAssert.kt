package run.qontract.test

import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.Result
import org.assertj.core.api.AbstractAssert
import run.qontract.core.resultReport

class ResultAssert(actual: Result) : AbstractAssert<ResultAssert, Result>(actual, ResultAssert::class.java) {
    companion object {
        fun assertThat(actual: Result): ResultAssert {
            return ResultAssert(actual)
        }
    }

    fun isSuccess(request: HttpRequest, response: HttpResponse?) {
        isNotNull

        when(actual) {
            is Result.Failure -> failWithMessage(resultReport(actual, request, response))
            else -> this
        }
    }

}