package io.specmatic.stub

import io.specmatic.core.Context
import io.specmatic.core.HttpRequest

data class RequestContext(val httpRequest: HttpRequest) : Context