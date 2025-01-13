package io.specmatic.core.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.specmatic.core.Configuration
import io.specmatic.core.Feature
import io.specmatic.core.config.v1.SpecmaticConfigV1
import io.specmatic.core.config.v2.ContractConfig
import io.specmatic.core.config.v2.FileSystemConfig
import io.specmatic.core.config.v2.GitConfig
import io.specmatic.core.config.v2.SpecmaticConfigV2
import io.specmatic.core.log.logger
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.utilities.Flags
import io.specmatic.core.utilities.Flags.Companion.EXAMPLE_DIRECTORIES
import io.specmatic.core.utilities.Flags.Companion.getBooleanValue
import io.specmatic.core.utilities.Flags.Companion.getStringValue
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.core.value.Value
import java.io.File
import java.io.IOException

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
const val DICTIONARY_FILE_SUFFIX = "_dictionary.json"
const val SPECMATIC_GITHUB_ISSUES = "https://github.com/znsio/specmatic/issues"
const val DEFAULT_WORKING_DIRECTORY = ".$APPLICATION_NAME_LOWER_CASE"

const val SPECMATIC_STUB_DICTIONARY = "SPECMATIC_STUB_DICTIONARY"

const val MISSING_CONFIG_FILE_MESSAGE =
    "Config file does not exist. (Could not find file ./specmatic.json OR ./specmatic.yaml OR ./specmatic.yml)"

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

fun invalidContractExtensionMessage(filename: String): String {
    return "The file $filename does not seem like a contract file. Valid extensions for contract files are ${
        CONTRACT_EXTENSIONS.joinToString(
            ", "
        )
    }"
}

fun String.isContractFile(): Boolean {
    return File(this).extension in CONTRACT_EXTENSIONS
}

fun String.loadContract(): Feature {
    if (!this.isContractFile())
        throw ContractException(invalidContractExtensionMessage(this))

    return parseContractFileToFeature(File(this))
}

fun loadSpecmaticConfigOrDefault(configFileName: String? = null): SpecmaticConfig {
    return if (configFileName == null)
        SpecmaticConfig()
    else try {
        loadSpecmaticConfig(configFileName)
    } catch (e: ContractException) {
        logger.log(exceptionCauseMessage(e))
        SpecmaticConfig()
    }
}

fun loadSpecmaticConfig(configFileName: String? = null): SpecmaticConfig {
    val configFile = File(configFileName ?: Configuration.configFilePath)
    if (!configFile.exists()) {
        throw ContractException("Could not find the Specmatic configuration at path ${configFile.canonicalPath}")
    }
    try {
        return SpecmaticConfigFactory().create(configFile)
    } catch (e: LinkageError) {
        logger.log(
            e,
            "A dependency version conflict has been detected. If you are using Spring in a maven project, a common resolution is to set the property <kotlin.version></kotlin.version> to your pom project."
        )
        throw e
    } catch (e: JsonParseException) {
        val msg = "Invalid configuration file format: " + e.message
        logger.log(e, msg)
        throw IllegalArgumentException(msg, e)
    } catch (e: JsonMappingException) {
        val msg = "Your configuration file does not match the expected structure: " + e.message
        logger.log(e, msg)
        throw IllegalArgumentException(msg, e)
    } catch (e: IOException) {
        val msg = "Error reading configuration file: " + e.message
        logger.log(e, msg)
        throw RuntimeException(msg, e)
    } catch (e: Throwable) {
        logger.log(
            e,
            "Your configuration file may have some missing configuration sections. Please ensure that the ${configFile.path} file adheres to the schema described at: https://specmatic.io/documentation/specmatic_json.html"
        )
        throw Exception(
            "Your configuration file may have some missing configuration sections. Please ensure that the ${configFile.path} file adheres to the schema described at: https://specmatic.io/documentation/specmatic_json.html",
            e
        )
    }
}