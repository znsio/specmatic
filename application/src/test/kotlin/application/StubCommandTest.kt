package application

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import picocli.CommandLine
import picocli.CommandLine.IFactory

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

    @MockkBean
    lateinit var watcher: Watcher

    @BeforeEach
    fun `clean up stub command`() {
        stubCommand.contractPaths = arrayListOf()
    }

    @Test
    fun `when contract files are not given it should load from qontract config`() {
        every { watchMaker.make(listOf("/config/path/to/contract.qontract")) }.returns(watcher)
        every { qontractConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.qontract"));

        CommandLine(stubCommand, factory).execute()

        verify(exactly = 1) { qontractConfig.contractStubPaths() }
        verify(exactly = 1) { watchMaker.make(listOf("/config/path/to/contract.qontract")) }
    }

    @Test
    fun `when contract files are given it should not load from qontract config`() {
        every { watchMaker.make(listOf("/parameter/path/to/contract.qontract")) }.returns(watcher)
        every { qontractConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.qontract"));

        CommandLine(stubCommand, factory).execute("/parameter/path/to/contract.qontract")

        verify(exactly = 0) { qontractConfig.contractStubPaths() }
        verify(exactly = 1) { watchMaker.make(listOf("/parameter/path/to/contract.qontract")) }
    }
}