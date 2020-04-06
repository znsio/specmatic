package run.qontract.test

class SyntaxException(message: String?) : Exception(message)

fun missingParam(missingValue: String): SyntaxException {
    return SyntaxException("$missingValue is missing. Can't generate the contract test.")
}
