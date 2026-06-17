package com.nendo.argosy.libretro

import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.local.entity.HotkeyScopeType

sealed interface HotkeyBindWarning {
    data class OverridesLowerTier(val shadowedAction: HotkeyAction, val scopeLabel: String) : HotkeyBindWarning
    data class ShadowedByHigherTier(val scopeLabel: String) : HotkeyBindWarning
    data class SameTierConflict(val action: HotkeyAction) : HotkeyBindWarning
}

object HotkeyScopeResolver {

    private fun tierRank(type: HotkeyScopeType): Int = when (type) {
        HotkeyScopeType.GLOBAL -> 0
        HotkeyScopeType.PLATFORM -> 1
        HotkeyScopeType.CORE -> 2
    }

    /** GLOBAL + PLATFORM(slug) + CORE(coreId); highest tier wins per combo+controller. */
    fun resolve(
        all: List<HotkeyEntity>,
        platformSlug: String?,
        coreId: String?,
        parseCombo: (HotkeyEntity) -> List<Int>
    ): List<HotkeyEntity> {
        val applicable = all.filter { isApplicable(it, platformSlug, coreId) }
        return applicable
            .groupBy { HotkeyManager.canonicalizeCombo(parseCombo(it)) to it.controllerId }
            .flatMap { (_, group) ->
                val topTier = group.maxOf { tierRank(it.scopeType) }
                group.filter { tierRank(it.scopeType) == topTier }
            }
    }

    private fun isApplicable(entity: HotkeyEntity, platformSlug: String?, coreId: String?): Boolean =
        when (entity.scopeType) {
            HotkeyScopeType.GLOBAL -> true
            HotkeyScopeType.PLATFORM -> entity.scopeKey != null && entity.scopeKey == platformSlug
            HotkeyScopeType.CORE -> entity.scopeKey != null && entity.scopeKey == coreId
        }

    /** Whether two scopes can both apply to one game (different cores never do). */
    fun scopesOverlap(a: HotkeyEntity, b: HotkeyEntity): Boolean {
        val at = a.scopeType
        val bt = b.scopeType
        return when {
            at == HotkeyScopeType.GLOBAL || bt == HotkeyScopeType.GLOBAL -> true
            at == HotkeyScopeType.PLATFORM && bt == HotkeyScopeType.PLATFORM -> a.scopeKey == b.scopeKey
            at == HotkeyScopeType.CORE && bt == HotkeyScopeType.CORE -> a.scopeKey == b.scopeKey
            at == HotkeyScopeType.PLATFORM && bt == HotkeyScopeType.CORE -> coreRunsOnPlatform(b.scopeKey, a.scopeKey)
            at == HotkeyScopeType.CORE && bt == HotkeyScopeType.PLATFORM -> coreRunsOnPlatform(a.scopeKey, b.scopeKey)
            else -> false
        }
    }

    private fun coreRunsOnPlatform(coreId: String?, platformSlug: String?): Boolean {
        if (coreId == null || platformSlug == null) return false
        return LibretroCoreRegistry.getCoreById(coreId)?.platforms?.contains(platformSlug) == true
    }

    /** Warnings for a candidate bind vs overlapping binds sharing its combo+controller. */
    fun evaluateOnSave(
        candidate: HotkeyEntity,
        existing: List<HotkeyEntity>,
        parseCombo: (HotkeyEntity) -> List<Int>
    ): List<HotkeyBindWarning> {
        val candidateCombo = HotkeyManager.canonicalizeCombo(parseCombo(candidate))
        if (candidateCombo.isEmpty()) return emptyList()
        val candidateRank = tierRank(candidate.scopeType)

        return existing.asSequence()
            .filter { it.id != candidate.id }
            .filter { it.controllerId == candidate.controllerId }
            .filter { HotkeyManager.canonicalizeCombo(parseCombo(it)) == candidateCombo }
            .filter { scopesOverlap(candidate, it) }
            .mapNotNull { other ->
                val otherRank = tierRank(other.scopeType)
                when {
                    candidateRank > otherRank ->
                        HotkeyBindWarning.OverridesLowerTier(other.action, scopeLabel(other))
                    candidateRank < otherRank ->
                        HotkeyBindWarning.ShadowedByHigherTier(scopeLabel(other))
                    other.action != candidate.action ->
                        HotkeyBindWarning.SameTierConflict(other.action)
                    else -> null
                }
            }
            .toList()
    }

    fun scopeLabel(entity: HotkeyEntity): String = when (entity.scopeType) {
        HotkeyScopeType.GLOBAL -> "Global"
        HotkeyScopeType.PLATFORM -> "Platform"
        HotkeyScopeType.CORE -> "Core"
    }
}
