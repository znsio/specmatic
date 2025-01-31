package io.specmatic.core

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import io.specmatic.core.Configuration.Companion.configFilePath
import io.specmatic.core.config.SpecmaticConfigVersion
import io.specmatic.core.config.SpecmaticConfigVersion.VERSION_1
import io.specmatic.core.config.toSpecmaticConfig
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.utilities.Flags
import io.specmatic.core.utilities.Flags.Companion.EXAMPLE_DIRECTORIES
import io.specmatic.core.utilities.Flags.Companion.EXTENSIBLE_SCHEMA
import io.specmatic.core.utilities.Flags.Companion.ONLY_POSITIVE
import io.specmatic.core.utilities.Flags.Companion.SPECMATIC_GENERATIVE_TESTS
import io.specmatic.core.utilities.Flags.Companion.SPECMATIC_STUB_DELAY
import io.specmatic.core.utilities.Flags.Companion.SPECMATIC_TEST_TIMEOUT
import io.specmatic.core.utilities.Flags.Companion.VALIDATE_RESPONSE_VALUE
import io.specmatic.core.utilities.Flags.Companion.getBooleanValue
import io.specmatic.core.utilities.Flags.Companion.getLongValue
import io.specmatic.core.utilities.Flags.Companion.getStringValue
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.core.utilities.readEnvVarOrProperty
import io.specmatic.core.value.Value
import java.io.File

const val APPLICATION_NAME = "Specmatic"
const val APPLICATION_NAME_LOWER_CASE = "specmatic"
const val CONFIG_FILE_NAME_WITHOUT_EXT = "specmatic"
const val DEFAULT_TIMEOUT_IN_MILLISECONDS: Long = 6000L
const val CONTRACT_EXTENSION = "spec"
const val YAML = "yaml"
const val WSDL = "wsdl"
const val YML = "yml"
const val JSON = "json"
val CONFIG_EXTENSIONS = listOf(YAML, YML, JSON)
val OPENAPI_FILE_EXTENSIONS = listOf(YAML, YML, JSON)
val CONTRACT_EXTENSIONS = listOf(CONTRACT_EXTENSION, WSDL) + OPENAPI_FILE_EXTENSIONS
const val DATA_DIR_SUFFIX = "_data"
const val TEST_DIR_SUFFIX = "_tests"
const val EXAMPLES_DIR_SUFFIX = "_examples"
const val SPECMATIC_GITHUB_ISSUES = "https://github.com/znsio/specmatic/issues"
const val DEFAULT_WORKING_DIRECTORY = ".$APPLICATION_NAME_LOWER_CASE"

const val SPECMATIC_STUB_DICTIONARY = "SPECMATIC_STUB_DICTIONARY"

const val MISSING_CONFIG_FILE_MESSAGE = "Config file does not exist. (Could not find file ./specmatic.json OR ./specmatic.yaml OR ./specmatic.yml)"

class WorkingDirectory(private val filePath: File) {
    constructor(path: String = DEFAULT_WORKING_DIRECTORY): this(File(path))

    val path: String
        get() {
            return filePath.path
        }
}

fun invalidContractExtensionMessage(filename: String): String {
    return "The file $filename does not seem like a contract file. Valid extensions for contract files are ${CONTRACT_EXTENSIONS.joinToString(", ")}"
}

fun String.isContractFile(): Boolean {
    return File(this).extension in CONTRACT_EXTENSIONS
}

fun String.loadContract(): Feature {
    if(!this.isContractFile())
        throw ContractException(invalidContractExtensionMessage(this))

    return parseContractFileToFeature(File(this))
}

data class StubConfiguration(
    val generative: Boolean? = null,
    val delayInMilliseconds: Long? = getLongValue(SPECMATIC_STUB_DELAY),
    val dictionary: String? = getStringValue(SPECMATIC_STUB_DICTIONARY),
    val includeMandatoryAndRequestedKeysInResponse: Boolean? = null
)

data class VirtualServiceConfiguration(
    val nonPatchableKeys: Set<String> = emptySet()
)

data class WorkflowIDOperation(
    val extract: String? = null,
    val use: String? = null
)

data class WorkflowConfiguration(
    val ids: Map<String, WorkflowIDOperation> = emptyMap()
)

