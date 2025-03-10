package io.specmatic.core.log

interface UsesBoundaryWithHelpers : UsesBoundary {
    fun removeBoundary(): Boolean { return false }
}