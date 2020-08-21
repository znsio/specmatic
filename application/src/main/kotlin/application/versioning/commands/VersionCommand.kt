package application.versioning.commands

import application.PushCommand
import picocli.CommandLine
import picocli.CommandLine.Command
import java.util.concurrent.Callable

@Command(name = "version", mixinStandardHelpOptions = true, description = ["Suggest the version of a contract"], subcommands = [])
class VersionCommand: Callable<Unit> {
    override fun call() {
        CommandLine(VersionCommand()).usage(System.out)
    }
}