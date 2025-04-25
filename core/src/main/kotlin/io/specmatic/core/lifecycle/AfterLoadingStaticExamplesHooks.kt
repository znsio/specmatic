package io.specmatic.core.lifecycle

import io.specmatic.core.Feature
import io.specmatic.core.Result
import io.specmatic.mock.ScenarioStub

class AfterLoadingStaticExamplesHooks : AfterLoadingStaticExamples {
    private val afterLoadingStaticExamples: MutableList<AfterLoadingStaticExamples> = mutableListOf()

    fun register(hook: AfterLoadingStaticExamples) {
        afterLoadingStaticExamples.add(hook)
    }

    fun remove(hook: AfterLoadingStaticExamples) {
        afterLoadingStaticExamples.remove(hook)
    }

    override fun call(examplesUsedFor: ExamplesUsedFor, examples: List<Pair<Feature, List<ScenarioStub>>>): Result {
        val results = afterLoadingStaticExamples.map {
            it.call(examplesUsedFor, examples)
        }
        return Result.fromResults(results)
    }
}