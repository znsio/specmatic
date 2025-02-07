package io.specmatic.core.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.specmatic.core.ResiliencyTestSuite
import io.specmatic.core.Source
import io.specmatic.core.SourceProvider.filesystem
import io.specmatic.core.SourceProvider.git
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.config.v1.SpecmaticConfigV1
import io.specmatic.core.config.v2.ContractConfig
import io.specmatic.core.config.v2.ContractConfig.FileSystemContractSource
import io.specmatic.core.config.v2.ContractConfig.GitContractSource
import io.specmatic.core.config.v2.SpecmaticConfigV2
import io.specmatic.core.config.v3.Consumes
import io.specmatic.core.config.v3.ContractConfigV2
import io.specmatic.core.config.v3.SpecmaticConfigV3
import io.specmatic.core.loadSpecmaticConfig
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.parsedJSON
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File


internal class SpecmaticConfigAllTest {
    private val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    @Test
    fun `should return the version from the specmatic config`() {
        val config = """
           {"version": 2}
        """.trimIndent()

        assertThat(config.getVersion()).isEqualTo(SpecmaticConfigVersion.VERSION_2)
    }

    @Test
    fun `should return the version as 1 if it is not present`() {
        assertThat("{}".getVersion()).isEqualTo(SpecmaticConfigVersion.VERSION_1)
    }

    @Test
    fun `should return the version as 1 if it is null in the config`() {
        val config = """
               {"version": null}
            """.trimIndent()

        assertThat(config.getVersion()).isEqualTo(SpecmaticConfigVersion.VERSION_1)
    }

    @Test
    fun `should do nothing for invalid versions`() {
        val config = """
           {"version": 666}
        """.trimIndent()

        assertThat(config.getVersion()).isNull()
    }

