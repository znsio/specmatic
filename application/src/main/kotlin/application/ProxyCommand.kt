package application

import picocli.CommandLine.*
import run.qontract.consoleLog
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.core.utilities.exitWithMessage
import run.qontract.proxy.Proxy
import java.io.File
import java.lang.Thread.sleep
import java.util.concurrent.Callable

@Command(name = "proxy",
        mixinStandardHelpOptions = true,
        description = ["Proxies requests to the specified target and converts the result into contracts and stubs"])
class ProxyCommand : Callable<Unit> {
    @Parameters(description = ["Base URL of the target to be proxied to"], index = "0")
    lateinit var targetBaseURL: String

    @Parameters(description = ["Store data from the proxy interactions into this dir"], index = "1")
    lateinit var proxyQontractDataDir: String

    @Option(names = ["--host"], description = ["Host for the proxy"], defaultValue = "localhost")
    lateinit var host: String

    @Option(names = ["--port"], description = ["Port for the proxy"], defaultValue = "9000")
    var port: Int = 9000

    var proxy: Proxy? = null

    override fun call() {
        validatedProxySettings(targetBaseURL, proxyQontractDataDir)

        proxy = Proxy(host, port, targetBaseURL, proxyQontractDataDir)
        addShutdownHook()
        consoleLog("Proxy server is running on http://$host:$port. Ctrl + C to stop.")
        while(true) sleep(10000)
    }

    private fun validatedProxySettings(unknownProxyTarget: String?, proxyQontractDataDir: String?) {
        if(unknownProxyTarget == null && proxyQontractDataDir == null) return

        if(unknownProxyTarget != null && proxyQontractDataDir != null) {
            val dataDirFile = File(proxyQontractDataDir)
            if(!dataDirFile.exists()) {
                try {
                    dataDirFile.mkdirs()
                } catch (e: Throwable) {
                    exitWithMessage(exceptionCauseMessage(e))
                }
            } else {
                if(dataDirFile.listFiles()?.isNotEmpty() == true) {
                    exitWithMessage("This data directory $proxyQontractDataDir must be empty if it exists")
                }
            }
        }
    }

    private fun addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                try {
                    println("Shutting down stub servers")
                    proxy?.close()
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        })
    }
}
