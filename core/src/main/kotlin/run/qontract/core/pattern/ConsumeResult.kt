package run.qontract.core.pattern

import run.qontract.core.Result
import run.qontract.core.breadCrumb

data class ConsumeResult(val result: Result = Result.Success(), val remainder: List<Pattern> = emptyList()) {
    constructor(patterns: List<Pattern>): this(remainder = patterns)

    fun breadCrumb(breadCrumb: String): ConsumeResult = this.copy(result = result.breadCrumb(breadCrumb))
}