    @CsvSource(
        "VERSION_1, ./src/test/resources/specmaticConfigFiles/specmatic_without_version.yaml",
        "VERSION_1, ./src/test/resources/specmaticConfigFiles/specmatic_without_version.json",
        "VERSION_1, ./src/test/resources/specmaticConfigFiles/v1/specmatic_config_v1.yaml",
        "VERSION_1, ./src/test/resources/specmaticConfigFiles/v1/specmatic_config_v1.json",
        "VERSION_2, ./src/test/resources/specmaticConfigFiles/v2/specmatic_config_v2.yaml",
        "VERSION_2, ./src/test/resources/specmaticConfigFiles/v2/specmatic_config_v2.json",
        "VERSION_3, ./src/test/resources/specmaticConfigFiles/v3/specmatic_config_v3.yaml",
        "VERSION_3, ./src/test/resources/specmaticConfigFiles/v3/specmatic_config_v3.json"
    )
    @ParameterizedTest
    fun `should create SpecmaticConfig from the versioned specmatic configuration`(version: SpecmaticConfigVersion, configFile: String) {
        val config: SpecmaticConfig = loadSpecmaticConfig(configFile)
        assertThat(config.getVersion()).isEqualTo(version)
        val sources = SpecmaticConfig.getSources(config)
        assertThat(sources.size).isEqualTo(2)
        val expectedSources = if(version != SpecmaticConfigVersion.VERSION_3) listOf(
            Source(
                provider = git,
                repository = "https://contracts",
                branch = "1.0.1",
                test = listOf("com/petstore/1.yaml"),
                stub = listOf(Consumes.StringValue("com/petstore/payment.yaml"))
            ),
            Source(
                provider = filesystem,
                test = listOf("com/petstore/1.yaml"),
                stub = listOf(
                    Consumes.StringValue("com/petstore/payment.yaml"),
                    Consumes.StringValue("com/petstore/order.yaml")
                ),
                directory = "contracts"
            )
        ) else listOf(
            Source(
                provider = git,
                repository = "https://contracts",
                branch = "1.0.1",
                test = listOf("com/petstore/1.yaml"),
                stub = listOf(Consumes.StringValue("com/petstore/payment.yaml"))
            ),
            Source(
                provider = filesystem,
                test = listOf("com/petstore/1.yaml"),
                stub = listOf(
                    Consumes.StringValue("com/petstore/payment.yaml"),
                    Consumes.ObjectValue(
                        specs = listOf("com/petstore/order1.yaml", "com/petstore/order2.yaml"),
                        port = 9001
                    )
                ),
                directory = "contracts"
            )
        )
        assertThat(sources).containsAll(expectedSources)
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_config_with_unsupported_version.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic_config_with_unsupported_version.json"
    )
    @ParameterizedTest
    fun `should throw error when unsupported version is provided`(configFile: String) {
        val specmaticConfigWithInvalidVersion = File(configFile)
        val exception = assertThrows<ContractException> { specmaticConfigWithInvalidVersion.toSpecmaticConfig() }
        assertThat(exception.message).isEqualTo("Unsupported Specmatic config version")
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/v2/specmatic_config_v2.yaml",
        "./src/test/resources/specmaticConfigFiles/v2/specmatic_config_v2.json"
    )
    @ParameterizedTest
    fun `should deserialize ContractConfig successfully`(configFile: String) {
        val specmaticConfigV2 = objectMapper.readValue(File(configFile).readText(), SpecmaticConfigV2::class.java)

        val contracts = specmaticConfigV2.contracts

        assertThat(contracts.size).isEqualTo(2)
        assertThat(contracts[0].contractSource).isInstanceOf(GitContractSource::class.java)
        val gitContractSource = contracts[0].contractSource as GitContractSource
        assertThat(gitContractSource.url).isEqualTo("https://contracts")
        assertThat(gitContractSource.branch).isEqualTo("1.0.1")
        assertThat(contracts[0].provides).containsOnly("com/petstore/1.yaml")
        assertThat(contracts[0].consumes).containsOnly("com/petstore/payment.yaml")

        assertThat(contracts[1].contractSource).isInstanceOf(FileSystemContractSource::class.java)
        val fileSystemContractSource = contracts[1].contractSource as FileSystemContractSource
        assertThat(fileSystemContractSource.directory).isEqualTo("contracts")
        assertThat(contracts[1].provides).containsOnly("com/petstore/1.yaml")
        assertThat(contracts[1].consumes).containsOnly("com/petstore/payment.yaml", "com/petstore/order.yaml")
    }

    @Test
    fun `should serialize ContractConfig successfully`() {
        val expectedContractsJson = """[
            {
                "git": {
                    "url": "https://contracts",
                    "branch": "1.0.1"
                },
                "provides": ["com/petstore/1.yaml"],
                "consumes": ["com/petstore/payment.yaml"]
            },
            {
                "filesystem": {
                    "directory": "contracts"
                },
                "provides": ["com/petstore/1.yaml"],
                "consumes": ["com/petstore/payment.yaml", "com/petstore/order.yaml"]
            }
        ]"""

        val contracts = listOf(
            ContractConfig(
                contractSource = GitContractSource(url = "https://contracts", branch = "1.0.1"),
                provides = listOf("com/petstore/1.yaml"),
                consumes = listOf("com/petstore/payment.yaml")
            ),
            ContractConfig(
                contractSource = FileSystemContractSource(directory = "contracts"),
                provides = listOf("com/petstore/1.yaml"),
                consumes = listOf("com/petstore/payment.yaml", "com/petstore/order.yaml")
            )
        )

        val objectMapper =
            ObjectMapper().registerKotlinModule().setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        val contractsJson = objectMapper.writeValueAsString(contracts)

        assertThat(parsedJSON(contractsJson)).isEqualTo(parsedJSON(expectedContractsJson))
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/v3/specmatic_config_v3.yaml",
        "./src/test/resources/specmaticConfigFiles/v3/specmatic_config_v3.json"
    )
    @ParameterizedTest
    fun `should deserialize ContractConfigV2 successfully`(configFile: String) {
        val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val specmaticConfigV3 = objectMapper.readValue(File(configFile).readText(), SpecmaticConfigV3::class.java)

        val contracts = specmaticConfigV3.contracts

        assertThat(contracts.size).isEqualTo(2)
        assertThat(contracts[0].contractSource).isInstanceOf(ContractConfigV2.GitContractSourceV2::class.java)
        val gitContractSource = contracts[0].contractSource as ContractConfigV2.GitContractSourceV2
        assertThat(gitContractSource.url).isEqualTo("https://contracts")
        assertThat(gitContractSource.branch).isEqualTo("1.0.1")
        assertThat(contracts[0].provides).containsOnly("com/petstore/1.yaml")
        assertThat(contracts[0].consumes).containsOnly(Consumes.StringValue("com/petstore/payment.yaml"))

        assertThat(contracts[1].contractSource).isInstanceOf(ContractConfigV2.FileSystemContractSourceV2::class.java)
        val fileSystemContractSource = contracts[1].contractSource as ContractConfigV2.FileSystemContractSourceV2
        assertThat(fileSystemContractSource.directory).isEqualTo("contracts")
        assertThat(contracts[1].provides).containsOnly("com/petstore/1.yaml")
        assertThat(contracts[1].consumes).containsOnly(
            Consumes.StringValue("com/petstore/payment.yaml"),
            Consumes.ObjectValue(
                specs = listOf("com/petstore/order1.yaml", "com/petstore/order2.yaml"),
                port = 9001
            )
        )
    }

