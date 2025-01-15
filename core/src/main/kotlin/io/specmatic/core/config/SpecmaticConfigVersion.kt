package io.specmatic.core.config

enum class SpecmaticConfigVersion(val version: Int) {
    VERSION_1(1),
    VERSION_2(2);

    companion object {
        fun getLatestVersion(): Int {
            return entries.toTypedArray().maxOf { it.version }
        }
    }
}