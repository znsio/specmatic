package io.specmatic.core.examples.server

data class FixExampleResult(
    val status: FixExampleStatus,
    val exampleFileName: String
)

enum class FixExampleStatus {
    SUCCEDED,
    SKIPPED,
    FAILED
}