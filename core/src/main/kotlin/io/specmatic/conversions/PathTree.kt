package io.specmatic.conversions

data class PathTree(val segment: String, val children: Map<String, PathTree> = emptyMap()) {

    fun insert(segments: List<String>): PathTree {
        if (segments.isEmpty()) return this
        val (head, tail) = segments.first() to segments.drop(1)

        val headNode = children.getOrElse(head) { PathTree(head) }
        val updatedHeadNode = headNode.insert(tail)
        return copy(children = children + (head to updatedHeadNode))
    }

    fun conflictsFor(path: String): Set<String> {
        val segments = path.split("/").filter(String::isNotEmpty)
        return conflictsFor(segments)
    }

    private fun conflictsFor(segments: List<String>): Set<String> {
        return when (segments.size) {
            1 -> children.keys.filterNot(::isParameter).toSet()
            else -> {
                val (head, tail) = segments.first() to segments.drop(1)
                return children[head]?.conflictsFor(tail).orEmpty()
            }
        }
    }

    private fun isParameter(segment: String): Boolean = segment.startsWith("{") && segment.endsWith("}")

    companion object {
        fun <T> from(paths: Map<String, T>): PathTree {
            val rootNode = PathTree("/")
            return paths.entries.fold(rootNode) { acc, (path, _) ->
                val segments = path.split("/").filter(String::isNotEmpty)
                acc.insert(segments)
            }
        }
    }
}
