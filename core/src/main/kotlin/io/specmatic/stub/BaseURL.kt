package io.specmatic.stub

import io.specmatic.core.Configuration
import io.specmatic.core.Feature
import io.specmatic.core.log.logger
import io.specmatic.core.utilities.findFreePort

data class BaseURL(val value: String, private val isDefault: Boolean) {

    fun getBaseUrlFor(feature: Feature, specToBaseUrlMap: Map<String, String?> = emptyMap()): String {
        val baseUrlFromConfig = specToBaseUrlMap[feature.path]
        if (baseUrlFromConfig != null) {
            BaseURLSource.FROM_CONFIG.logSource(baseUrlFromConfig, feature.path)
            return baseUrlFromConfig
        }

        if (!isDefault) {
            BaseURLSource.FROM_ARGUMENTS.logSource(value, feature.path)
            return value
        }

        val preferredBaseUrl = feature.getPreferredServer()
        val (source, baseUrl) = when {
            preferredBaseUrl != null -> BaseURLSource.FROM_SERVERS to preferredBaseUrl
            else -> BaseURLSource.DEFAULT to value
        }

        source.logSource(baseUrl, feature.path)
        return baseUrl
    }

    companion object {
        fun from(value: String): BaseURL = BaseURL(value, isDefault = false)
        fun default(): BaseURL {
            val portTouse = findFreePort(Configuration.DEFAULT_HTTP_STUB_HOST, Configuration.DEFAULT_HTTP_STUB_PORT)
            return BaseURL(
                value = "${Configuration.DEFAULT_HTTP_STUB_SCHEME}://${Configuration.DEFAULT_HTTP_STUB_HOST}:$portTouse",
                isDefault = true
            )
        }

        fun BaseURL?.orDefault(): BaseURL = this ?: default()
    }
}

private enum class BaseURLSource(val description: String) {
    FROM_CONFIG("Specmatic Config"),
    FROM_ARGUMENTS("Arguments"),
    FROM_SERVERS("OpenAPI servers"),
    DEFAULT("Default BaseURL");

    fun logSource(baseUrl: String, openApiSpecPath: String) {
        logger.log("Using base URL: \"$baseUrl\" from $description for OpenAPI Specification at \"$openApiSpecPath\"")
    }
}
