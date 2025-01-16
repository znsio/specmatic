package io.specmatic.core.config

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class SpecmaticConfigVersion(@JsonValue val value: Int) {
    VERSION_1(1),
    VERSION_2(2);

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
    }
}