package io.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class CommandHookTest {
    companion object {
        private val primaryContractString = """
        ---
        openapi: "3.0.1"
        info:
          title: "Random API"
          version: "1"
        paths:
          /:
            get:
              summary: "Random number"
              parameters: []
              responses:
                "200":
                  description: "Random number"
                  content:
                    text/plain:
                      schema:
                        type: "number"
    """.trimIndent()

        private val secondaryContractString = """
        ---
        openapi: "3.0.1"
        info:
          title: "Random API"
          version: "1"
        paths:
          /:
            get:
              summary: "Random number"
              parameters: []
              responses:
                "200":
                  description: "Random number"
                  content:
                    text/plain:
                      schema:
                        type: "number"
    """.trimIndent()

        @TempDir
        @JvmStatic
        lateinit var dir: File

        @JvmStatic
        lateinit var primary: File

        @JvmStatic
        lateinit var secondary: File

        @BeforeAll
        @JvmStatic
        fun setup() {
            primary = dir.resolve("primary.yaml")
            primary.createNewFile()
            primary.writeText(primaryContractString)

            secondary = dir.resolve("secondary.yaml")
            secondary.createNewFile()
            secondary.writeText(secondaryContractString)
        }
    }

    @Test
    fun `command hook when hook exists`() {
        Configuration.config = SpecmaticConfig(emptyList(), hooks = mapOf(HookName.stub_load_contract.name to "cat ${secondary.canonicalPath}"))

        val contractInFile = CommandHook(HookName.stub_load_contract).readContract(primary.canonicalPath)
        Configuration.config = SpecmaticConfig(emptyList())
        assertThat(contractInFile.trimIndent()).isEqualTo(secondaryContractString)
    }

    @Test
    fun `command hook when hook does not exist`() {
        val primary = dir.resolve("primary.yaml")
        primary.createNewFile()
        primary.writeText(primaryContractString)

        val contractInFile = CommandHook(HookName.stub_load_contract).readContract(primary.canonicalPath)
        assertThat(contractInFile.trimIndent()).isEqualTo(primaryContractString)
    }
}
