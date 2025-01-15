package io.specmatic.core.config

enum class SpecmaticConfigVersion(val value: Int) {
    VERSION_1(1),
    VERSION_2(2);

    companion object {
        fun getSpecmaticConfigVersion(version: Int): SpecmaticConfigVersion? {
            return entries.find { it.value == version }
        }

        fun getLatestVersion(): SpecmaticConfigVersion {
            return entries.maxBy { it.value }
        }
    }
}