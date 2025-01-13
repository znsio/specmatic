package io.specmatic.core.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.config.v1.SpecmaticConfigV1
import io.specmatic.core.config.v2.SpecmaticConfigV2
import java.io.File

private const val SPECMATIC_CONFIG_VERSION = "version"

class SpecmaticConfigFactory {
    private val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    fun create(configFile: File): SpecmaticConfig {
        val configYaml = configFile.readText()
        return when (getVersion(configYaml)) {
            2 -> objectMapper.readValue(configYaml, SpecmaticConfigV2::class.java).transform()
            else -> objectMapper.readValue(configYaml, SpecmaticConfigV1::class.java).transform()
        }
    }

    private fun getVersion(configYaml: String): Int? {
        return objectMapper.readTree(configYaml).get(SPECMATIC_CONFIG_VERSION)?.intValue()
    }
}