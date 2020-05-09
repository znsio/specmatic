package application.versioning.commands

import application.QontractApplication
import picocli.CommandLine
import picocli.CommandLine.Command
import run.qontract.core.*
import run.qontract.core.pattern.ContractException
import java.io.File
import java.lang.NumberFormatException
import java.util.concurrent.Callable

@Command(name = "version", mixinStandardHelpOptions = true, description = ["Suggest the version of a contract"], subcommands = [CheckCommand::class, CheckoutCommand::class, IncrementCommand::class, ListCommand::class, RepoCommand::class, ShowCommand::class, SuggestCommand::class, UpdateCommand::class])
class VersionCommand: Callable<Unit> {
    override fun call() {
        CommandLine(VersionCommand()).usage(System.out)
    }
}