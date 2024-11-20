package io.specmatic.core.overlay

data class Overlay(
    val updateMap: Map<String, List<Any?>>,
    val removalMap: Map<String, Boolean>
)