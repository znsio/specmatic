package application.backwardCompatibility

data class ParseResult(
    val specPath: String,
    val errorMessages: Set<String>
)