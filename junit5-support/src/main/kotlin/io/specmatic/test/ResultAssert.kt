package io.specmatic.test

import io.specmatic.core.Result
import org.assertj.core.api.AbstractAssert

class ResultAssert(result: Result) : AbstractAssert<ResultAssert, Result>(result, ResultAssert::class.java) {
    companion object {
        fun assertThat(actual: Result): ResultAssert {
            return ResultAssert(actual)
        }
    }

    fun isSuccess() {
        isNotNull

        actual.let {
            if(it is Result.Failure) {
                failWithMessage(it.toFailureReport("Testing scenario").toText())
            }
        }
    }
}
