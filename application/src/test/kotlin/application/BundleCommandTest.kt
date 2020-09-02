package application

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import picocli.CommandLine
import run.qontract.core.utilities.ContractPathData

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [QontractApplication::class, BundleCommand::class])
internal class BundleCommandTest {
    @MockkBean
    lateinit var qontractConfig: QontractConfig

    @MockkBean
    lateinit var zipper: Zipper

    @MockkBean
    lateinit var fileReader: RealFileReader

    @Autowired
    lateinit var factory: CommandLine.IFactory

    @Autowired
    lateinit var stubCommand: BundleCommand

    @Test
    fun `the command should compress all stub commands in the config into a zip file`() {
        val contractPath = "/Users/jane_doe/.qontract/repos/git-repo/com/example/api_1.qontract"
        val contractContents = "123".encodeToByteArray()

        every { qontractConfig.contractStubPathData() }.returns(listOf(ContractPathData("/Users/jane_doe/.qontract/repos/git-repo/", contractPath)))
        every { fileReader.readBytes(contractPath) }.returns(contractContents)
        every { zipper.compress("./bundle.zip", listOf(ZipperEntry("git-repo/com/example/api_1.qontract", contractContents))) }

        CommandLine(stubCommand, factory).execute()

        verify(exactly = 1) { qontractConfig.contractStubPathData() }
        verify(exactly = 1) { fileReader.readBytes(contractPath) }
        verify(exactly = 1) { zipper.compress("./bundle.zip", listOf(ZipperEntry("git-repo/com/example/api_1.qontract", contractContents))) }
    }
}
