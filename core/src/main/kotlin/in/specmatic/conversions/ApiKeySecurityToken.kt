package `in`.specmatic.conversions

import `in`.specmatic.core.APIKeySecuritySchemeConfiguration
import `in`.specmatic.core.SecuritySchemeConfiguration

data class ApiKeySecurityToken(
    private val securitySchemeConfiguration: SecuritySchemeConfiguration?,
    private val environment: Environment = DefaultEnvironment()
) : SecurityToken {
    override fun resolve(): String? {
        return securitySchemeConfiguration?.let {
            (it as APIKeySecuritySchemeConfiguration).value
        } ?: environment.getEnvironmentVariable(SPECMATIC_API_KEY)
    }
}