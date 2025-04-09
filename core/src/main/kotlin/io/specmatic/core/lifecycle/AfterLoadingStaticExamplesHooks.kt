package io.specmatic.core.lifecycle

import io.specmatic.core.Feature
import io.specmatic.mock.ScenarioStub

class AfterLoadingStaticExamplesHooks : AfterLoadingStaticExamples {
    private val afterLoadingStaticExamples: MutableList<AfterLoadingStaticExamples> = mutableListOf()

    fun register(hook: AfterLoadingStaticExamples) {
        afterLoadingStaticExamples.add(hook)
    }

    override fun call(examplesUsedFor: ExamplesUsedFor, examples: List<Pair<Feature, List<ScenarioStub>>>) {
        afterLoadingStaticExamples.forEach {
            it.call(examplesUsedFor, examples)
        }
    }
}