package io.specmatic.core.config

enum class SpecmaticConfigVersion(val value: Int) {
    VERSION_1(1),
    VERSION_2(2);

    companion object {
        fun getLatestVersion(): SpecmaticConfigVersion {
            return entries.maxBy { it.value }
        }

        fun isValidVersion(version: Int): Boolean {
            return entries.any { it.value == version }
        }
    }
}