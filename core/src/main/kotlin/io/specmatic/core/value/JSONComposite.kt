package io.specmatic.core.value

import io.specmatic.core.Resolver
import io.specmatic.core.Result

sealed interface JSONComposite {
    fun checkIfAllRootLevelKeysAreAttributeSelected(attributeSelectedFields: Set<String>, resolver: Resolver): Result
}
