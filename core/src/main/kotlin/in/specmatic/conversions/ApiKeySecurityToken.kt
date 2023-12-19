package `in`.specmatic.conversions

import `in`.specmatic.core.APIKeySecuritySchemeConfiguration
import `in`.specmatic.core.SecuritySchemeConfiguration

data class ApiKeySecurityToken(
    private val securitySchemeConfiguration: SecuritySchemeConfiguration?,
    private val environmentVariable: String,
    private val environment: Environment
) : SecurityToken {
    override fun resolve(): String? {
        return environment.getEnvironmentVariable(environmentVariable) ?: securitySchemeConfiguration?.let {
            (it as APIKeySecuritySchemeConfiguration).value
        }
    }
}