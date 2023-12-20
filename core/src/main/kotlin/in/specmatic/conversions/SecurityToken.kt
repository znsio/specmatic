package `in`.specmatic.conversions

import `in`.specmatic.core.APIKeySecuritySchemeConfiguration
import `in`.specmatic.core.SecuritySchemeConfiguration
import `in`.specmatic.core.SecuritySchemeWithOAuthToken

fun getSecurityTokenForBearerScheme(
    securitySchemeConfiguration: SecuritySchemeConfiguration?,
    environmentVariable: String,
    environment: Environment
): String? {
    return environment.getEnvironmentVariable(environmentVariable) ?: securitySchemeConfiguration?.let {
        (it as SecuritySchemeWithOAuthToken).token
    }
}

fun getSecurityTokenForApiKeyScheme(
    securitySchemeConfiguration: SecuritySchemeConfiguration?,
    environmentVariable: String,
    environment: Environment
): String? {
    return environment.getEnvironmentVariable(environmentVariable) ?: securitySchemeConfiguration?.let {
        (it as APIKeySecuritySchemeConfiguration).value
    }
}