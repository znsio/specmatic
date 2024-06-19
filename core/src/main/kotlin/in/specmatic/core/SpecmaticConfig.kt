package `in`.specmatic.core

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import `in`.specmatic.core.Configuration.Companion.globalConfigFileName
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import java.io.File

const val APPLICATION_NAME = "Specmatic"
const val APPLICATION_NAME_LOWER_CASE = "specmatic"
const val APPLICATION_NAME_LOWER_CASE_LEGACY = "qontract"
const val DEFAULT_TIMEOUT_IN_SECONDS = "60"
const val CONTRACT_EXTENSION = "spec"
const val LEGACY_CONTRACT_EXTENSION = "qontract"
const val YAML = "yaml"
const val WSDL = "wsdl"
const val YML = "yml"
const val JSON = "json"
val OPENAPI_FILE_EXTENSIONS = listOf(YAML, YML, JSON)
val CONTRACT_EXTENSIONS = listOf(CONTRACT_EXTENSION, LEGACY_CONTRACT_EXTENSION, WSDL) + OPENAPI_FILE_EXTENSIONS
const val DATA_DIR_SUFFIX = "_data"
const val SPECMATIC_GITHUB_ISSUES = "https://github.com/znsio/specmatic/issues"
const val   DEFAULT_WORKING_DIRECTORY = ".$APPLICATION_NAME_LOWER_CASE"

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
    val test: List<String>? = null,
    val stub: List<String>? = null,
    val directory: String? = null,
)

data class SpecmaticConfig(
    val sources: List<Source> = emptyList(),
    val auth: Auth? = null,
    val pipeline: Pipeline? = null,
    val environments: Map<String, Environment>? = null,
    val hooks: Map<String, String> = emptyMap(),
    val repository: RepositoryInfo? = null,
    val report: ReportConfiguration? = null,
    val security: SecurityConfiguration? = null
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
        throw Exception("Your configuration file may have some missing configuration sections. Please ensure that the $configFileName file adheres to the schema described at: https://specmatic.in/documentation/specmatic_json.html", e)
    }
}