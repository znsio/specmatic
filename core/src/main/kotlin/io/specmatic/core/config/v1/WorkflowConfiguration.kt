package io.specmatic.core.config.v1

data class WorkflowIDOperation(
    val extract: String? = null,
    val use: String? = null
)

data class WorkflowConfiguration(
    val ids: Map<String, WorkflowIDOperation> = emptyMap()
)