package io.specmatic.core.config.v1

enum class PipelineProvider { azure }

data class Pipeline(
    val provider: PipelineProvider = PipelineProvider.azure,
    val organization: String = "",
    val project: String = "",
    val definitionId: Int = 0
)