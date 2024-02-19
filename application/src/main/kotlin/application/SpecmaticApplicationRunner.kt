package application

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.stereotype.Component
import picocli.CommandLine

@Component
class SpecmaticApplicationRunner(specmaticCommand: SpecmaticCommand, private val factory: CommandLine.IFactory) : CommandLineRunner, ExitCodeGenerator {
    private val myCommand: SpecmaticCommand = specmaticCommand
    private var exitCode = 0

    @Throws(Exception::class)
    override fun run(vararg args: String) {
        val cmd = CommandLine(myCommand, factory)
        cmd.subcommands.getValue("generate-completion").commandSpec.usageMessage().hidden(true)

        exitCode = cmd.execute(*args)
    }

    override fun getExitCode() = exitCode
}