package application

import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import java.util.concurrent.Callable

@Component
@Command(
        name = "specmatic",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class,
        subcommands = [BundleCommand::class, CompareCommand::class, CompatibleCommand::class, GraphCommand::class, ToOpenAPICommand::class, ImportCommand::class, InstallCommand::class, ProxyCommand::class, PushCommand::class, SamplesCommand::class, StubCommand::class, SubscribeCommand::class, TestCommand::class]
)
class SpecmaticCommand : Callable<Int> {
    override fun call(): Int {
        return 0
    }
}
