package application

import io.specmatic.core.utilities.StubServerWatcher

class WatchMaker {
    fun make(paths: List<String>): StubServerWatcher = StubServerWatcher(paths)
}
