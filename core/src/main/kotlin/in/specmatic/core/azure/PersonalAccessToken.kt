package `in`.specmatic.core.azure

import java.util.*

data class PersonalAccessToken(private val token: String): AzureAuthToken {
    override fun basic(): String {
        return String(Base64.getEncoder().encode("$token:".encodeToByteArray()))
    }
}