package com.nendo.argosy.libretro.touch

import android.content.res.Configuration

data class GroupPlacement(
    val anchorX: Float,
    val anchorY: Float,
    val scale: Float = 1.0f,
    val disabled: Boolean = false
)

data class ResolvedLayout(
    val placements: Map<GroupId, GroupPlacement>,
    val buttonOverrides: Map<String, GroupPlacement> = emptyMap()
) {
    fun get(id: GroupId): GroupPlacement? = placements[id]
    fun getButton(key: String): GroupPlacement? = buttonOverrides[key]
}

object LayoutDefaults {

    fun forOrientation(spec: TouchLayoutSpec, orientation: Int): ResolvedLayout =
        if (orientation == Configuration.ORIENTATION_PORTRAIT) portraitFor(spec) else landscapeFor(spec)

    private fun landscapeFor(spec: TouchLayoutSpec): ResolvedLayout {
        val placements = mutableMapOf<GroupId, GroupPlacement>()
        val hasDpad = spec.dpad != DpadStyle.None && spec.dpad != DpadStyle.AnalogOnly
        val hasLeftStick = spec.analog != AnalogConfig.None
        val hasRightStick = spec.analog == AnalogConfig.LeftAndRight
        if (hasDpad) {
            placements[GroupId.DPAD] = GroupPlacement(0.10f, 0.55f)
        }
        if (hasLeftStick) {
            val stickAnchorX = if (hasDpad) 0.18f else 0.10f
            val stickAnchorY = if (hasDpad) 0.85f else 0.78f
            placements[GroupId.LEFT_ANALOG] = GroupPlacement(stickAnchorX, stickAnchorY)
        }
        placements[GroupId.FACE] = GroupPlacement(0.90f, 0.55f)
        if (hasRightStick) {
            placements[GroupId.RIGHT_ANALOG] = GroupPlacement(0.82f, 0.85f)
        }
        if (spec.shoulders != ShoulderShape.None) {
            placements[GroupId.SHOULDERS] = GroupPlacement(0.5f, 0.12f)
        }
        placements[GroupId.SYSTEM] = GroupPlacement(0.5f, 0.95f)
        return ResolvedLayout(placements)
    }

    private fun portraitFor(spec: TouchLayoutSpec): ResolvedLayout {
        val placements = mutableMapOf<GroupId, GroupPlacement>()
        val hasDpad = spec.dpad != DpadStyle.None && spec.dpad != DpadStyle.AnalogOnly
        val hasLeftStick = spec.analog != AnalogConfig.None
        val hasRightStick = spec.analog == AnalogConfig.LeftAndRight
        if (hasDpad) {
            placements[GroupId.DPAD] = GroupPlacement(0.12f, 0.40f)
        }
        if (hasLeftStick) {
            val stickAnchorX = if (hasDpad) 0.22f else 0.12f
            val stickAnchorY = if (hasDpad) 0.80f else 0.62f
            placements[GroupId.LEFT_ANALOG] = GroupPlacement(stickAnchorX, stickAnchorY)
        }
        placements[GroupId.FACE] = GroupPlacement(0.88f, 0.40f)
        if (hasRightStick) {
            placements[GroupId.RIGHT_ANALOG] = GroupPlacement(0.78f, 0.80f)
        }
        if (spec.shoulders != ShoulderShape.None) {
            placements[GroupId.SHOULDERS] = GroupPlacement(0.5f, 0.10f)
        }
        placements[GroupId.SYSTEM] = GroupPlacement(0.5f, 0.95f)
        return ResolvedLayout(placements)
    }
}

fun ResolvedLayout.applyHandedness(swap: Boolean): ResolvedLayout {
    if (!swap) return this
    return ResolvedLayout(
        placements = placements.mapValues { (_, p) -> p.copy(anchorX = 1f - p.anchorX) },
        buttonOverrides = buttonOverrides.mapValues { (_, p) -> p.copy(anchorX = 1f - p.anchorX) }
    )
}

fun ResolvedLayout.applySizeScale(scale: Float): ResolvedLayout {
    if (scale == 1.0f) return this
    return ResolvedLayout(
        placements = placements.mapValues { (_, p) -> p.copy(scale = p.scale * scale) },
        buttonOverrides = buttonOverrides.mapValues { (_, p) -> p.copy(scale = p.scale * scale) }
    )
}

fun ResolvedLayout.applyMirror180(mirror: Boolean, rotation: Int, baseline: Int = 0): ResolvedLayout {
    if (!mirror) return this
    val flipped = ((rotation - baseline) % 4 + 4) % 4 == 2
    if (!flipped) return this
    return ResolvedLayout(
        placements = placements.mapValues { (_, p) -> p.copy(anchorX = 1f - p.anchorX) },
        buttonOverrides = buttonOverrides.mapValues { (_, p) -> p.copy(anchorX = 1f - p.anchorX) }
    )
}
