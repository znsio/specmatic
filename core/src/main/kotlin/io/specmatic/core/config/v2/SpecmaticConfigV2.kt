package io.specmatic.core.config.v2

import com.fasterxml.jackson.annotation.JsonAlias
import io.specmatic.core.*
import io.specmatic.core.config.SpecmaticConfigVersion
import io.specmatic.core.utilities.Flags
import io.specmatic.core.utilities.Flags.Companion.EXAMPLE_DIRECTORIES
import io.specmatic.core.utilities.Flags.Companion.getBooleanValue
import io.specmatic.core.utilities.Flags.Companion.getStringValue

data class SpecmaticConfigV2(
    val version: SpecmaticConfigVersion,
    val contracts: List<ContractConfig> = emptyList(),
    val auth: Auth? = null,
    val pipeline: Pipeline? = null,
    val environments: Map<String, Environment>? = null,
    val hooks: Map<String, String> = emptyMap(),
    val repository: RepositoryInfo? = null,
    val report: ReportConfiguration? = null,
    val security: SecurityConfiguration? = null,
    val test: TestConfiguration? = TestConfiguration(),
    val stub: StubConfiguration = StubConfiguration(),
    @field:JsonAlias("virtual_service") val virtualService: VirtualServiceConfiguration = VirtualServiceConfiguration(),
    val examples: List<String> = getStringValue(EXAMPLE_DIRECTORIES)?.split(",") ?: emptyList(),
    val workflow: WorkflowConfiguration? = null,
    val ignoreInlineExamples: Boolean = getBooleanValue(Flags.IGNORE_INLINE_EXAMPLES),
    val additionalExampleParamsFilePath: String? = getStringValue(Flags.ADDITIONAL_EXAMPLE_PARAMS_FILE),
    @field:JsonAlias("attribute_selection_pattern") val attributeSelectionPattern: AttributeSelectionPattern = AttributeSelectionPattern(),
    @field:JsonAlias("all_patterns_mandatory") val allPatternsMandatory: Boolean = getBooleanValue(Flags.ALL_PATTERNS_MANDATORY),
    @field:JsonAlias("default_pattern_values") val defaultPatternValues: Map<String, Any> = emptyMap()
) {
    fun transform(): SpecmaticConfig {
        return SpecmaticConfig(
            version = SpecmaticConfigVersion.VERSION_2,
            sources = this.contracts.map { contract -> contract.transform() },
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

