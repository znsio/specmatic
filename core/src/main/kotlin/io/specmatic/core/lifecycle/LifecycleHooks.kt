package io.specmatic.core.lifecycle

import io.specmatic.core.Feature
import io.specmatic.mock.ScenarioStub

object LifecycleHooks {
    var afterLoadingStaticExamples: AfterLoadingStaticExamples = object : AfterLoadingStaticExamples {
        override fun call(examplesUsedFor: ExamplesUsedFor, examples: List<Pair<Feature, List<ScenarioStub>>>) {
            // No-op
        }
    }
}