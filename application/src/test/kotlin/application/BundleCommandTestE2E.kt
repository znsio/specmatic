package application

import `in`.specmatic.core.Configuration
import `in`.specmatic.core.git.SystemGit
import io.ktor.util.*
import io.ktor.utils.io.streams.*
import io.mockk.clearAllMocks
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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

class BundleTestData(tempDir: File) {
    val projectDir: File
    val specmaticJSONContent: String
    val configFilename: String

    init {
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [SpecmaticApplication::class, BundleCommand::class])
internal class BundleCommandTestE2E {
    companion object {
        lateinit var configFilename: String

        @BeforeAll
        @JvmStatic
        fun setUp() {
            configFilename = Configuration.globalConfigFileName
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            Configuration.globalConfigFileName = configFilename
        }
    }

    @Autowired
    lateinit var factory: CommandLine.IFactory

    @Autowired
    lateinit var bundleCommand: BundleCommand

    @BeforeEach
    fun setupEachTest() {
        clearAllMocks()
    }

    @Test
    fun `basic stub bundle command functionality`(@TempDir tempDir: File) {
        val bundleTestData = BundleTestData(tempDir)
        val bundleFile = bundleTestData.projectDir.resolve("bundle.zip")

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
        assertThat(entries).anySatisfy(Consumer {
            assertThat(it.first).contains("test.yaml")
            assertThat(it.second).contains("dummy contract content")
        })
        assertThat(entries).anySatisfy(Consumer {
            assertThat(it.first).contains("stub.json")
            assertThat(it.second).contains("dummy stub content")
        })
        assertThat(entries).hasSize(2)
    }

    @Test
    fun `bundle is generated with shifted base`(@TempDir tempDir: File) {
        val bundleTestData = BundleTestData(tempDir)
        val bundleFile = bundleTestData.projectDir.resolve("bundle.zip")

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

            assertThat(entries).hasSize(3)
        } finally {
            System.clearProperty("customImplicitStubBase")
        }
    }

    @Test
    fun `test bundle is generated`(@TempDir tempDir: File) {
        val bundleTestData = BundleTestData(tempDir)
        val bundleFile = bundleTestData.projectDir.resolve("bundle.zip")

        bundleCommand.bundleOutputPath = bundleFile.canonicalPath
        bundleCommand.testBundle = true
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
        assertThat(entries).anySatisfy(Consumer {
            assertThat(it.first).contains("test.yaml")
            assertThat(it.second).contains("dummy contract content")
        })
        assertThat(entries).anySatisfy(Consumer {
            assertThat(it.first).contains("specmatic.json")
            assertThat(it.second).contains(bundleTestData.specmaticJSONContent)
        })
        assertThat(entries).hasSize(2)
    }
}
