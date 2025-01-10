package io.specmatic.core.config

data class WorkflowIDOperation(
    val extract: String? = null,
    val use: String? = null
)

data class WorkflowConfiguration(
    val ids: Map<String, WorkflowIDOperation> = emptyMap()
)