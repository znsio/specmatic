package io.specmatic.core.azure

object NoGitAuthCredentials: AuthCredentials {
    override fun gitCommandAuthHeaders(): List<String> {
        return emptyList()
    }
}