package application

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import picocli.CommandLine
import picocli.CommandLine.IFactory
import run.qontract.core.QONTRACT_EXTENSION
import java.io.File
import java.nio.file.Path

@SpringBootTest(webEnvironment = NONE, classes = arrayOf(QontractApplication::class, StubCommand::class))
internal class StubCommandTest {
    @MockkBean
    lateinit var qontractConfig: QontractConfig

    @MockkBean
    lateinit var reader: RealFileReader

    @Autowired
    lateinit var factory: IFactory

    @Autowired
    lateinit var stubCommand: StubCommand

    @MockkBean
    lateinit var watchMaker: WatchMaker

    @MockkBean(relaxUnitFun = true)
    lateinit var watcher: Watcher

    @BeforeEach
    fun `clean up stub command`() {
        stubCommand.contractPaths = arrayListOf()
    }

    @Test
    fun `when contract files are not given it should load from qontract config`() {
        every { watchMaker.make(listOf("/config/path/to/contract.qontract")) }.returns(watcher)
        every { qontractConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.qontract"))
        every { reader.isFile("/config/path/to/contract.qontract") }.returns(true)
        every { reader.extensionIsNot("/config/path/to/contract.qontract", QONTRACT_EXTENSION) }.returns(false)

        CommandLine(stubCommand, factory).execute()

        verify(exactly = 1) { qontractConfig.contractStubPaths() }
        verify(exactly = 1) { watchMaker.make(listOf("/config/path/to/contract.qontract")) }
    }

    @Test
    fun `when contract files are given it should not load from qontract config`() {
        every { watchMaker.make(listOf("/parameter/path/to/contract.qontract")) }.returns(watcher)
        every { qontractConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.qontract"))
        every { reader.isFile("/parameter/path/to/contract.qontract") }.returns(true)
        every { reader.extensionIsNot("/parameter/path/to/contract.qontract", QONTRACT_EXTENSION) }.returns(false)

        CommandLine(stubCommand, factory).execute("/parameter/path/to/contract.qontract")

        verify(exactly = 0) { qontractConfig.contractStubPaths() }
        verify(exactly = 1) { watchMaker.make(listOf("/parameter/path/to/contract.qontract")) }
    }

    @Test
    fun `when a contract with the correct extension is given it should be loaded`(@TempDir tempDir: Path) {
        val validQontract = tempDir.resolve("contract.qontract")

        val qontractFilePath = validQontract.toAbsolutePath().toString()
        File(qontractFilePath).writeText("""
            Feature: Is a dummy feature
        """.trimIndent())

        every { watchMaker.make(listOf(qontractFilePath)) }.returns(watcher)
        every { qontractConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.qontract"))
        every { reader.isFile(qontractFilePath) }.returns(true)
        every { reader.extensionIsNot(qontractFilePath, QONTRACT_EXTENSION) }.returns(false)

        val execute = CommandLine(stubCommand, factory).execute(qontractFilePath)

        assertThat(execute).isEqualTo(0)
    }

    @Test
    @ExpectSystemExitWithStatus(1)
    fun `when a contract with the incorrect extension command should exit with non-zero`(@TempDir tempDir: Path) {
        val invalidQontract = tempDir.resolve("contract.contract")

        val qontractFilePath = invalidQontract.toAbsolutePath().toString()
        File(qontractFilePath).writeText("""
            Feature: Is a dummy feature
        """.trimIndent())

        every { watchMaker.make(listOf(qontractFilePath)) }.returns(watcher)
        every { qontractConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.qontract"))
        every { reader.isFile(qontractFilePath) }.returns(true)
        every { reader.extensionIsNot(qontractFilePath, QONTRACT_EXTENSION) }.returns(true)

        CommandLine(stubCommand, factory).execute(qontractFilePath)
    }
}
