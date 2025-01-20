package io.specmatic.core.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import io.specmatic.core.*
import io.specmatic.core.config.v2.ContractConfig

class AttributeSelectionPatternSerializer : JsonSerializer<AttributeSelectionPattern>() {
    override fun serialize(
        attributeSelectionPattern: AttributeSelectionPattern,
        gen: JsonGenerator,
        provider: SerializerProvider
    ) {
        if (attributeSelectionPattern.defaultFields.isEmpty() && attributeSelectionPattern.queryParamKey.isBlank()) {
            return
        }

        gen.writeStartObject()
        if (attributeSelectionPattern.defaultFields.isNotEmpty()) {
            gen.writeObjectField("defaultFields", attributeSelectionPattern.defaultFields)
        }
        if (attributeSelectionPattern.queryParamKey.isNotBlank()) {
            gen.writeObjectField("queryParamKey", attributeSelectionPattern.queryParamKey)
        }
        gen.writeEndObject()
    }

    override fun isEmpty(
        provider: SerializerProvider?,
        attributeSelectionPattern: AttributeSelectionPattern?
    ): Boolean {
        if (attributeSelectionPattern == null) return true
        return attributeSelectionPattern.defaultFields.isEmpty() && attributeSelectionPattern.queryParamKey.isBlank()
    }
}

class VirtualServiceSerializer : JsonSerializer<VirtualServiceConfiguration>() {
    override fun serialize(
        virtualServiceConfiguration: VirtualServiceConfiguration,
        gen: JsonGenerator,
        provider: SerializerProvider
    ) {
        if (virtualServiceConfiguration.nonPatchableKeys.isEmpty()) {
            return
        }

        gen.writeStartObject()
        gen.writeObjectField("nonPatchableKeys", virtualServiceConfiguration.nonPatchableKeys)
        gen.writeEndObject()
    }

    override fun isEmpty(
        provider: SerializerProvider?,
        virtualServiceConfiguration: VirtualServiceConfiguration?
    ): Boolean {
        if (virtualServiceConfiguration == null) return true
        return virtualServiceConfiguration.nonPatchableKeys.isEmpty()
    }
}

class TestConfigurationSerializer : JsonSerializer<TestConfiguration>() {
    override fun serialize(
        testConfiguration: TestConfiguration,
        gen: JsonGenerator,
        provider: SerializerProvider
    ) {
        if (testConfiguration.resiliencyTests?.enable?.equals(ResiliencyTestSuite.none) == true) {
            return
        }

        gen.writeStartObject()
        gen.writeObjectField("resiliencyTests", testConfiguration.resiliencyTests)
        gen.writeObjectField("validateResponseValues", testConfiguration.validateResponseValues)
        gen.writeObjectField("allowExtensibleSchema", testConfiguration.allowExtensibleSchema)
        gen.writeObjectField("timeoutInMilliseconds", testConfiguration.timeoutInMilliseconds)
        gen.writeEndObject()
    }

    override fun isEmpty(
        provider: SerializerProvider?,
        testConfiguration: TestConfiguration?
    ): Boolean {
        if (testConfiguration == null) return true
        return testConfiguration.resiliencyTests?.enable?.equals(ResiliencyTestSuite.none) == true
    }
}

class StubConfigurationSerializer : JsonSerializer<StubConfiguration>() {
    override fun serialize(stubConfiguration: StubConfiguration, gen: JsonGenerator, provider: SerializerProvider?) {
        if (stubConfiguration.includeMandatoryAndRequestedKeysInResponse == true) {
            return
        }

        gen.writeStartObject()
        gen.writeObjectField("generative", stubConfiguration.generative)
        gen.writeObjectField("delayInMilliseconds", stubConfiguration.delayInMilliseconds)
        gen.writeObjectField("dictionary", stubConfiguration.dictionary)
        gen.writeObjectField(
            "includeMandatoryAndRequestedKeysInResponse",
            stubConfiguration.includeMandatoryAndRequestedKeysInResponse
        )
        gen.writeEndObject()
    }

    override fun isEmpty(provider: SerializerProvider?, stubConfiguration: StubConfiguration?): Boolean {
        if (stubConfiguration == null) return true
        return stubConfiguration.includeMandatoryAndRequestedKeysInResponse == true
    }
}

class ContractConfigSerializer : JsonSerializer<ContractConfig>() {
    override fun serialize(contract: ContractConfig, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        if (!contract.contractSource.isEmpty()) {
            contract.contractSource.write(gen)
        }
        gen.writeObjectField("provides", contract.provides)
        gen.writeObjectField("consumes", contract.consumes)
        gen.writeEndObject()
    }

    override fun isEmpty(provider: SerializerProvider?, contract: ContractConfig?): Boolean {
        return contract?.contractSource?.isEmpty() == true
    }
}