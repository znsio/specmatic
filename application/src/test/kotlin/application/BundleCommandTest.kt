package application

import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import picocli.CommandLine
import run.qontract.core.utilities.ContractPathData
import java.io.File

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [QontractApplication::class, BundleCommand::class])
internal class BundleCommandTest {
    @MockkBean
    lateinit var qontractConfig: QontractConfig

    @MockkBean
    lateinit var zipper: Zipper

    @MockkBean
    lateinit var fileOperations: FileOperations

    @Autowired
    lateinit var factory: CommandLine.IFactory

    @Autowired
    lateinit var bundleCommand: BundleCommand

    @Test
    fun `the command should compress all stub commands in the config into a zip file`() {
        val file = mockk<File>()
        val contractPath = "/Users/jane_doe/.qontract/repos/git-repo/com/example/api_1.qontract"
        val bytes = "123".encodeToByteArray()

        every { qontractConfig.contractStubPathData() }.returns(listOf(ContractPathData("/Users/jane_doe/.qontract/repos/git-repo/", contractPath)))
        every { fileOperations.readBytes(contractPath) }.returns(bytes)

        val files = listOf(file)
        val stubFilePath = "/Users/jane_doe/.qontract/repos/git-repo/com/example/api_1_data/stub.json"

        every { file.isFile } returns(true)
        every { file.name } returns("stub.json")
        every { file.path } returns stubFilePath
        every { fileOperations.isJSONFile(file) } returns(true)
        every { fileOperations.readBytes(stubFilePath) }.returns(bytes)

        every { fileOperations.files("/Users/jane_doe/.qontract/repos/git-repo/com/example/api_1_data") }.returns(files)
        justRun { zipper.compress("./bundle.zip", listOf(ZipperEntry("git-repo/com/example/api_1.qontract", bytes), ZipperEntry("git-repo/com/example/api_1_data/stub.json", bytes))) }

        CommandLine(bundleCommand, factory).execute()

        verify(exactly = 1) { qontractConfig.contractStubPathData() }
        verify(exactly = 1) { fileOperations.readBytes(contractPath) }
        verify(exactly = 1) { fileOperations.readBytes(stubFilePath) }
        verify(exactly = 1) { zipper.compress("./bundle.zip", listOf(ZipperEntry("git-repo/com/example/api_1.qontract", bytes), ZipperEntry("git-repo/com/example/api_1_data/stub.json", bytes))) }
    }

    @Test
    fun `should list stub files in a given directory`() {
        val jsonFilePath = "/namespace/contract_data/path.json"
        val file = File(jsonFilePath)
        every { fileOperations.isJSONFile(file) }.returns(true)

        val stubDataDir = "/namespace/contract_data"
        every { fileOperations.files(stubDataDir) }.returns(listOf(file))

        val files = stubFilesIn(stubDataDir, fileOperations)

        assertThat(files.single()).isEqualTo(jsonFilePath)
        verify(exactly = 1) { fileOperations.isJSONFile(file) }
        verify(exactly = 1) { fileOperations.files(stubDataDir) }
    }

    @Test
    fun `should list stub files in subdirectories of the given directory`() {
        val jsonFilePath = "/namespace/contract_data/path.json"
        val file = mockk<File>()
        val subDir = mockk<File>()

        every { file.path }.returns(jsonFilePath)
        every { subDir.isDirectory }.returns(true)
        every { subDir.name }.returns("subdir")

        every { fileOperations.isJSONFile(file) }.returns(true)
        every { fileOperations.isJSONFile(subDir) }.returns(false)

        val subDirFile = mockk<File>()
        every { fileOperations.isJSONFile(subDirFile) }.returns(true)
        every { subDirFile.path }.returns("/namespace/contract_data/subdir/more.json")
        every { fileOperations.files("/namespace/contract_data/subdir") }.returns(listOf(subDirFile))

        val stubDataDir = "/namespace/contract_data"
        every { fileOperations.files(stubDataDir) }.returns(listOf(file, subDir))

        val files = stubFilesIn(stubDataDir, fileOperations)

        assertThat(files.toSet()).isEqualTo(setOf("/namespace/contract_data/path.json", "/namespace/contract_data/subdir/more.json"))

        verify(exactly = 1) { file.path }
        verify(exactly = 1) { subDir.isDirectory }
        verify(exactly = 1) { subDir.name }

        verify(exactly = 1) { fileOperations.isJSONFile(file) }
        verify(exactly = 1) { fileOperations.isJSONFile(subDir) }

        verify(exactly = 1) { fileOperations.isJSONFile(subDirFile) }
        verify(exactly = 1) { subDirFile.path }
        verify(exactly = 1) { fileOperations.files("/namespace/contract_data/subdir") }

        verify(exactly = 1) { fileOperations.files(stubDataDir) }
    }

    @Test
    fun `should avoid entries that are neither file nor directory`() {
        val file = mockk<File>()

        every { fileOperations.isJSONFile(file) }.returns(false)
        every { file.isDirectory }.returns(false)

        val stubDataDir = "/namespace/contract_data"
        every { fileOperations.files(stubDataDir) }.returns(listOf(file))

        val files = stubFilesIn(stubDataDir, fileOperations)

        assertThat(files).isEmpty()
        verify(exactly = 1) { fileOperations.isJSONFile(file) }
        verify(exactly = 1) { file.isDirectory }
        verify(exactly = 1) { fileOperations.files(stubDataDir) }
    }
}
