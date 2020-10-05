package application

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
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
    lateinit var stubCommand: BundleCommand

    @MockkBean
    lateinit var file: File

    @Test
    fun `the command should compress all stub commands in the config into a zip file`() {
        val contractPath = "/Users/jane_doe/.qontract/repos/git-repo/com/example/api_1.qontract"
        val bytes = "123".encodeToByteArray()

        every { qontractConfig.contractStubPathData() }.returns(listOf(ContractPathData("/Users/jane_doe/.qontract/repos/git-repo/", contractPath)))
        every { fileOperations.readBytes(contractPath) }.returns(bytes)

        val files = listOf(file)
        val stubFilePath = "/Users/jane_doe/.qontract/repos/git-repo/com/example/api_1_data/stub.json"

        every { file.isFile } returns(true)
        every { file.name } returns("stub.json")
        every { file.path } returns stubFilePath
        every { fileOperations.readBytes(stubFilePath) }.returns(bytes)

        every { fileOperations.files("/Users/jane_doe/.qontract/repos/git-repo/com/example/api_1_data") }.returns(files)
        justRun { zipper.compress("./bundle.zip", listOf(ZipperEntry("git-repo/com/example/api_1.qontract", bytes), ZipperEntry("git-repo/com/example/api_1_data/stub.json", bytes))) }

        CommandLine(stubCommand, factory).execute()

        verify(exactly = 1) { qontractConfig.contractStubPathData() }
        verify(exactly = 1) { fileOperations.readBytes(contractPath) }
        verify(exactly = 1) { fileOperations.readBytes(stubFilePath) }
        verify(exactly = 1) { zipper.compress("./bundle.zip", listOf(ZipperEntry("git-repo/com/example/api_1.qontract", bytes), ZipperEntry("git-repo/com/example/api_1_data/stub.json", bytes))) }
    }
}
