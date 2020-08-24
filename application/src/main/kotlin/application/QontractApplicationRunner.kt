package application

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.stereotype.Component
import picocli.CommandLine

@Component
class QontractApplicationRunner(qontractCommand: QontractCommand, factory: CommandLine.IFactory) : CommandLineRunner, ExitCodeGenerator {
    private val myCommand: QontractCommand = qontractCommand
    private val factory: CommandLine.IFactory = factory
    private var exitCode = 0

    @Throws(Exception::class)
    override fun run(vararg args: String) {
        exitCode = CommandLine(myCommand, factory).execute(*args)
    }

    override fun getExitCode() = exitCode
}