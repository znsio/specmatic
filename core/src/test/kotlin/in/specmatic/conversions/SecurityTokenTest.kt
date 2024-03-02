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
        val securityToken = getSecurityTokenForBearerScheme(BearerSecuritySchemeConfiguration("bearer", token), "BearerAuth", EnvironmentAndPropertiesConfiguration())
        assertThat(securityToken).isEqualTo(token)
    }

    @Test
    fun `should extract security token for bearer security scheme from environment variable`() {
        val token = "BEARER1234"
        val testEnvironmentAndPropertiesConfiguration = mockk<EnvironmentAndPropertiesConfiguration>()
        val schemeName = "BearerAuth"
        every { testEnvironmentAndPropertiesConfiguration.getEnvironmentVariable(schemeName) }.returns(token)
        val securityToken = getSecurityTokenForBearerScheme( null, schemeName, testEnvironmentAndPropertiesConfiguration)
        assertThat(securityToken).isEqualTo(token)
    }

    @Test
    fun `should pick up the security token from environment variable over the one defined in the configuration for bearer security scheme `() {
        val envToken = "ENV1234"
        val configToken = "CONFIG1234"
        val testEnvironmentAndPropertiesConfiguration = mockk<EnvironmentAndPropertiesConfiguration>()
        val schemeName = "BearerAuth"
        every { testEnvironmentAndPropertiesConfiguration.getEnvironmentVariable(schemeName) }.returns(envToken)
        val securityToken = getSecurityTokenForBearerScheme(BearerSecuritySchemeConfiguration("bearer", configToken),
            schemeName, testEnvironmentAndPropertiesConfiguration)
        assertThat(securityToken).isEqualTo(envToken)
    }

    @Test
    fun `should extract security token for oauth2 security scheme from configuration`() {
        val token = "OAUTH1234"
        val securityToken = getSecurityTokenForBearerScheme(OAuth2SecuritySchemeConfiguration("oauth2", token), "oAuth2AuthCode", EnvironmentAndPropertiesConfiguration())
        assertThat(securityToken).isEqualTo(token)
    }

    @Test
    fun `should extract security token for oauth2 security scheme from environment variable`() {
        val token = "OAUTH1234"
        val testEnvironmentAndPropertiesConfiguration = mockk<EnvironmentAndPropertiesConfiguration>()
        val schemeName = "oAuth2AuthCode"
        every { testEnvironmentAndPropertiesConfiguration.getEnvironmentVariable(schemeName) }.returns(token)
        val securityToken = getSecurityTokenForBearerScheme(null,
            schemeName, testEnvironmentAndPropertiesConfiguration)
        assertThat(securityToken).isEqualTo(token)
    }

    @Test
    fun `should pick up the security token from environment variable over the one defined in the configuration for oauth2 security scheme `() {
        val envToken = "ENV1234"
        val configToken = "CONFIG1234"
        val testEnvironmentAndPropertiesConfiguration = mockk<EnvironmentAndPropertiesConfiguration>()
        val schemeName = "oAuth2AuthCode"
        every { testEnvironmentAndPropertiesConfiguration.getEnvironmentVariable(schemeName) }.returns(envToken)
        val securityToken = getSecurityTokenForBearerScheme(OAuth2SecuritySchemeConfiguration("oauth2", configToken),
            schemeName, testEnvironmentAndPropertiesConfiguration)
        assertThat(securityToken).isEqualTo(envToken)
    }

    @Test
    fun `should pick up the security token from the SPECMATIC_OAUTH2_TOKEN environment variable as a fallback for bearer security scheme`() {
        val envToken = "ENV1234"
        val testEnvironmentAndPropertiesConfiguration = mockk<EnvironmentAndPropertiesConfiguration>()
        val schemeName = "oAuth2AuthCode"
        every { testEnvironmentAndPropertiesConfiguration.getEnvironmentVariable(schemeName) }.returns(null)
        every { testEnvironmentAndPropertiesConfiguration.getEnvironmentVariable(SPECMATIC_OAUTH2_TOKEN) }.returns(envToken)
        val securityToken = getSecurityTokenForBearerScheme(null,
            schemeName, testEnvironmentAndPropertiesConfiguration)
        assertThat(securityToken).isEqualTo(envToken)
    }

    @Test
    fun `should extract security token for apikey security scheme from configuration`() {
        val token = "APIKEY1234"
        val securityToken = getSecurityTokenForApiKeyScheme(APIKeySecuritySchemeConfiguration("apiKey", token), "ApiKeyAuthHeader", EnvironmentAndPropertiesConfiguration())
        assertThat(securityToken).isEqualTo(token)
    }

    @Test
    fun `should extract security token for apikey security scheme from environment variable`() {
        val token = "APIKEY1234"
        val testEnvironmentAndPropertiesConfiguration = mockk<EnvironmentAndPropertiesConfiguration>()
        val schemeName = "ApiKeyAuthHeader"
        every { testEnvironmentAndPropertiesConfiguration.getEnvironmentVariable(schemeName) }.returns(token)
        val securityToken = getSecurityTokenForApiKeyScheme(null, schemeName, testEnvironmentAndPropertiesConfiguration)
        assertThat(securityToken).isEqualTo(token)
    }

    @Test
    fun `should pick up the security token from environment variable over the one defined in the configuration for apikey security scheme `() {
        val envToken = "ENV1234"
        val configToken = "CONFIG1234"
        val testEnvironmentAndPropertiesConfiguration = mockk<EnvironmentAndPropertiesConfiguration>()
        val schemeName = "ApiKeyAuthHeader"
        every { testEnvironmentAndPropertiesConfiguration.getEnvironmentVariable(schemeName) }.returns(envToken)
        val securityToken = getSecurityTokenForApiKeyScheme(APIKeySecuritySchemeConfiguration("apikey", configToken),
            schemeName, testEnvironmentAndPropertiesConfiguration)
        assertThat(securityToken).isEqualTo(envToken)
    }
}