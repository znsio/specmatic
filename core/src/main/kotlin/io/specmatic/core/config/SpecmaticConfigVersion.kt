package io.specmatic.core.config

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.config.v1.SpecmaticConfigV1
import io.specmatic.core.config.v2.SpecmaticConfigV2
import io.specmatic.core.utilities.onceOnly

enum class SpecmaticConfigVersion(@JsonValue val value: Int, val configLoader: SpecmaticVersionedConfigLoader) {
    VERSION_1(1, SpecmaticConfigV1.Companion),
    VERSION_2(2, SpecmaticConfigV2.Companion);

    fun printWarningForOlderVersions() {
        if (this == getLatestVersion())
           return

        onceOnly("config_warning") {
            println("WARNING: The Specmatic configuration file is of an older version. Please update it to the latest version using the \"config update\" sub-command of Specmatic.")
        }
    }

    companion object {
        @JsonCreator
        fun getByValue(value: Int): SpecmaticConfigVersion? {
            return entries.find { it.value == value }
        }

        fun getLatestVersion(): SpecmaticConfigVersion {
            return entries.maxBy { it.value }
        }

        fun isValidVersion(version: SpecmaticConfigVersion): Boolean {
            return entries.any { it == version }
        }

        fun convertToLatestVersionedConfig(config: SpecmaticConfig): SpecmaticVersionedConfig {
            return getLatestVersion().configLoader.loadFrom(config)
        }
    }
}