package io.specmatic.core.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.specmatic.core.config.v2.SpecmaticConfigV2
import java.io.File

class SpecmaticConfigMapper {
    private val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    fun read(configFile: File): SpecmaticConfig {
        val configYaml = configFile.readText()
        return when (getVersion(configYaml)) {
            2 -> objectMapper.readValue(configYaml, SpecmaticConfigV2::class.java).transform()
            else -> objectMapper.readValue(configYaml, SpecmaticConfig::class.java)
        }
    }

    private fun getVersion(configYaml: String): Int? {
        return objectMapper.readTree(configYaml).get("version")?.intValue()
    }
}