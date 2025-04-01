package io.specmatic.core

import io.specmatic.mock.ScenarioStub

enum class ExamplesUsedFor {
    Test, Stub, Validation
}

interface AfterLoadingStaticExamples {
    fun call(examplesUsedFor: ExamplesUsedFor, examples: List<Pair<Feature, List<ScenarioStub>>>)
}

object LifecycleHooks {
    var afterLoadingStaticExamples: AfterLoadingStaticExamples = object : AfterLoadingStaticExamples {
        override fun call(examplesUsedFor: ExamplesUsedFor, examples: List<Pair<Feature, List<ScenarioStub>>>) {
            // No-op
        }
    }
}