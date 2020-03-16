package run.qontract.test

import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.Result
import org.assertj.core.api.AbstractAssert
import java.util.*

class ResultAssert(actual: Result) : AbstractAssert<ResultAssert, Result>(actual, ResultAssert::class.java) {
    companion object {
        fun assertThat(actual: Result): ResultAssert {
            return ResultAssert(actual)
        }
    }

    fun isSuccess(request: HttpRequest, response: HttpResponse?) {
        isNotNull

        when(actual) {
            is Result.Failure -> failWithMessage(generateErrorMessage(actual.stackTrace(), request, response))
            else -> this
        }
    }

    private fun generateErrorMessage(stackTrace: Stack<String>, request: HttpRequest, response: HttpResponse?): String {
        val message = StringBuilder()
        while (stackTrace.isNotEmpty()) {
            message.appendln(stackTrace.pop())
        }
        message.appendln("Request: $request")
        response?.let {
            message.appendln("Response: $it")
        }
        return message.toString()
    }
}