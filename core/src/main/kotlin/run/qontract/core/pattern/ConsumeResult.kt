package run.qontract.core.pattern

import run.qontract.core.Result
import run.qontract.core.breadCrumb

data class ProvisionalError<MatchedType>(val result: Result.Failure, val type: Pattern, val value: MatchedType)

data class ConsumeResult<ListType, MatchedType>(val result: Result = Result.Success(), val remainder: List<ListType> = emptyList(), val provisionalError: ProvisionalError<MatchedType>? = null) {
    constructor(patterns: List<ListType>): this(remainder = patterns)

    fun breadCrumb(breadCrumb: String): ConsumeResult<ListType, MatchedType> = this.copy(result = result.breadCrumb(breadCrumb))

    inline fun <reified OtherListType> cast(typeName: String): ConsumeResult<OtherListType, MatchedType> {
        val newList = remainder.map {
            if(it is OtherListType)
                it
            else
                throw ContractException("Could not cast list to $typeName type")
        }

        return ConsumeResult(result, newList, provisionalError)
    }
}