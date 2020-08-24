package application

import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import java.util.concurrent.Callable

@Component
@Command(
        name = "qontract",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class,
        subcommands = [CompareCommand::class, CompatibleCommand::class, ImportCommand::class, InstallCommand::class, ProxyCommand::class, PushCommand::class, SamplesCommand::class, StubCommand::class, SubscribeCommand::class, TestCommand::class]
)
class QontractCommand : Callable<Int> {
    override fun call(): Int {
        return 0
    }
}
