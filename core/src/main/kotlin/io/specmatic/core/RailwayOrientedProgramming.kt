package io.specmatic.core

sealed class MatchingResult<T>
data class MatchSuccess<T>(val value: T) : MatchingResult<T>()
data class MatchFailure<T>(val error: Result.Failure) : MatchingResult<T>()

infix fun <T, U> MatchingResult<T>.then(f: (T) -> MatchingResult<U>) =
        when (this) {
            is MatchSuccess -> f(this.value)
            is MatchFailure -> MatchFailure(this.error)
        }

// Pipe input: the beginning of a railway
infix fun <T, U> T.to(f: (T) -> MatchingResult<U>) = MatchSuccess(this) then f

// Handle error output: the end of a railway
infix fun <T> MatchingResult<T>.otherwise(f: (Result.Failure) -> MatchingResult<T>) =
        if (this is MatchFailure) f(this.error) else this

infix fun <T> MatchingResult<T>.toResult(f: (Result) -> Result) =
        when (this) {
            is MatchSuccess -> f(Result.Success())
            is MatchFailure -> f(this.error)
        }

fun <T> handleError(error: Result.Failure): MatchingResult<T> = MatchFailure(error)

fun returnResult(result: Result) = result

fun <T> summarize(parameters: Triple<T, Resolver, List<Result.Failure>>): MatchingResult<Triple<T, Resolver, List<Result.Failure>>> {
    val (_, _, failures) = parameters

    return if(failures.isNotEmpty())
        MatchFailure(Result.Failure.fromFailures(failures))
    else
        MatchSuccess(parameters)
}