data class AttributeSelectionPattern(
    @field:JsonAlias("default_fields")
    val defaultFields: List<String> = readEnvVarOrProperty(
        ATTRIBUTE_SELECTION_DEFAULT_FIELDS,
        ATTRIBUTE_SELECTION_DEFAULT_FIELDS
    ).orEmpty().split(",").filter { it.isNotBlank() },
    @field:JsonAlias("query_param_key")
    val queryParamKey: String = readEnvVarOrProperty(
        ATTRIBUTE_SELECTION_QUERY_PARAM_KEY,
        ATTRIBUTE_SELECTION_QUERY_PARAM_KEY
    ).orEmpty()
)

data class SpecmaticConfig(
    val sources: List<Source> = emptyList(),
    private val auth: Auth? = null,
    private val pipeline: Pipeline? = null,
    val environments: Map<String, Environment>? = null,
    private val hooks: Map<String, String> = emptyMap(),
    private val repository: RepositoryInfo? = null,
    val report: ReportConfiguration? = null,
    val security: SecurityConfiguration? = null,
    val test: TestConfiguration? = TestConfiguration(),
    val stub: StubConfiguration = StubConfiguration(),
    val virtualService: VirtualServiceConfiguration = VirtualServiceConfiguration(),
    private val examples: List<String>? = null,
    val workflow: WorkflowConfiguration? = null,
    val ignoreInlineExamples: Boolean? = null,
    private val additionalExampleParamsFilePath: String? = null,
    val attributeSelectionPattern: AttributeSelectionPattern = AttributeSelectionPattern(),
    val allPatternsMandatory: Boolean? = null,
    private val defaultPatternValues: Map<String, Any> = emptyMap(),
    private val version: SpecmaticConfigVersion? = null
) {
    companion object {
        @JsonIgnore
        fun getRepository(specmaticConfig: SpecmaticConfig): RepositoryInfo? {
            return specmaticConfig.repository
        }

        @JsonIgnore
        fun getPipeline(specmaticConfig: SpecmaticConfig): Pipeline? {
            return specmaticConfig.pipeline
        }
    }

    @JsonIgnore
    fun attributeSelectionQueryParamKey(): String {
        return attributeSelectionPattern.queryParamKey
    }

    @JsonIgnore
    fun isExtensibleSchemaEnabled(): Boolean {
        return test?.allowExtensibleSchema ?: getBooleanValue(EXTENSIBLE_SCHEMA)
    }

    @JsonIgnore
    fun isResiliencyTestingEnabled(): Boolean {
        return (getResiliencyTestsEnable() != ResiliencyTestSuite.none)
    }

    @JsonIgnore
    fun isOnlyPositiveTestingEnabled(): Boolean {
        return (getResiliencyTestsEnable() == ResiliencyTestSuite.positiveOnly)
    }

    @JsonIgnore
    fun isResponseValueValidationEnabled(): Boolean {
        return test?.validateResponseValues ?: getBooleanValue(VALIDATE_RESPONSE_VALUE)
    }

    @JsonIgnore
    fun parsedDefaultPatternValues(): Map<String, Value> {
        return parsedJSONObject(ObjectMapper().writeValueAsString(defaultPatternValues)).jsonObject
    }

    @JsonIgnore
    fun getIncludeMandatoryAndRequestedKeysInResponse(): Boolean {
        return stub.includeMandatoryAndRequestedKeysInResponse ?: true
    }

    @JsonIgnore
    fun getResiliencyTestsEnable(): ResiliencyTestSuite {
        return test?.resiliencyTests?.enable ?: ResiliencyTestSuite.none
    }

    @JsonIgnore
    fun getStubGenerative(): Boolean {
        return stub.generative ?: false
    }

    @JsonIgnore
    fun getIgnoreInlineExamples(): Boolean {
        return ignoreInlineExamples ?: getBooleanValue(Flags.IGNORE_INLINE_EXAMPLES)
    }

    @JsonIgnore
    fun getAllPatternsMandatory(): Boolean {
        return allPatternsMandatory ?: getBooleanValue(Flags.ALL_PATTERNS_MANDATORY)
    }

    @JsonIgnore
    fun getAdditionalExampleParamsFilePath(): String? {
        return additionalExampleParamsFilePath ?: getStringValue(Flags.ADDITIONAL_EXAMPLE_PARAMS_FILE)
    }

    @JsonIgnore
    fun getHooks(): Map<String, String> {
        return hooks
    }

    @JsonIgnore
    fun getDefaultPatternValues(): Map<String, Any> {
        return defaultPatternValues
    }

    fun getVersion(): SpecmaticConfigVersion {
        return this.version ?: VERSION_1
    }

    @JsonIgnore
    fun getAuth(): Auth? {
        return auth
    }

    @JsonIgnore
    fun getAuthBearerFile(): String? {
        return auth?.getBearerFile()
    }

    @JsonIgnore
    fun getAuthBearerEnvironmentVariable(): String? {
        return auth?.getBearerEnvironmentVariable()
    }

    @JsonIgnore
    fun getExamples(): List<String> {
        return examples ?: getStringValue(EXAMPLE_DIRECTORIES)?.split(",") ?: emptyList()
    }

    @JsonIgnore
    fun getRepositoryProvider(): String? {
        return repository?.getProvider()
    }

    @JsonIgnore
    fun getRepositoryCollectionName(): String? {
        return repository?.getCollectionName()
    }

    @JsonIgnore
    fun getPipelineProvider(): PipelineProvider? {
        return pipeline?.getProvider()
    }

    @JsonIgnore
    fun getPipelineDefinitionId(): Int? {
        return pipeline?.getDefinitionId()
    }

    @JsonIgnore
    fun getPipelineOrganization(): String? {
        return pipeline?.getOrganization()
    }

    @JsonIgnore
    fun getPipelineProject(): String? {
        return pipeline?.getProject()
    }
}

