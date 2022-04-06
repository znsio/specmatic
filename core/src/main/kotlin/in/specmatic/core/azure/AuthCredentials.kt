package `in`.specmatic.core.azure

interface AuthCredentials {
    fun gitCommandAuthHeaders(): List<String>
}