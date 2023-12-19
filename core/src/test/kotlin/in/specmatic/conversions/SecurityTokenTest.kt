package `in`.specmatic.conversions

import `in`.specmatic.core.APIKeySecuritySchemeConfiguration
import `in`.specmatic.core.BearerSecuritySchemeConfiguration
import `in`.specmatic.core.OAuth2SecuritySchemeConfiguration
import io.mockk.every
import io.mockk.mockk
import io.swagger.v3.oas.models.security.SecurityScheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SecurityTokenTest {

    @Test
    fun `should extract security token for bearer security scheme from configuration`() {
        val token = "BEARER1234"
        val securityToken = BearerSecurityToken(BEARER_SECURITY_SCHEME, BearerSecuritySchemeConfiguration("bearer", token), SPECMATIC_BEARER_TOKEN)
        assertThat(securityToken.resolve()).isEqualTo(token)
    }

    @Test
    fun `should extract security token for bearer security scheme from environment variable`() {
        val token = "BEARER1234"
        val testEnvironment = mockk<Environment>()
        every { testEnvironment.getEnvironmentVariable(SPECMATIC_BEARER_TOKEN) }.returns(token)
        val securityToken = BearerSecurityToken(BEARER_SECURITY_SCHEME, null, SPECMATIC_BEARER_TOKEN, testEnvironment)
        assertThat(securityToken.resolve()).isEqualTo(token)
    }

    @Test
    fun `should extract security token for oauth2 security scheme from configuration`() {
        val token = "OAUTH1234"
        val securityToken = BearerSecurityToken(SecurityScheme.Type.OAUTH2.toString(), OAuth2SecuritySchemeConfiguration("oauth2", token), SPECMATIC_OAUTH2_TOKEN)
        assertThat(securityToken.resolve()).isEqualTo(token)
    }

    @Test
    fun `should extract security token for oauth2 security scheme from environment variable`() {
        val token = "OAUTH1234"
        val testEnvironment = mockk<Environment>()
        every { testEnvironment.getEnvironmentVariable(SPECMATIC_OAUTH2_TOKEN) }.returns(token)
        val securityToken = BearerSecurityToken(SecurityScheme.Type.OAUTH2.toString(), null, SPECMATIC_OAUTH2_TOKEN, testEnvironment)
        assertThat(securityToken.resolve()).isEqualTo(token)
    }

    @Test
    fun `should extract security token for apikey security scheme from configuration`() {
        val token = "APIKEY1234"
        val securityToken = ApiKeySecurityToken(APIKeySecuritySchemeConfiguration("apiKey", token))
        assertThat(securityToken.resolve()).isEqualTo(token)
    }

    @Test
    fun `should extract security token for apikey security scheme from environment variable`() {
        val token = "APIKEY1234"
        val testEnvironment = mockk<Environment>()
        every { testEnvironment.getEnvironmentVariable(SPECMATIC_API_KEY) }.returns(token)
        val securityToken = ApiKeySecurityToken(null, testEnvironment)
        assertThat(securityToken.resolve()).isEqualTo(token)
    }
}