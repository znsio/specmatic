package application

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import picocli.CommandLine
import run.qontract.core.utilities.UncaughtExceptionHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.logging.LogManager
import kotlin.system.exitProcess

@SpringBootApplication
open class QontractApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            setupLogging()

            Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler())

            when {
                args.isEmpty() -> CommandLine(QontractCommand()).usage(System.out)
                else ->  {
                    val app = SpringApplication(QontractApplication::class.java)
                    app.setBannerMode(Banner.Mode.OFF)

                    exitProcess(SpringApplication.exit(app.run(*args)))
                }
            }
        }

        private fun setupLogging() {
            val logManager = LogManager.getLogManager()
            val props = Properties()
            props.setProperty("java.util.logging.ConsoleHandler.level", "FINE")
            val out = ByteArrayOutputStream(512)
            props.store(out, "No comment")
            logManager.readConfiguration(ByteArrayInputStream(out.toByteArray()))
        }
    }

}