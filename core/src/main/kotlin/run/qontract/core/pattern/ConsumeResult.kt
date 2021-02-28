package run.qontract.core.pattern

import run.qontract.core.Result
import run.qontract.core.breadCrumb
import run.qontract.core.value.Value

data class ProvisionalError(val result: Result.Failure, val type: Pattern, val value: Value)

data class ConsumeResult<ListType>(val result: Result = Result.Success(), val remainder: List<ListType> = emptyList(), val provisionalError: ProvisionalError? = null) {
    constructor(patterns: List<ListType>): this(remainder = patterns)

    fun breadCrumb(breadCrumb: String): ConsumeResult<ListType> = this.copy(result = result.breadCrumb(breadCrumb))

    inline fun <reified OtherListType> cast(typeName: String): ConsumeResult<OtherListType> {
        val newList = remainder.map {
            if(it is OtherListType)
                it
            else
                throw ContractException("Could not cast list to $typeName type")
        }

        return ConsumeResult(result, newList, provisionalError)
    }
}