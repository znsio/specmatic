package io.specmatic.core.config

import io.specmatic.core.SpecmaticConfig

interface SpecmaticVersionedConfigLoader {
    fun loadFrom(config: SpecmaticConfig): SpecmaticVersionedConfig
}