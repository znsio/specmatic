package io.specmatic.core.config

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.specmatic.core.Source
import io.specmatic.core.SourceProvider
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
        assertThat(config.sources.size).isEqualTo(2)
        val expectedSources = if(version != SpecmaticConfigVersion.VERSION_3) listOf(
            Source(
                provider = SourceProvider.git,
                repository = "https://contracts",
                branch = "1.0.1",
                test = listOf("com/petstore/1.yaml"),
                stub = listOf(Consumes.StringValue("com/petstore/payment.yaml"))
            ),
            Source(
                provider = SourceProvider.filesystem,
                test = listOf("com/petstore/1.yaml"),
                stub = listOf(
                    Consumes.StringValue("com/petstore/payment.yaml"),
                    Consumes.StringValue("com/petstore/order.yaml")
                ),
                directory = "contracts"
            )
        ) else listOf(
            Source(
                provider = SourceProvider.git,
                repository = "https://contracts",
                branch = "1.0.1",
                test = listOf("com/petstore/1.yaml"),
                stub = listOf(Consumes.StringValue("com/petstore/payment.yaml"))
            ),
            Source(
                provider = SourceProvider.filesystem,
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
        assertThat(config.sources).containsAll(expectedSources)
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
    fun `should serialize ContractConfig successfully`(configFile: String) {
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
    fun `should deserialize ContractConfig successfully`() {
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

        val objectMapper = ObjectMapper().registerKotlinModule()
        val contractsJson = objectMapper.writeValueAsString(contracts)

        assertThat(parsedJSON(contractsJson)).isEqualTo(parsedJSON(expectedContractsJson))
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/v3/specmatic_config_v3.yaml",
        "./src/test/resources/specmaticConfigFiles/v3/specmatic_config_v3.json"
    )
    @ParameterizedTest
    fun `should serialize ContractConfigV2 successfully`(configFile: String) {
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
    fun `should deserialize ContractConfigV2 successfully`() {
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

        val objectMapper = ObjectMapper().registerKotlinModule()
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
    fun `should throw JsonMappingException when Git URL is absent in the Contract`() {
        val contractConfigYaml = """
            git:
              branch: 1.0.1
            provides:
              - com/petstore/1.yaml
            consumes:
              - com/petstore/payment.yaml
        """.trimIndent()

        val exception = assertThrows<JsonMappingException> {
            objectMapper.readValue(contractConfigYaml, ContractConfig::class.java)
        }
        assertThat(exception.message).startsWith("Git contract source must have 'url' field")
    }

    @Test
    fun `should throw JsonMappingException when Filesystem Directory is empty in the Contract`() {
        val contractConfigYaml = """
            filesystem:
                directory: ""
            provides:
              - com/petstore/1.yaml
            consumes:
              - com/petstore/payment.yaml
        """.trimIndent()

        val exception = assertThrows<JsonMappingException> {
            objectMapper.readValue(contractConfigYaml, ContractConfig::class.java)
        }
        assertThat(exception.message).startsWith("Filesystem contract source must have 'directory' field")
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
    fun `should serialize SpecmaticConfig successfully when AllPatternsMandatory key is present`() {
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
    fun `should serialize SpecmaticConfig successfully when IgnoreInlineExamples key is present`() {
        val configYaml = """
            ignoreInlineExamples: true
        """.trimIndent()

        val config = objectMapper.readValue(configYaml, SpecmaticConfigV1::class.java).transform()
        val configV2 = SpecmaticConfigV2.loadFrom(config) as SpecmaticConfigV2

        assertThat(configV2.ignoreInlineExamples).isTrue()
    }
}