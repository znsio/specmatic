package io.specmatic.core.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.specmatic.core.config.v1.SpecmaticConfigV1
import io.specmatic.core.config.v2.ContractConfig
import io.specmatic.core.config.v2.FileSystemConfig
import io.specmatic.core.config.v2.GitConfig
import io.specmatic.core.config.v2.SpecmaticConfigV2
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.utilities.Flags
import io.specmatic.core.utilities.Flags.Companion.EXAMPLE_DIRECTORIES
import io.specmatic.core.utilities.Flags.Companion.getBooleanValue
import io.specmatic.core.utilities.Flags.Companion.getStringValue
import io.specmatic.core.value.Value

data class SpecmaticConfig(
    val sources: List<Source> = emptyList(),
    val auth: Auth? = null,
    val pipeline: Pipeline? = null,
    val environments: Map<String, Environment>? = null,
    val hooks: Map<String, String> = emptyMap(),
    val repository: RepositoryInfo? = null,
    val report: ReportConfiguration? = null,
    val security: SecurityConfiguration? = null,
    val test: TestConfiguration? = TestConfiguration(),
    val stub: StubConfiguration = StubConfiguration(),
    val virtualService: VirtualServiceConfiguration = VirtualServiceConfiguration(),
    val examples: List<String> = getStringValue(EXAMPLE_DIRECTORIES)?.split(",") ?: emptyList(),
    val workflow: WorkflowConfiguration? = null,
    val ignoreInlineExamples: Boolean = getBooleanValue(Flags.IGNORE_INLINE_EXAMPLES),
    val additionalExampleParamsFilePath: String? = getStringValue(Flags.ADDITIONAL_EXAMPLE_PARAMS_FILE),
    val attributeSelectionPattern: AttributeSelectionPattern = AttributeSelectionPattern(),
    val allPatternsMandatory: Boolean = getBooleanValue(Flags.ALL_PATTERNS_MANDATORY),
    val defaultPatternValues: Map<String, Any> = emptyMap(),
    val version: Int? = null
) {
    @JsonIgnore
    fun attributeSelectionQueryParamKey(): String {
        return attributeSelectionPattern.queryParamKey
    }

    @JsonIgnore
    fun isExtensibleSchemaEnabled(): Boolean {
        return (test?.allowExtensibleSchema == true)
    }
    @JsonIgnore
    fun isResiliencyTestingEnabled(): Boolean {
        return (test?.resiliencyTests?.enable != ResiliencyTestSuite.none)
    }
    @JsonIgnore
    fun isOnlyPositiveTestingEnabled(): Boolean {
        return (test?.resiliencyTests?.enable == ResiliencyTestSuite.positiveOnly)
    }
    @JsonIgnore
    fun isResponseValueValidationEnabled(): Boolean {
        return (test?.validateResponseValues == true)
    }
    @JsonIgnore
    fun parsedDefaultPatternValues(): Map<String, Value> {
        return parsedJSONObject(ObjectMapper().writeValueAsString(defaultPatternValues)).jsonObject
    }

    @JsonIgnore
    fun transformTo(version: Int): String {
        val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT)
        return when (version) {
            2 -> objectMapper.writeValueAsString(transformToV2())
            else -> objectMapper.writeValueAsString(transformToV1())
        }
    }

    private fun transformToV1(): SpecmaticConfigV1 {
        return SpecmaticConfigV1(
            version = 1,
            sources = this.sources,
            auth = this.auth,
            pipeline = this.pipeline,
            environments = this.environments,
            hooks = this.hooks,
            repository = this.repository,
            report = this.report,
            security = this.security,
            test = this.test,
            stub = this.stub,
            virtualService = this.virtualService,
            examples = this.examples,
            workflow = this.workflow,
            ignoreInlineExamples = this.ignoreInlineExamples,
            additionalExampleParamsFilePath = this.additionalExampleParamsFilePath,
            attributeSelectionPattern = this.attributeSelectionPattern,
            allPatternsMandatory = this.allPatternsMandatory,
            defaultPatternValues = this.defaultPatternValues
        )
    }

    private fun transformToV2(): SpecmaticConfigV2 {
        return SpecmaticConfigV2(
            version = 2,
            contracts = this.sources.map { source ->
                ContractConfig(
                    git = when (source.provider) {
                        SourceProvider.git -> GitConfig(url = source.repository, branch = source.branch)
                        else -> null
                    },
                    filesystem = when (source.provider) {
                        SourceProvider.filesystem -> FileSystemConfig(directory = source.directory)
                        else -> null
                    },
                    provides = source.test,
                    consumes = source.stub
                )
            },
            auth = this.auth,
            pipeline = this.pipeline,
            environments = this.environments,
            hooks = this.hooks,
            repository = this.repository,
            report = this.report,
            security = this.security,
            test = this.test,
            stub = this.stub,
            virtualService = this.virtualService,
            examples = this.examples,
            workflow = this.workflow,
            ignoreInlineExamples = this.ignoreInlineExamples,
            additionalExampleParamsFilePath = this.additionalExampleParamsFilePath,
            attributeSelectionPattern = this.attributeSelectionPattern,
            allPatternsMandatory = this.allPatternsMandatory,
            defaultPatternValues = this.defaultPatternValues
        )
    }
}