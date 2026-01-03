package com.nendo.argosy.util

import com.nendo.argosy.data.local.dao.SearchCandidate
import kotlin.math.min

object FuzzySearch {

    data class ScoredResult(
        val candidate: SearchCandidate,
        val score: Float
    )

    fun search(
        query: String,
        candidates: List<SearchCandidate>,
        limit: Int = 10,
        minScore: Float = 0.3f
    ): List<SearchCandidate> {
        if (query.isBlank()) return emptyList()

        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (queryWords.isEmpty()) return emptyList()

        return candidates
            .map { candidate -> ScoredResult(candidate, scoreTitle(queryWords, candidate.title)) }
            .filter { it.score >= minScore }
            .sortedWith(
                compareByDescending<ScoredResult> { it.score }
                    .thenByDescending { it.candidate.rating ?: 0f }
                    .thenBy { it.candidate.title.lowercase() }
            )
            .take(limit)
            .map { it.candidate }
    }

    private fun scoreTitle(queryWords: List<String>, title: String): Float {
        val titleWords = title.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (titleWords.isEmpty()) return 0f

        var totalScore = 0f
        var lastMatchIndex = -1
        var matchedCount = 0
        var inOrderCount = 0

        for (queryWord in queryWords) {
            var bestScore = 0f
            var bestIndex = -1

            for ((index, titleWord) in titleWords.withIndex()) {
                val score = wordMatchScore(queryWord, titleWord)
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = index
                }
            }

            if (bestScore > 0f) {
                totalScore += bestScore
                matchedCount++

                if (bestIndex > lastMatchIndex) {
                    inOrderCount++
                }
                lastMatchIndex = bestIndex
            }
        }

        if (matchedCount == 0) return 0f

        val coverageRatio = matchedCount.toFloat() / queryWords.size
        val orderBonus = if (matchedCount > 1 && inOrderCount == matchedCount) 0.2f else 0f

        return (totalScore / queryWords.size) * coverageRatio + orderBonus
    }

    private fun wordMatchScore(queryWord: String, titleWord: String): Float {
        if (queryWord.isEmpty() || titleWord.isEmpty()) return 0f

        return when {
            titleWord == queryWord -> 1.0f
            titleWord.startsWith(queryWord) -> 0.85f
            queryWord.length >= 3 && titleWord.contains(queryWord) -> 0.7f
            isSubsequence(queryWord, titleWord) -> 0.65f
            else -> {
                val distance = levenshteinDistance(queryWord, titleWord)
                val maxAllowedDistance = when {
                    queryWord.length <= 3 -> 1
                    queryWord.length <= 5 -> 2
                    else -> 3
                }
                if (distance <= maxAllowedDistance) {
                    (1f - (distance.toFloat() / (maxAllowedDistance + 1))) * 0.6f
                } else {
                    0f
                }
            }
        }
    }

    private fun isSubsequence(query: String, target: String): Boolean {
        if (query.length > target.length) return false
        var queryIndex = 0
        for (char in target) {
            if (queryIndex < query.length && char == query[queryIndex]) {
                queryIndex++
            }
        }
        return queryIndex == query.length
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        if (m == 0) return n
        if (n == 0) return m

        var prevRow = IntArray(n + 1) { it }
        var currRow = IntArray(n + 1)

        for (i in 1..m) {
            currRow[0] = i
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                currRow[j] = min(
                    min(currRow[j - 1] + 1, prevRow[j] + 1),
                    prevRow[j - 1] + cost
                )
            }
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }

        return prevRow[n]
    }
}
