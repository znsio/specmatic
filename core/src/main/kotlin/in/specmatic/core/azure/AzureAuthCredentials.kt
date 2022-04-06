package `in`.specmatic.core.azure

import `in`.specmatic.core.git.getBearerToken
import `in`.specmatic.core.git.getPersonalAccessToken

object AzureAuthCredentials: AuthCredentials {
    override fun gitCommandAuthHeaders(): List<String> {
        val azurePAT: String? = getPersonalAccessToken()

        if(azurePAT != null) {
            return listOf("-c", "http.extraHeader=Authorization: Basic ${PersonalAccessToken(azurePAT).basic()}")
        }

        val bearer: String? = getBearerToken()
        if (bearer != null) {
            return listOf("-c", "http.extraHeader=Authorization: Bearer $bearer")
        }

        return emptyList()
    }
}