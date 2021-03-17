package application

import picocli.CommandLine.*
import run.qontract.consoleLog
import run.qontract.core.APPLICATION_NAME_LOWER_CASE
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
    @Option(names = ["--target"], description = ["Base URL of the target to be proxied to"])
    var targetBaseURL: String = ""

    @Option(names = ["--host"], description = ["Host for the proxy"], defaultValue = "localhost")
    lateinit var host: String

    @Option(names = ["--port"], description = ["Port for the proxy"], defaultValue = "9000")
    var port: Int = 9000

    @Parameters(description = ["Store data from the proxy interactions into this dir"], index = "0")
    lateinit var proxyQontractDataDir: String

    @Option(names = ["--httpsKeyStore"], description = ["Run the proxy on https using a key in this store"])
    var keyStoreFile = ""

    @Option(names = ["--httpsKeyStoreDir"], description = ["Run the proxy on https, create a store named specmatic.jks in this directory"])
    var keyStoreDir = ""

    @Option(names = ["--httpsKeyStorePassword"], description = ["Run the proxy on https, password for pre-existing key store"])
    var keyStorePassword = "forgotten"

    @Option(names = ["--httpsKeyAlias"], description = ["Run the proxy on https using a key by this name"])
    var keyStoreAlias = "${APPLICATION_NAME_LOWER_CASE}proxy"

    @Option(names = ["--httpsPassword"], description = ["Key password if any"])
    var keyPassword = "forgotten"

    var proxy: Proxy? = null

    override fun call() {
        validatedProxySettings(targetBaseURL, proxyQontractDataDir)

        val certInfo = CertInfo(keyStoreFile, keyStoreDir, keyStorePassword, keyStoreAlias, keyPassword)
        val keyStoreData = certInfo.getHttpsCert()

        proxy = Proxy(host, port, targetBaseURL, proxyQontractDataDir, keyStoreData)
        addShutdownHook()

        val protocol = keyStoreData?.let { "https" } ?: "http"

        consoleLog("Proxy server is running on $protocol://$host:$port. Ctrl + C to stop.")
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
