package io.specmatic.core.pattern

import io.specmatic.core.Resolver
import io.specmatic.core.Result

typealias TypeStack = Set<PairOfTypes>

fun biggerEncompassesSmaller(bigger: Pattern, smaller: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
    val pair = PairOfTypes(bigger.typeAlias, smaller.typeAlias)

    return try {
        when {
            pair.hasNoNulls() ->
                if (typeStack.contains(pair))
                    Result.Success()
                else
                    bigger.encompasses(resolvedHop(smaller, otherResolver), thisResolver, otherResolver, typeStack.plus(pair))
            else ->
                bigger.encompasses(resolvedHop(smaller, otherResolver), thisResolver, otherResolver, typeStack)
        }
    } catch(e: ContractException) {
        e.failure()
    }
}
