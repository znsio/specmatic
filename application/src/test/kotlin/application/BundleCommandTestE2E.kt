package application

import `in`.specmatic.core.Configuration
import `in`.specmatic.core.git.SystemGit
import io.ktor.util.*
import io.ktor.utils.io.streams.*
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.util.function.Consumer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [SpecmaticApplication::class, BundleCommand::class])
internal class BundleCommandTestE2E {
    companion object {
        @TempDir
        lateinit var tempDir: File
        lateinit var configFilename: String
        lateinit var projectDir: File
        lateinit var specmaticJSONContent: String

        @BeforeAll
        @JvmStatic
        fun setUp() {
            val bundleTestDir = tempDir.resolve("bundle_tests")
            val gitDir = bundleTestDir.resolve("bundle_test_contracts")

            val gitInit = Git.init().setDirectory(gitDir)
            val git = gitInit.call()

            createFile(gitDir, "test.yaml", "dummy contract content")
            val implicitDir = createDir(gitDir, "test_data")
            createFile(implicitDir, "stub.json", "dummy stub content")

            val separateDataDir = createDir(gitDir, "data")
            val implicitCustomBaseDir = createDir(separateDataDir, "test_data")
            createFile(implicitCustomBaseDir, "stub2.json", "dummy stub content 2")

            git.add().addFilepattern(".").call()
            git.commit().also { it.message = "First commit" }.call()

            projectDir = createDir(bundleTestDir, "project")
            val specmaticJSONFile = createFile(projectDir, "specmatic.json")

            specmaticJSONContent = """
                {
                  "sources": [
                    {
                      "provider": "git",
                      "repository": "${gitDir.canonicalPath}",
                      "test": [
                        "test.yaml"
                      ],
                      "stub": [
                        "test.yaml"
                      ]
                    }
                  ]
                }
            """.trimIndent()

            specmaticJSONFile.writeText(specmaticJSONContent)

            configFilename = Configuration.globalConfigFileName
            Configuration.globalConfigFileName = specmaticJSONFile.canonicalPath
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            Configuration.globalConfigFileName = configFilename
        }

        private fun createDir(gitDir: File, dirName: String): File {
            val implicitDir = gitDir.resolve(dirName)
            implicitDir.mkdirs()
            return implicitDir
        }

        private fun createFile(gitDir: File, filename: String, content: String? = null): File {
            val newFile = gitDir.resolve(filename)
            newFile.createNewFile()

            if(content != null)
                newFile.writeText(content)

            return newFile
        }
    }

    @Autowired
    lateinit var factory: CommandLine.IFactory

    @Autowired
    lateinit var bundleCommand: BundleCommand

    @Test
    fun `basic stub bundle command functionality`() {
        val bundleFile = projectDir.resolve("bundle.zip")
        try {
            bundleCommand.bundleOutputPath = bundleFile.canonicalPath
            bundleCommand.call()

            val entries = mutableListOf<Pair<String, String>>()

            FileInputStream(bundleCommand.bundleOutputPath!!).use { zipFileInputStream ->
                ZipInputStream(zipFileInputStream).use { zipIn ->
                    var zipEntry = zipIn.nextEntry
                    while (zipEntry != null) {
                        val content = String(zipIn.asInput().asStream().readBytes())
                        entries.add(Pair(zipEntry.name, content))
                        zipEntry = zipIn.nextEntry
                    }
                }
            }

            println(entries)
            assertThat(entries).hasSize(2)
            assertThat(entries).anySatisfy(Consumer {
                assertThat(it.first).contains("test.yaml")
                assertThat(it.second).contains("dummy contract content")
            })
            assertThat(entries).anySatisfy(Consumer {
                assertThat(it.first).contains("stub.json")
                assertThat(it.second).contains("dummy stub content")
            })
        } finally {
            bundleFile.delete()
        }
    }

    @Test
    fun `bundle is generated with shifted base`() {
        val bundleFile = projectDir.resolve("bundle.zip")
        try {
            bundleCommand.bundleOutputPath = bundleFile.canonicalPath
            System.setProperty("customImplicitStubBase", "data")
            bundleCommand.call()

            val entries = mutableListOf<Pair<String, String>>()

            FileInputStream(bundleCommand.bundleOutputPath!!).use { zipFileInputStream ->
                ZipInputStream(zipFileInputStream).use { zipIn ->
                    var zipEntry = zipIn.nextEntry
                    while (zipEntry != null) {
                        val content = String(zipIn.asInput().asStream().readBytes())
                        entries.add(Pair(zipEntry.name, content))
                        zipEntry = zipIn.nextEntry
                    }
                }
            }

            println(entries)
            assertThat(entries).hasSize(3)
            assertThat(entries).anySatisfy(Consumer {
                assertThat(it.first).contains("test.yaml")
                assertThat(it.second).contains("dummy contract content")
            })
            assertThat(entries).anySatisfy(Consumer {
                assertThat(it.first).contains("stub.json")
                assertThat(it.second).contains("dummy stub content")
            })
            assertThat(entries).anySatisfy(Consumer {
                assertThat(it.first).contains("stub2.json")
                assertThat(it.second).contains("dummy stub content 2")
            })
        } finally {
            System.clearProperty("customImplicitStubBase")
            bundleFile.delete()
        }
    }

    @Test
    fun `test bundle is generated`() {
        val bundleFile = projectDir.resolve("bundle.zip")
        try {
            bundleCommand.bundleOutputPath = bundleFile.canonicalPath
            bundleCommand.testBundle = true
            System.setProperty("customImplicitStubBase", "data")
            bundleCommand.call()

            val entries = mutableListOf<Pair<String, String>>()

            FileInputStream(bundleCommand.bundleOutputPath!!).use { zipFileInputStream ->
                ZipInputStream(zipFileInputStream).use { zipIn ->
                    var zipEntry = zipIn.nextEntry
                    while (zipEntry != null) {
                        val content = String(zipIn.asInput().asStream().readBytes())
                        entries.add(Pair(zipEntry.name, content))
                        zipEntry = zipIn.nextEntry
                    }
                }
            }

            println(entries)
            assertThat(entries).hasSize(2)
            assertThat(entries).anySatisfy(Consumer {
                assertThat(it.first).contains("test.yaml")
                assertThat(it.second).contains("dummy contract content")
            })
            assertThat(entries).anySatisfy(Consumer {
                assertThat(it.first).contains("specmatic.json")
                assertThat(it.second).contains(specmaticJSONContent)
            })
        } finally {
            System.clearProperty("customImplicitStubBase")
            bundleFile.delete()
        }
    }
}