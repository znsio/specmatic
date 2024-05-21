package application

import picocli.CommandLine.*
import `in`.specmatic.core.APPLICATION_NAME_LOWER_CASE
import `in`.specmatic.core.Configuration.Companion.DEFAULT_PROXY_HOST
import `in`.specmatic.core.Configuration.Companion.DEFAULT_PROXY_PORT
import `in`.specmatic.core.log.*
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.utilities.exitWithMessage
import `in`.specmatic.proxy.Proxy
import java.io.File
import java.lang.Thread.sleep
import java.util.concurrent.Callable

@Command(name = "proxy",
        mixinStandardHelpOptions = true,
        description = ["Proxies requests to the specified target and converts the result into contracts and stubs"])
class ProxyCommand : Callable<Unit> {
    @Option(names = ["--target"], description = ["Base URL of the target to proxy"])
    var targetBaseURL: String = ""

    @Option(names = ["--host"], description = ["Host for the proxy"], defaultValue = DEFAULT_PROXY_HOST)
    lateinit var host: String

    @Option(names = ["--port"], description = ["Port for the proxy"], defaultValue = DEFAULT_PROXY_PORT)
    var port: Int = 9000

    @Parameters(description = ["Store data from the proxy interactions into this dir"], index = "0")
    lateinit var proxySpecmaticDataDir: String

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

    @Option(names = ["--debug"], description = ["Write verbose logs to console for debugging"])
    var debugLog = false

    var proxy: Proxy? = null

    override fun call() {
        if(debugLog)
            logger = Verbose()

        validatedProxySettings(targetBaseURL, proxySpecmaticDataDir)

        val certInfo = CertInfo(keyStoreFile, keyStoreDir, keyStorePassword, keyStoreAlias, keyPassword)
        val keyStoreData = certInfo.getHttpsCert()

        proxy = Proxy(host, port, targetBaseURL, proxySpecmaticDataDir, keyStoreData)
        addShutdownHook()

        val protocol = keyStoreData?.let { "https" } ?: "http"

        consoleLog(StringLog("Proxy server is running on $protocol://$host:$port. Ctrl + C to stop."))
        while(true) sleep(10000)
    }

    private fun validatedProxySettings(unknownProxyTarget: String?, proxySpecmaticDataDir: String?) {
        if(unknownProxyTarget == null && proxySpecmaticDataDir == null) return

        if(unknownProxyTarget != null && proxySpecmaticDataDir != null) {
            val dataDirFile = File(proxySpecmaticDataDir)
            if(!dataDirFile.exists()) {
                try {
                    dataDirFile.mkdirs()
                } catch (e: Throwable) {
                    exitWithMessage(exceptionCauseMessage(e))
                }
            } else {
                if(dataDirFile.listFiles()?.isNotEmpty() == true) {
                    exitWithMessage("This data directory $proxySpecmaticDataDir must be empty if it exists")
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
                } catch (e: Throwable) {
                    logger.log(e)
                }
            }
        })
    }
}
