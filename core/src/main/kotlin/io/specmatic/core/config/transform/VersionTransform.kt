package io.specmatic.core.config.transform

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.specmatic.core.config.SpecmaticConfig
import io.specmatic.core.config.v1.SpecmaticConfigV1
import io.specmatic.core.config.v2.SpecmaticConfigV2
import java.io.File

object VersionTransform {
    private val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    fun parseConfigFile(configFile: File): SpecmaticConfig {
        val configYaml = configFile.readText()
        return when (getVersion(configYaml)) {
            2 -> objectMapper.readValue(configYaml, SpecmaticConfigV2::class.java).transform()
            else -> objectMapper.readValue(configYaml, SpecmaticConfigV1::class.java).transform()
        }
    }

    private fun getVersion(configYaml: String): Int? {
        return objectMapper.readTree(configYaml).get("version")?.intValue()
    }
}