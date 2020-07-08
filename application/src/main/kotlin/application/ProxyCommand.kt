package application

import io.ktor.network.tls.certificates.generateCertificate
import picocli.CommandLine.*
import run.qontract.consoleLog
import run.qontract.core.KeyStoreData
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.core.utilities.exitWithMessage
import run.qontract.proxy.Proxy
import java.io.File
import java.lang.Thread.sleep
import java.security.KeyStore
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

    @Option(names = ["--httpsKeyStore"], description = ["EXPERIMENTAL: Run the proxy on https using a key in this store"])
    var keyStoreFile = ""

    @Option(names = ["--httpsKeyStoreDir"], description = ["EXPERIMENTAL: Run the proxy on https, create a store named qontract.jks in this directory"])
    var keyStoreDir = ""

    @Option(names = ["--httpsKeyStorePassword"], description = ["EXPERIMENTAL: Run the proxy on https, password for pre-existing key store"])
    var keyStorePassword = "forgotten"

    @Option(names = ["--httpsKeyAlias"], description = ["EXPERIMENTAL: Run the proxy on https using a key by this name"])
    var keyStoreAlias = "qontractproxy"

    @Option(names = ["--httpsPassword"], description = ["EXPERIMENTAL: Key password if any"])
    var keyPassword = "forgotten"

    @Parameters(description = ["Store data from the proxy interactions into this dir"], index = "0")
    lateinit var proxyQontractDataDir: String

    var proxy: Proxy? = null

    override fun call() {
        validatedProxySettings(targetBaseURL, proxyQontractDataDir)

        val keyStoreData = getHttpsCert(keyStoreFile, keyStoreDir, keyStorePassword, keyStoreAlias, keyPassword)
        proxy = Proxy(host, port, targetBaseURL, proxyQontractDataDir, keyStoreData)
        addShutdownHook()
        val protocol = if(keyStoreData != null) "https" else "http"
        consoleLog("Proxy server is running on ${protocol}://$host:$port. Ctrl + C to stop.")
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

fun getHttpsCert(keyStoreFile: String, keyStoreDir: String, keyStorePassword: String, keyAlias: String, keyPassword: String): KeyStoreData? {
    return when {
        keyStoreFile.isNotBlank() -> KeyStoreData(keyStore = loadKeyStoreFromFile(keyStoreFile, keyStorePassword), keyStorePassword = keyStorePassword, keyAlias = keyAlias, keyPassword = keyPassword)
        keyStoreDir.isNotBlank() -> createKeyStore(keyStoreDir, keyStorePassword, keyAlias, keyPassword)
        else -> null
    }
}

private fun createKeyStore(keyStoreDirPath: String, keyStorePassword: String, keyAlias: String, keyPassword: String): KeyStoreData {
    val keyStoreDir = File(keyStoreDirPath)
    if (!keyStoreDir.exists())
        keyStoreDir.mkdirs()

    val filename = "qontract.jks"
    val keyStoreFile = keyStoreDir.resolve(filename)
    if (keyStoreFile.exists())
        keyStoreFile.delete()

    val keyStore = generateCertificate(keyStoreFile, jksPassword = keyStorePassword, keyAlias = keyAlias, keyPassword = keyPassword)
    return KeyStoreData(keyStore = keyStore, keyStorePassword = keyStorePassword, keyAlias = keyAlias, keyPassword = keyPassword)
}

private fun loadKeyStoreFromFile(keyStoreFile: String, keyStorePassword: String): KeyStore {
    val certFilePath = File(keyStoreFile)
    val keyStoreType = when (certFilePath.extension.toLowerCase()) {
        "jks" -> "JKS"
        "pfx" -> "PKCS12"
        else -> exitWithMessage("The certificate file must be either in Java Key Store or PKCS12 format")
    }

    return KeyStore.getInstance(keyStoreType).apply {
        this.load(certFilePath.inputStream(), keyStorePassword.toCharArray())
    }
}
