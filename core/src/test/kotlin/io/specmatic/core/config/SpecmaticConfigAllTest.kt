package io.specmatic.core.config

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.specmatic.core.Source
import io.specmatic.core.SourceProvider.filesystem
import io.specmatic.core.SourceProvider.git
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.config.v2.ContractConfig
import io.specmatic.core.config.v2.ContractConfig.FileSystemContractSource
import io.specmatic.core.config.v2.ContractConfig.GitContractSource
import io.specmatic.core.config.v2.SpecmaticConfigV2
import io.specmatic.core.loadSpecmaticConfig
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.parsedJSON
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
        "VERSION_1, ./src/test/resources/specmaticConfigFiles/specmatic_config_v1.yaml",
        "VERSION_2, ./src/test/resources/specmaticConfigFiles/specmatic_config_v2.yaml",
        "VERSION_1, ./src/test/resources/specmaticConfigFiles/specmatic_without_version.json",
        "VERSION_1, ./src/test/resources/specmaticConfigFiles/specmatic_config_v1.json",
        "VERSION_2, ./src/test/resources/specmaticConfigFiles/specmatic_config_v2.json"
    )
    @ParameterizedTest
    fun `should create SpecmaticConfig given SpecmaticConfigV1`(version: SpecmaticConfigVersion, configFile: String) {
        val config: SpecmaticConfig = loadSpecmaticConfig(configFile)
        assertThat(config.getVersion()).isEqualTo(version)
        val sources = config.sources
        assertThat(sources.size).isEqualTo(2)
        val expectedSources = listOf(
            Source(
                provider = git,
                repository = "https://contracts",
                branch = "1.0.1",
                test = listOf("com/petstore/1.yaml"),
                stub = listOf("com/petstore/payment.yaml")
            ),
            Source(
                provider = filesystem,
                test = listOf("com/petstore/1.yaml"),
                stub = listOf("com/petstore/payment.yaml", "com/petstore/order.yaml"),
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
        "./src/test/resources/specmaticConfigFiles/specmatic_config_v2.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic_config_v2.json"
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
}