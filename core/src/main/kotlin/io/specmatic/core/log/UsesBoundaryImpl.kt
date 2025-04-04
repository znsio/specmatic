package io.specmatic.core.log

class UsesBoundaryImpl : UsesBoundaryWithHelpers {
    var boundary: Boolean = false

    override fun boundary() {
        boundary = true
    }

    override fun removeBoundary(): Boolean {
        val oldBoundary = boundary
        boundary = false
        return oldBoundary
    }

}