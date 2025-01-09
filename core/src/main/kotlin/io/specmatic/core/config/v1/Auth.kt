package io.specmatic.core.config.v1

import com.fasterxml.jackson.annotation.JsonProperty

data class Auth(
    @JsonProperty("bearer-file") val bearerFile: String = "bearer.txt",
    @JsonProperty("bearer-environment-variable") val bearerEnvironmentVariable: String? = null
)