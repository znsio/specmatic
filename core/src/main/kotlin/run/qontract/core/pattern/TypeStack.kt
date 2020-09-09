package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result

typealias TypeStack = Set<PairOfTypes>

fun biggerEncompassesSmaller(bigger: Pattern, smaller: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
    val pair = PairOfTypes(bigger.typeAlias, smaller.typeAlias)

    return when {
        pair.hasNoNulls() ->
            if(typeStack.contains(pair))
                Result.Success()
            else
                bigger.encompasses(resolvedHop(smaller, otherResolver), thisResolver, otherResolver, typeStack.plus(pair))
        else ->
            bigger.encompasses(resolvedHop(smaller, otherResolver), thisResolver, otherResolver, typeStack)
    }
}
