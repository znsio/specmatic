package io.specmatic.core.examples.server

data class FixExampleResult(
    val status: FixExampleStatus,
    val exampleFileName: String,
    val error: Throwable? = null
)

enum class FixExampleStatus {
    SUCCEDED,
    SKIPPED,
    FAILED
}