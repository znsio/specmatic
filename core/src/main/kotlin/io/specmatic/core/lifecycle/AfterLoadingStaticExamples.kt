package io.specmatic.core.lifecycle

import io.specmatic.core.Feature
import io.specmatic.core.Result
import io.specmatic.mock.ScenarioStub

fun interface AfterLoadingStaticExamples {
    fun call(examplesUsedFor: ExamplesUsedFor, examples: List<Pair<Feature, List<ScenarioStub>>>): Result
}