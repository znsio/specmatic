package io.specmatic.core

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.specmatic.core.Configuration.Companion.globalConfigFileName
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.utilities.Flags.Companion.EXAMPLE_DIRECTORIES
import io.specmatic.core.utilities.Flags.Companion.EXTENSIBLE_SCHEMA
import io.specmatic.core.utilities.Flags.Companion.ONLY_POSITIVE
import io.specmatic.core.utilities.Flags.Companion.SPECMATIC_GENERATIVE_TESTS
import io.specmatic.core.utilities.Flags.Companion.VALIDATE_RESPONSE_VALUE
import io.specmatic.core.utilities.Flags.Companion.getBooleanValue
import io.specmatic.core.utilities.Flags.Companion.getStringValue
import java.io.File

const val APPLICATION_NAME = "Specmatic"
const val APPLICATION_NAME_LOWER_CASE = "specmatic"
const val DEFAULT_TIMEOUT_IN_SECONDS = "60"
const val CONTRACT_EXTENSION = "spec"
const val YAML = "yaml"
const val WSDL = "wsdl"
const val YML = "yml"
const val JSON = "json"
val OPENAPI_FILE_EXTENSIONS = listOf(YAML, YML, JSON)
val CONTRACT_EXTENSIONS = listOf(CONTRACT_EXTENSION, WSDL) + OPENAPI_FILE_EXTENSIONS
const val DATA_DIR_SUFFIX = "_data"
const val TEST_DIR_SUFFIX = "_tests"
const val EXAMPLES_DIR_SUFFIX = "_examples"
const val SPECMATIC_GITHUB_ISSUES = "https://github.com/znsio/specmatic/issues"
const val DEFAULT_WORKING_DIRECTORY = ".$APPLICATION_NAME_LOWER_CASE"

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
    val generative: Boolean = false
)

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
    val examples: List<String> = getStringValue(EXAMPLE_DIRECTORIES)?.split(",") ?: emptyList()
) {
    fun isExtensibleSchemaEnabled(): Boolean {
        return (test?.allowExtensibleSchema == true)
    }

    fun isResiliencyTestingEnabled(): Boolean {
        return (test?.resiliencyTests?.enable != ResiliencyTestSuite.none)
    }

    fun isOnlyPositiveTestingEnabled(): Boolean {
        return (test?.resiliencyTests?.enable == ResiliencyTestSuite.positiveOnly)
    }

    fun isResponseValueValidationEnabled(): Boolean {
        return (test?.validateResponseValues == true)
    }
}

data class TestConfiguration(
    val resiliencyTests: ResiliencyTestsConfig? = ResiliencyTestsConfig(
        isResiliencyTestFlagEnabled = getBooleanValue(SPECMATIC_GENERATIVE_TESTS),
        isOnlyPositiveFlagEnabled = getBooleanValue(ONLY_POSITIVE)
    ),
    val validateResponseValues: Boolean? = getBooleanValue(VALIDATE_RESPONSE_VALUE),
    val allowExtensibleSchema: Boolean? = getBooleanValue(EXTENSIBLE_SCHEMA),
)

enum class ResiliencyTestSuite {
    all, positiveOnly, none
}

data class ResiliencyTestsConfig(
    val enable: ResiliencyTestSuite = ResiliencyTestSuite.none
) {
    constructor(isResiliencyTestFlagEnabled: Boolean, isOnlyPositiveFlagEnabled: Boolean) : this(
        enable = getEnableFrom(isResiliencyTestFlagEnabled, isOnlyPositiveFlagEnabled)
    )

    companion object {
        private fun getEnableFrom(isResiliencyTestFlagEnabled: Boolean, isOnlyPositiveFlagEnabled: Boolean): ResiliencyTestSuite {
            return when {
                isResiliencyTestFlagEnabled -> ResiliencyTestSuite.all
                isOnlyPositiveFlagEnabled -> ResiliencyTestSuite.positiveOnly
                else -> ResiliencyTestSuite.none
            }
        }
    }
}

data class Auth(
    @JsonProperty("bearer-file") val bearerFile: String = "bearer.txt",
    @JsonProperty("bearer-environment-variable") val bearerEnvironmentVariable: String? = null
)

enum class PipelineProvider { azure }

data class Pipeline(
    val provider: PipelineProvider = PipelineProvider.azure,
    val organization: String = "",
    val project: String = "",
    val definitionId: Int = 0
)

data class Environment(
    val baseurls: Map<String, String>? = null,
    val variables: Map<String, String>? = null
)

enum class SourceProvider { git, filesystem, web }

data class Source(
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
    val provider: String,
    val collectionName: String
)

data class ReportConfiguration(
    val formatters: List<ReportFormatter>? = null,
    val types: ReportTypes = ReportTypes()
)

data class ReportFormatter(
    val type: ReportFormatterType = ReportFormatterType.TEXT,
    val layout: ReportFormatterLayout = ReportFormatterLayout.TABLE
)

enum class ReportFormatterType {
    @JsonProperty("text")
    TEXT
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

fun loadSpecmaticConfig(configFileName: String? = null): SpecmaticConfig {
    val configFile = File(configFileName ?: globalConfigFileName)
    if (!configFile.exists()) {
        throw ContractException("Could not find the Specmatic configuration at path ${configFile.canonicalPath}")
    }
    try {
        return ObjectMapper(YAMLFactory()).readValue(configFile.readText(), SpecmaticConfig::class.java)
    } catch(e: LinkageError) {
        logger.log(e, "A dependency version conflict has been detected. If you are using Spring in a maven project, a common resolution is to set the property <kotlin.version></kotlin.version> to your pom project.")
        throw e
    } catch (e: Throwable) {
        throw Exception("Your configuration file may have some missing configuration sections. Please ensure that the $configFileName file adheres to the schema described at: https://specmatic.io/documentation/specmatic_json.html", e)
    }
}