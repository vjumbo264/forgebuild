package com.forgebuild.taskflow.data

/** Fractional/lexo-style ranking: insert between any two ranks without renumbering. */
object PriorityRank {
    const val START = 1000.0
    const val STEP = 1000.0
    /** Below this gap we rebalance that level in the background. */
    const val MIN_GAP = 1.0 / (1 shl 20)

    fun between(before: Double?, after: Double?): Double = when {
        before == null && after == null -> START
        before == null -> (after!!) / 2.0
        after == null -> before + STEP
        else -> (before + after) / 2.0
    }
}