    @Test
    fun `should serialize ContractConfigV2 successfully`() {
        val expectedContractsJson = """[
            {
                "git": {
                    "url": "https://contracts",
                    "branch": "1.0.1"
                },
                "provides": ["com/petstore/1.yaml"],
                "consumes": ["com/petstore/payment.yaml"]
            },
            {
                "filesystem": {
                    "directory": "contracts"
                },
                "provides": ["com/petstore/1.yaml"],
                "consumes": [
                    "com/petstore/payment.yaml",
                    {
                        "specs": ["com/petstore/order.yaml"],
                        "port": 9001
                    }
                ]
            }
        ]"""

        val contracts = listOf(
            ContractConfigV2(
                contractSource = ContractConfigV2.GitContractSourceV2(url = "https://contracts", branch = "1.0.1"),
                provides = listOf("com/petstore/1.yaml"),
                consumes = listOf(
                    Consumes.StringValue("com/petstore/payment.yaml")
                )
            ),
            ContractConfigV2(
                contractSource = ContractConfigV2.FileSystemContractSourceV2(directory = "contracts"),
                provides = listOf("com/petstore/1.yaml"),
                consumes = listOf(
                    Consumes.StringValue("com/petstore/payment.yaml"),
                    Consumes.ObjectValue(
                        specs = listOf("com/petstore/order.yaml"),
                        port = 9001
                    )
                )
            )
        )

        val objectMapper =
            ObjectMapper().registerKotlinModule().setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        val contractsJson = objectMapper.writeValueAsString(contracts)

        assertThat(parsedJSON(contractsJson)).isEqualTo(parsedJSON(expectedContractsJson))
    }

    @Test
    fun `should deserialize ContractConfig successfully when branch field is absent`() {
        val contractConfigYaml = """
            git:
              url: https://contracts
            provides:
              - com/petstore/1.yaml
            consumes:
              - com/petstore/payment.yaml
        """.trimIndent()

        val contractConfig = objectMapper.readValue(contractConfigYaml, ContractConfig::class.java)

        assertThat(contractConfig.contractSource).isInstanceOf(GitContractSource::class.java)
        assertThat((contractConfig.contractSource as GitContractSource).url).isEqualTo("https://contracts")
        assertThat(contractConfig.provides).containsOnly("com/petstore/1.yaml")
        assertThat(contractConfig.consumes).containsOnly("com/petstore/payment.yaml")
    }

