package application

import io.specmatic.core.utilities.StubServerWatcher
import org.springframework.stereotype.Component

@Component
class WatchMaker {
    fun make(paths: List<String>): StubServerWatcher = StubServerWatcher(paths)
}
