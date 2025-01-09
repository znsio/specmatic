package io.specmatic.core.config.v1

data class VirtualServiceConfiguration(
    val nonPatchableKeys: Set<String> = emptySet()
)