    @Test
    fun `should deserialize ContractConfig successfully when provides is absent`() {
        val contractConfigYaml = """
            git:
              url: https://contracts
            consumes:
              - com/petstore/payment.yaml
        """.trimIndent()

        val contractConfig = objectMapper.readValue(contractConfigYaml, ContractConfig::class.java)

        assertThat(contractConfig.contractSource).isInstanceOf(GitContractSource::class.java)
        assertThat((contractConfig.contractSource as GitContractSource).url).isEqualTo("https://contracts")
        assertThat(contractConfig.provides).isNull()
        assertThat(contractConfig.consumes).containsOnly("com/petstore/payment.yaml")
    }

    @Test
    fun `should deserialize ContractConfig successfully when consumes is absent`() {
        val contractConfigYaml = """
            git:
              url: https://contracts
            provides:
              - com/petstore/1.yaml
        """.trimIndent()

        val contractConfig = objectMapper.readValue(contractConfigYaml, ContractConfig::class.java)

        assertThat(contractConfig.contractSource).isInstanceOf(GitContractSource::class.java)
        assertThat((contractConfig.contractSource as GitContractSource).url).isEqualTo("https://contracts")
        assertThat(contractConfig.provides).containsOnly("com/petstore/1.yaml")
        assertThat(contractConfig.consumes).isNull()
    }

    @Test
    fun `should deserialize SpecmaticConfig successfully when VirtualService key is present`(@TempDir tempDir: File) {
        val configFile = tempDir.resolve("specmatic.yaml")
        val configYaml = """
            virtualService:
                nonPatchableKeys:
                    - description
                    - url
        """.trimIndent()
        configFile.writeText(configYaml)

        val specmaticConfig = configFile.toSpecmaticConfig()

        val nonPatchableKeys = specmaticConfig.getVirtualServiceNonPatchableKeys()
        assertThat(nonPatchableKeys.size).isEqualTo(2)
        assertThat(nonPatchableKeys).containsExactly("description", "url")
    }

    @Test
    fun `should convert config with VirtualService from v1 to v2`() {
        val configYaml = """
            virtualService:
                nonPatchableKeys:
                    - description
                    - url
        """.trimIndent()

        val config = objectMapper.readValue(configYaml, SpecmaticConfigV1::class.java).transform()
        val configV2 = SpecmaticConfigV2.loadFrom(config) as SpecmaticConfigV2

        assertThat(configV2.virtualService.getNonPatchableKeys()).containsExactly("description", "url")
    }

    @Test
    fun `should deserialize SpecmaticConfig successfully when AttributeSelectionPattern key is present`(@TempDir tempDir: File) {
        val configFile = tempDir.resolve("specmatic.yaml")
        val configYaml = """
            attributeSelectionPattern:
                default_fields:
                    - description
                    - url
                query_param_key: web
        """.trimIndent()
        configFile.writeText(configYaml)

        val specmaticConfig = configFile.toSpecmaticConfig()

        assertThat(specmaticConfig.getAttributeSelectionPattern().getDefaultFields()).containsExactly(
            "description",
            "url"
        )
        assertThat(specmaticConfig.getAttributeSelectionPattern().getQueryParamKey()).isEqualTo("web")
    }

    @Test
    fun `should convert config with AttributeSelectionPattern from v1 to v2`() {
        val configYaml = """
            attributeSelectionPattern:
                default_fields:
                    - description
                    - url
                query_param_key: web
        """.trimIndent()

        val config = objectMapper.readValue(configYaml, SpecmaticConfigV1::class.java).transform()
        val configV2 = SpecmaticConfigV2.loadFrom(config) as SpecmaticConfigV2

        assertThat(configV2.attributeSelectionPattern.getDefaultFields()).containsExactly("description", "url")
        assertThat(configV2.attributeSelectionPattern.getQueryParamKey()).isEqualTo("web")
    }

