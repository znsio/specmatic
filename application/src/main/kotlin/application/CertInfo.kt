package application

import io.ktor.network.tls.certificates.*
import run.qontract.core.APPLICATION_NAME_LOWER_CASE
import run.qontract.core.KeyData
import run.qontract.core.utilities.exitWithMessage
import java.io.File
import java.security.KeyStore

data class CertInfo(val keyStoreFile: String = "", val keyStoreDir: String = "", val keyStorePassword: String = "forgotten", val keyStoreAlias: String = "${APPLICATION_NAME_LOWER_CASE}proxy", val keyPassword: String = "forgotten") {
    fun getHttpsCert(): KeyData? {
        return when {
            keyStoreFile.isNotBlank() -> KeyData(keyStore = loadKeyStoreFromFile(keyStoreFile, keyStorePassword), keyStorePassword = keyStorePassword, keyAlias = keyStoreAlias, keyPassword = keyPassword)
            keyStoreDir.isNotBlank() -> createKeyStore(keyStoreDir, keyStorePassword, keyStoreAlias, keyPassword)
            else -> null
        }
    }
}

private fun createKeyStore(keyStoreDirPath: String, keyStorePassword: String, keyAlias: String, keyPassword: String): KeyData {
    val keyStoreDir = File(keyStoreDirPath)
    if (!keyStoreDir.exists())
        keyStoreDir.mkdirs()

    val filename = "$APPLICATION_NAME_LOWER_CASE.jks"
    val keyStoreFile = keyStoreDir.resolve(filename)
    if (keyStoreFile.exists())
        keyStoreFile.delete()

    val keyStore = generateCertificate(keyStoreFile, jksPassword = keyStorePassword, keyAlias = keyAlias, keyPassword = keyPassword)
    return KeyData(keyStore = keyStore, keyStorePassword = keyStorePassword, keyAlias = keyAlias, keyPassword = keyPassword)
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
