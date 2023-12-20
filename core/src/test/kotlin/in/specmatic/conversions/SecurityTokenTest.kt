package `in`.specmatic.conversions

import `in`.specmatic.core.APIKeySecuritySchemeConfiguration
import `in`.specmatic.core.BearerSecuritySchemeConfiguration
import `in`.specmatic.core.OAuth2SecuritySchemeConfiguration
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SecurityTokenTest {

    @Test
    fun `should extract security token for bearer security scheme from configuration`() {
        val token = "BEARER1234"
        val securityToken = getSecurityTokenForBearerScheme(BearerSecuritySchemeConfiguration("bearer", token), "BearerAuth", DefaultEnvironment())
        assertThat(securityToken).isEqualTo(token)
    }

    @Test
    fun `should extract security token for bearer security scheme from environment variable`() {
        val token = "BEARER1234"
        val testEnvironment = mockk<Environment>()
        val schemeName = "BearerAuth"
        every { testEnvironment.getEnvironmentVariable(schemeName) }.returns(token)
        val securityToken = getSecurityTokenForBearerScheme( null, schemeName, testEnvironment)
        assertThat(securityToken).isEqualTo(token)
    }

    @Test
    fun `should pick up the security token from environment variable over the one defined in the configuration for bearer security scheme `() {
        val envToken = "ENV1234"
        val configToken = "CONFIG1234"
        val testEnvironment = mockk<Environment>()
        val schemeName = "BearerAuth"
        every { testEnvironment.getEnvironmentVariable(schemeName) }.returns(envToken)
        val securityToken = getSecurityTokenForBearerScheme(BearerSecuritySchemeConfiguration("bearer", configToken),
            schemeName, testEnvironment)
        assertThat(securityToken).isEqualTo(envToken)
    }

    @Test
    fun `should extract security token for oauth2 security scheme from configuration`() {
        val token = "OAUTH1234"
        val securityToken = getSecurityTokenForBearerScheme(OAuth2SecuritySchemeConfiguration("oauth2", token), "oAuth2AuthCode", DefaultEnvironment())
        assertThat(securityToken).isEqualTo(token)
    }

    @Test
    fun `should extract security token for oauth2 security scheme from environment variable`() {
        val token = "OAUTH1234"
        val testEnvironment = mockk<Environment>()
        val schemeName = "oAuth2AuthCode"
        every { testEnvironment.getEnvironmentVariable(schemeName) }.returns(token)
        val securityToken = getSecurityTokenForBearerScheme(null,
            schemeName, testEnvironment)
        assertThat(securityToken).isEqualTo(token)
    }

    @Test
    fun `should pick up the security token from environment variable over the one defined in the configuration for oauth2 security scheme `() {
        val envToken = "ENV1234"
        val configToken = "CONFIG1234"
        val testEnvironment = mockk<Environment>()
        val schemeName = "oAuth2AuthCode"
        every { testEnvironment.getEnvironmentVariable(schemeName) }.returns(envToken)
        val securityToken = getSecurityTokenForBearerScheme(OAuth2SecuritySchemeConfiguration("oauth2", configToken),
            schemeName, testEnvironment)
        assertThat(securityToken).isEqualTo(envToken)
    }

    @Test
    fun `should extract security token for apikey security scheme from configuration`() {
        val token = "APIKEY1234"
        val securityToken = getSecurityTokenForApiKeyScheme(APIKeySecuritySchemeConfiguration("apiKey", token), "ApiKeyAuthHeader", DefaultEnvironment())
        assertThat(securityToken).isEqualTo(token)
    }

    @Test
    fun `should extract security token for apikey security scheme from environment variable`() {
        val token = "APIKEY1234"
        val testEnvironment = mockk<Environment>()
        val schemeName = "ApiKeyAuthHeader"
        every { testEnvironment.getEnvironmentVariable(schemeName) }.returns(token)
        val securityToken = getSecurityTokenForApiKeyScheme(null, schemeName, testEnvironment)
        assertThat(securityToken).isEqualTo(token)
    }

    @Test
    fun `should pick up the security token from environment variable over the one defined in the configuration for apikey security scheme `() {
        val envToken = "ENV1234"
        val configToken = "CONFIG1234"
        val testEnvironment = mockk<Environment>()
        val schemeName = "ApiKeyAuthHeader"
        every { testEnvironment.getEnvironmentVariable(schemeName) }.returns(envToken)
        val securityToken = getSecurityTokenForApiKeyScheme(APIKeySecuritySchemeConfiguration("apikey", configToken),
            schemeName, testEnvironment)
        assertThat(securityToken).isEqualTo(envToken)
    }
}