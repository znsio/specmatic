package io.specmatic.core.discriminator

data class DiscriminatorBasedItem<T>(
    val discriminator: DiscriminatorMetadata,
    val value: T
) {
    val discriminatorProperty get() = discriminator.discriminatorProperty
    val discriminatorValue get() = discriminator.discriminatorValue
}