package io.specmatic.core

interface Report {
    override fun toString(): String
    fun toText(): String
}