data class TestConfiguration(
    val resiliencyTests: ResiliencyTestsConfig? = ResiliencyTestsConfig(
        isResiliencyTestFlagEnabled = getBooleanValue(SPECMATIC_GENERATIVE_TESTS),
        isOnlyPositiveFlagEnabled = getBooleanValue(ONLY_POSITIVE)
    ),
    val validateResponseValues: Boolean? = null,
    val allowExtensibleSchema: Boolean? = null,
    val timeoutInMilliseconds: Long? = getLongValue(SPECMATIC_TEST_TIMEOUT)
)

enum class ResiliencyTestSuite {
    all, positiveOnly, none
}

data class ResiliencyTestsConfig(
    val enable: ResiliencyTestSuite? = null
) {
    constructor(isResiliencyTestFlagEnabled: Boolean, isOnlyPositiveFlagEnabled: Boolean) : this(
        enable = getEnableFrom(isResiliencyTestFlagEnabled, isOnlyPositiveFlagEnabled)
    )

    companion object {
        private fun getEnableFrom(
            isResiliencyTestFlagEnabled: Boolean,
            isOnlyPositiveFlagEnabled: Boolean
        ): ResiliencyTestSuite? {
            return when {
                isResiliencyTestFlagEnabled -> ResiliencyTestSuite.all
                isOnlyPositiveFlagEnabled -> ResiliencyTestSuite.positiveOnly
                else -> null
            }
        }
    }
}

data class Auth(
    @JsonProperty("bearer-file") private val bearerFile: String = "bearer.txt",
    @JsonProperty("bearer-environment-variable") private val bearerEnvironmentVariable: String? = null
) {
    fun getBearerFile(): String {
        return bearerFile
    }

    fun getBearerEnvironmentVariable(): String? {
        return bearerEnvironmentVariable
    }
}

enum class PipelineProvider { azure }

data class Pipeline(
    private val provider: PipelineProvider = PipelineProvider.azure,
    private val organization: String = "",
    private val project: String = "",
    private val definitionId: Int = 0
) {
    fun getProvider(): PipelineProvider {
        return provider
    }

    fun getOrganization(): String {
        return organization
    }

    fun getProject(): String {
        return project
    }

    fun getDefinitionId(): Int {
        return definitionId
    }
}

data class Environment(
    val baseurls: Map<String, String>? = null,
    val variables: Map<String, String>? = null
)

enum class SourceProvider { git, filesystem, web }

data class Source(
    @field:JsonAlias("type")
    val provider: SourceProvider = SourceProvider.filesystem,
    val repository: String? = null,
    val branch: String? = null,
    @field:JsonAlias("provides")
    val test: List<String>? = null,
    @field:JsonAlias("consumes")
    val stub: List<String>? = null,
    val directory: String? = null,
)

data class RepositoryInfo(
    private val provider: String,
    private val collectionName: String
) {
    fun getProvider(): String {
        return provider
    }

    fun getCollectionName(): String {
        return collectionName
    }
}

data class ReportConfiguration(
    val formatters: List<ReportFormatter>? = null,
    val types: ReportTypes = ReportTypes()
)

