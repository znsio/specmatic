package run.qontract.core.pattern

import run.qontract.core.Result
import run.qontract.core.breadCrumb

data class ConsumeResult<ListType>(val result: Result = Result.Success(), val remainder: List<ListType> = emptyList()) {
    constructor(patterns: List<ListType>): this(remainder = patterns)

    fun breadCrumb(breadCrumb: String): ConsumeResult<ListType> = this.copy(result = result.breadCrumb(breadCrumb))

    inline fun <reified OtherListType> cast(typeName: String): ConsumeResult<OtherListType> {
        val newList = remainder.map {
            if(it is OtherListType)
                it
            else
                throw ContractException("Could not cast list to $typeName type")
        }

        return ConsumeResult(result, newList)
    }
}