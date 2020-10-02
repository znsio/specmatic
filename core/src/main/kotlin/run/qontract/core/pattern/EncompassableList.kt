package run.qontract.core.pattern

import run.qontract.core.Resolver

interface EncompassableList {
    fun getEncompassableList(count: Int, resolver: Resolver): List<Pattern>
    fun getEncompassableList(): MemberList
    fun isEndless(): Boolean
}