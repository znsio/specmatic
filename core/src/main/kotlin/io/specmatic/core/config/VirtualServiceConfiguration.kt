package io.specmatic.core.config

data class VirtualServiceConfiguration(
    val nonPatchableKeys: Set<String> = emptySet()
)