package io.specmatic.core.filters

interface HasScenarioMetadata {
    fun toScenarioMetadata(): ScenarioMetadata
}