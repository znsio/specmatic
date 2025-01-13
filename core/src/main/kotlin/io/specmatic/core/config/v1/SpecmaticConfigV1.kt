package io.specmatic.core.config.v1

import com.fasterxml.jackson.annotation.JsonAlias
import io.specmatic.core.*
import io.specmatic.core.utilities.Flags
import io.specmatic.core.utilities.Flags.Companion.EXAMPLE_DIRECTORIES
import io.specmatic.core.utilities.Flags.Companion.getBooleanValue
import io.specmatic.core.utilities.Flags.Companion.getStringValue

data class SpecmaticConfigV1 (
	@field:JsonAlias("contract_repositories")
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
	@field:JsonAlias("virtual_service")
	val virtualService: VirtualServiceConfiguration = VirtualServiceConfiguration(),
	val examples: List<String> = getStringValue(EXAMPLE_DIRECTORIES)?.split(",") ?: emptyList(),
	val workflow: WorkflowConfiguration? = null,
	val ignoreInlineExamples: Boolean = getBooleanValue(Flags.IGNORE_INLINE_EXAMPLES),
	val additionalExampleParamsFilePath: String? = getStringValue(Flags.ADDITIONAL_EXAMPLE_PARAMS_FILE),
	@field:JsonAlias("attribute_selection_pattern")
	val attributeSelectionPattern: AttributeSelectionPattern = AttributeSelectionPattern(),
	@field:JsonAlias("all_patterns_mandatory")
	val allPatternsMandatory: Boolean = getBooleanValue(Flags.ALL_PATTERNS_MANDATORY),
	@field:JsonAlias("default_pattern_values")
	val defaultPatternValues: Map<String, Any> = emptyMap(),
	val version: Int? = null
){
	fun transform(): SpecmaticConfig {
		return SpecmaticConfig(
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
			defaultPatternValues = this.defaultPatternValues,
			version = this.version
		)
	}
}