    @Test
    fun `should deserialize SpecmaticConfig successfully when AllPatternsMandatory is present`(@TempDir tempDir: File) {
        val configFile = tempDir.resolve("specmatic.yaml")
        val configYaml = """
            allPatternsMandatory: true
        """.trimIndent()
        configFile.writeText(configYaml)

        val specmaticConfig = configFile.toSpecmaticConfig()

        assertThat(specmaticConfig.getAllPatternsMandatory()).isEqualTo(true)
    }

    @Test
    fun `should convert config from v1 to v2 when AllPatternsMandatory key is present`() {
        val configYaml = """
            allPatternsMandatory: true
        """.trimIndent()

        val config = objectMapper.readValue(configYaml, SpecmaticConfigV1::class.java).transform()
        val configV2 = SpecmaticConfigV2.loadFrom(config) as SpecmaticConfigV2

        assertThat(configV2.allPatternsMandatory).isTrue()
    }

    @Test
    fun `should deserialize SpecmaticConfig successfully when IgnoreInlineExamples key is present`(@TempDir tempDir: File) {
        val configFile = tempDir.resolve("specmatic.yaml")
        val configYaml = """
            ignoreInlineExamples: true
        """.trimIndent()
        configFile.writeText(configYaml)

        val specmaticConfig = configFile.toSpecmaticConfig()

        assertThat(specmaticConfig.getIgnoreInlineExamples()).isTrue()
    }

    @Test
    fun `should convert config from v1 to v2 when IgnoreInlineExamples key is present`() {
        val configYaml = """
            ignoreInlineExamples: true
        """.trimIndent()

        val config = objectMapper.readValue(configYaml, SpecmaticConfigV1::class.java).transform()
        val configV2 = SpecmaticConfigV2.loadFrom(config) as SpecmaticConfigV2

        assertThat(configV2.ignoreInlineExamples).isTrue()
    }

    @Test
    fun `should deserialize test configuration in SpecmaticConfig successfully`(@TempDir tempDir: File) {
        val configFile = tempDir.resolve("specmatic.yaml")
        val configYaml = """
            test:
                resiliencyTests:
                    enable: all
                validateResponseValues: true
                allowExtensibleSchema: true
                timeoutInMilliseconds: 10
        """.trimIndent()
        configFile.writeText(configYaml)

        val specmaticConfig = configFile.toSpecmaticConfig()

        specmaticConfig.apply {
            assertThat(isResiliencyTestingEnabled()).isTrue()
            assertThat(isResponseValueValidationEnabled()).isTrue()
            assertThat(isExtensibleSchemaEnabled()).isTrue()
            assertThat(getTestTimeoutInMilliseconds()).isEqualTo(10)
        }
    }

    @Test
    fun `should convert config from v1 to v2 when test configuration is present`() {
        val configYaml = """
            test:
                resiliencyTests:
                    enable: all
                validateResponseValues: true
                allowExtensibleSchema: true
                timeoutInMilliseconds: 10
        """.trimIndent()

        val configFromV1 = objectMapper.readValue(configYaml, SpecmaticConfigV1::class.java).transform()
        val configV2 = SpecmaticConfigV2.loadFrom(configFromV1) as SpecmaticConfigV2

        configV2.test!!.apply {
            assertThat(getResiliencyTests().getEnableTestSuite()).isEqualTo(ResiliencyTestSuite.all)
            assertThat(getValidateResponseValues()).isTrue()
            assertThat(getAllowExtensibleSchema()).isTrue()
            assertThat(getTimeoutInMilliseconds()).isEqualTo(10)
        }
    }

    @Test
    fun `should deserialize stub configuration in SpecmaticConfig successfully`(@TempDir tempDir: File) {
        val configFile = tempDir.resolve("specmatic.yaml")
        val configYaml = """
            stub:
                generative: true
                delayInMilliseconds: 1000
                dictionary: stubDictionary
                includeMandatoryAndRequestedKeysInResponse: true
        """.trimIndent()
        configFile.writeText(configYaml)

        val specmaticConfig = configFile.toSpecmaticConfig()

        specmaticConfig.apply {
            assertThat(getStubGenerative()).isTrue()
            assertThat(getStubDelayInMilliseconds()).isEqualTo(1000L)
            assertThat(getStubDictionary()).isEqualTo("stubDictionary")
            assertThat(getStubIncludeMandatoryAndRequestedKeysInResponse()).isTrue()
        }
    }

