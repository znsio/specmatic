package io.specmatic.core.log

interface UsesBoundary {
    fun boundary() {}
    fun removeBoundary(): Boolean { return false }
}