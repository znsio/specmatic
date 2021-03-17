package `in`.specmatic.core

import java.security.KeyStore

data class KeyData(val keyStore: KeyStore, val keyStorePassword: String, val keyAlias: String = "", val keyPassword: String = "")
