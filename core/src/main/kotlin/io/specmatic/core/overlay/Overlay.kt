package io.specmatic.core.overlay

data class Overlay(
    val updateMap: Map<String, Any?>,
    val removalMap: Map<String, Boolean>
)