    @Test
    fun `should convert config from v1 to v2 when stub configuration is present`() {
        val configYaml = """
            stub:
                generative: true
                delayInMilliseconds: 1000
                dictionary: stubDictionary
                includeMandatoryAndRequestedKeysInResponse: true
        """.trimIndent()

        val configFromV1 = objectMapper.readValue(configYaml, SpecmaticConfigV1::class.java).transform()
        val configV2 = SpecmaticConfigV2.loadFrom(configFromV1) as SpecmaticConfigV2

        configV2.stub.apply {
            assertThat(getGenerative()).isTrue()
            assertThat(getDelayInMilliseconds()).isEqualTo(1000L)
            assertThat(getDictionary()).isEqualTo("stubDictionary")
            assertThat(getIncludeMandatoryAndRequestedKeysInResponse()).isTrue()
        }
    }

    @Test
    fun `should deserialize workflow configuration in SpecmaticConfig successfully`(@TempDir tempDir: File) {
        val configFile = tempDir.resolve("specmatic.yaml")
        val configYaml = """
            workflow:
              ids:
                "POST / -> 201":
                  extract: "BODY.id"
                "*":
                  use: "PATH.id"
        """.trimIndent()
        configFile.writeText(configYaml)

        val specmaticConfig = configFile.toSpecmaticConfig()

        specmaticConfig.apply {
            assertThat(specmaticConfig.getWorkflowDetails()?.getUseForAPI("*")).isEqualTo("PATH.id")
            assertThat(specmaticConfig.getWorkflowDetails()?.getExtractForAPI("POST / -> 201")).isEqualTo("BODY.id")
        }
    }

    @Test
    fun `should convert config from v1 to v2 when workflow configuration is present`() {
        val configYaml = """
            workflow:
              ids:
                "POST / -> 201":
                  extract: "BODY.id"
                "*":
                  use: "PATH.id"
        """.trimIndent()

        val configFromV1 = objectMapper.readValue(configYaml, SpecmaticConfigV1::class.java).transform()
        val configV2 = SpecmaticConfigV2.loadFrom(configFromV1) as SpecmaticConfigV2

        configV2.workflow!!.apply {
            assertThat(getUseForAPI("*")).isEqualTo("PATH.id")
            assertThat(getExtractForAPI("POST / -> 201")).isEqualTo("BODY.id")
        }
    }

    @Test
    fun `when the source v1 config has a source of filesystem type with no directory it gets converted to v2 with no source` () {
        val contractConfigYaml = """
            sources:
              - provides:
                - com/petstore/1.yaml
        """.trimIndent()

        val configV1 = objectMapper.readValue(contractConfigYaml, SpecmaticConfigV1::class.java)

        val dslConfig = configV1.transform()

        val configV2 = SpecmaticConfigV2.loadFrom(dslConfig) as SpecmaticConfigV2

        assertThat(configV2.contracts.first().contractSource).isNull()
    }

    @Test
    fun `when the source v1 config has a source of git type with no directory it gets converted to v2 with git source written out` () {
        val contractConfigYaml = """
            sources:
              - provider: git
                repository: http://source.in
                provides:
                - com/petstore/1.yaml
        """.trimIndent()

        val configV1 = objectMapper.readValue(contractConfigYaml, SpecmaticConfigV1::class.java)

        val dslConfig = configV1.transform()

        val configV2 = SpecmaticConfigV2.loadFrom(dslConfig) as SpecmaticConfigV2

        val contractSource = configV2.contracts.first().contractSource

        assertThat(contractSource).isNotNull()
        assertThat(contractSource).isInstanceOf(GitContractSource::class.java)
    }
}