data class ReportFormatter(
    var type: ReportFormatterType = ReportFormatterType.TEXT,
    val layout: ReportFormatterLayout = ReportFormatterLayout.TABLE,
    val lite: Boolean = false,
    val title: String = "Specmatic Report",
    val logo: String = "assets/specmatic-logo.svg",
    val logoAltText: String = "Specmatic",
    val heading: String = "Contract Test Results",
    val outputDirectory: String = "./build/reports/specmatic/html"
)

enum class ReportFormatterType {
    @JsonProperty("text")
    TEXT,

    @JsonProperty("html")
    HTML
}

enum class ReportFormatterLayout {
    @JsonProperty("table")
    TABLE
}

data class ReportTypes (
    @JsonProperty("APICoverage")
    val apiCoverage: APICoverage = APICoverage()
)

data class APICoverage (
    @JsonProperty("OpenAPI")
    val openAPI: APICoverageConfiguration = APICoverageConfiguration()
)

data class APICoverageConfiguration(
    val successCriteria: SuccessCriteria = SuccessCriteria(),
    val excludedEndpoints: List<String> = emptyList()
)

data class SuccessCriteria(
    val minThresholdPercentage: Int = 0,
    val maxMissedEndpointsInSpec: Int = 0,
    val enforce: Boolean = false
)

data class SecurityConfiguration(
    @JsonProperty("OpenAPI")
    val OpenAPI:OpenAPISecurityConfiguration?
)

data class OpenAPISecurityConfiguration(
    val securitySchemes: Map<String, SecuritySchemeConfiguration> = emptyMap()
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = OAuth2SecuritySchemeConfiguration::class, name = "oauth2"),
    JsonSubTypes.Type(value = BasicAuthSecuritySchemeConfiguration::class, name = "basicAuth"),
    JsonSubTypes.Type(value = BearerSecuritySchemeConfiguration::class, name = "bearer"),
    JsonSubTypes.Type(value = APIKeySecuritySchemeConfiguration::class, name = "apiKey")
)
sealed class SecuritySchemeConfiguration {
    abstract val type: String
}

interface SecuritySchemeWithOAuthToken {
    val token: String
}

@JsonTypeName("oauth2")
data class OAuth2SecuritySchemeConfiguration(
    override val type: String = "oauth2",
    override val token: String = ""
) : SecuritySchemeConfiguration(), SecuritySchemeWithOAuthToken

@JsonTypeName("basicAuth")
data class BasicAuthSecuritySchemeConfiguration(
    override val type: String = "basicAuth",
    val token: String = ""
) : SecuritySchemeConfiguration()

@JsonTypeName("bearer")
data class BearerSecuritySchemeConfiguration(
    override val type: String = "bearer",
    override val token: String = ""
) : SecuritySchemeConfiguration(), SecuritySchemeWithOAuthToken

@JsonTypeName("apiKey")
data class APIKeySecuritySchemeConfiguration(
    override val type: String = "apiKey",
    val value: String = ""
) : SecuritySchemeConfiguration()

fun loadSpecmaticConfigOrDefault(configFileName: String? = null): SpecmaticConfig {
    return if(configFileName == null)
        SpecmaticConfig()
    else try {
        loadSpecmaticConfig(configFileName)
    }
    catch (e: ContractException) {
        logger.log(exceptionCauseMessage(e))
        SpecmaticConfig()
    }
}

fun loadSpecmaticConfig(configFileName: String? = null): SpecmaticConfig {
    val configFile = File(configFileName ?: configFilePath)
    if (!configFile.exists()) {
        throw ContractException("Could not find the Specmatic configuration at path ${configFile.canonicalPath}")
    }
    try {
        return configFile.toSpecmaticConfig()
    } catch(e: LinkageError) {
        logger.log(e, "A dependency version conflict has been detected. If you are using Spring in a maven project, a common resolution is to set the property <kotlin.version></kotlin.version> to your pom project.")
        throw e
    } catch (e: Throwable) {
        logger.log(e, "Your configuration file may have some missing configuration sections. Please ensure that the ${configFile.path} file adheres to the schema described at: https://specmatic.io/documentation/specmatic_json.html")
        throw Exception("Your configuration file may have some missing configuration sections. Please ensure that the ${configFile.path} file adheres to the schema described at: https://specmatic.io/documentation/specmatic_json.html", e)
    }
}