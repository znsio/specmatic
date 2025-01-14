package io.specmatic.core.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.config.v1.SPECMATIC_CONFIG_VERSION_1
import io.specmatic.core.config.v1.SpecmaticConfigV1
import io.specmatic.core.config.v2.SPECMATIC_CONFIG_VERSION_2
import io.specmatic.core.config.v2.SpecmaticConfigV2
import io.specmatic.core.pattern.ContractException
import java.io.File

private const val SPECMATIC_CONFIG_VERSION = "version"

private val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

fun File.toSpecmaticConfig(): SpecmaticConfig {
    val configYaml = this.readText()
    return when (val configVersion = configYaml.getVersion()) {
        SPECMATIC_CONFIG_VERSION_1 -> objectMapper.readValue(configYaml, SpecmaticConfigV1::class.java).transform()
        SPECMATIC_CONFIG_VERSION_2 -> objectMapper.readValue(configYaml, SpecmaticConfigV2::class.java).transform()
        else -> throw ContractException("Unsupported Specmatic config version: $configVersion")
    }
}

private fun String.getVersion(): Int? {
    return objectMapper.readTree(this).get(SPECMATIC_CONFIG_VERSION)?.asInt() ?: SPECMATIC_CONFIG_VERSION_1
}
