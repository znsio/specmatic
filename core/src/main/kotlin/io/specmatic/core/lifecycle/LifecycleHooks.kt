package io.specmatic.core.lifecycle

object LifecycleHooks {
    var afterLoadingStaticExamples: AfterLoadingStaticExamples = AfterLoadingStaticExamples { _, _ ->
            // No-op
        }
}