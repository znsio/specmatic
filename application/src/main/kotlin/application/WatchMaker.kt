package application

import org.springframework.stereotype.Component

@Component
class WatchMaker {
    fun make(paths: List<String>): Watcher = Watcher(paths)
}
