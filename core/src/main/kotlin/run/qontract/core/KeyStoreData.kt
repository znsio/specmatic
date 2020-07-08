package run.qontract.core

import java.security.KeyStore

data class KeyStoreData(val keyStore: KeyStore, val keyStorePassword: String, val keyAlias: String = "", val keyPassword: String = "")