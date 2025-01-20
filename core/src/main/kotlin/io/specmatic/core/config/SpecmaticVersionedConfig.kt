package io.specmatic.core.config

import io.specmatic.core.SpecmaticConfig

interface SpecmaticVersionedConfig {
    fun transform(): SpecmaticConfig
}