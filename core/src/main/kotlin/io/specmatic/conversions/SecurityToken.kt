package io.specmatic.conversions

import io.specmatic.core.APIKeySecuritySchemeConfiguration
import io.specmatic.core.BasicAuthSecuritySchemeConfiguration
import io.specmatic.core.SecuritySchemeConfiguration
import io.specmatic.core.SecuritySchemeWithOAuthToken
import io.specmatic.core.utilities.Flags.Companion.getStringValue

@Deprecated("This will be deprecated shortly.Use the security scheme name as the environment variable.")
const val SPECMATIC_OAUTH2_TOKEN = "SPECMATIC_OAUTH2_TOKEN"

const val SPECMATIC_BASIC_AUTH_TOKEN = "SPECMATIC_BASIC_AUTH_TOKEN"

fun getSecurityTokenForBasicAuthScheme(
    securitySchemeConfiguration: SecuritySchemeConfiguration?,
    environmentVariable: String,
): String? {
    return getStringValue(environmentVariable) ?: securitySchemeConfiguration?.let {
        (securitySchemeConfiguration as BasicAuthSecuritySchemeConfiguration).token
    } ?: getStringValue(SPECMATIC_BASIC_AUTH_TOKEN)
}

fun getSecurityTokenForBearerScheme(
    securitySchemeConfiguration: SecuritySchemeConfiguration?,
    environmentVariable: String,
): String? {
    return getStringValue(environmentVariable) ?: securitySchemeConfiguration?.let {
        (it as SecuritySchemeWithOAuthToken).token
    } ?: getStringValue(SPECMATIC_OAUTH2_TOKEN)
}

fun getSecurityTokenForApiKeyScheme(
    securitySchemeConfiguration: SecuritySchemeConfiguration?,
    environmentVariable: String,
): String? {
    return getStringValue(environmentVariable) ?: securitySchemeConfiguration?.let {
        (it as APIKeySecuritySchemeConfiguration).value
    }
}