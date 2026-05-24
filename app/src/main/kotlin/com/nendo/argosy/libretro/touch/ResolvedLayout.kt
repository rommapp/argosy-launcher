package com.nendo.argosy.libretro.touch

import android.content.res.Configuration

data class GroupPlacement(
    val anchorX: Float,
    val anchorY: Float,
    val scale: Float = 1.0f
)

data class ResolvedLayout(
    val placements: Map<GroupId, GroupPlacement>
) {
    fun get(id: GroupId): GroupPlacement? = placements[id]
}

object LayoutDefaults {

    fun forOrientation(spec: TouchLayoutSpec, orientation: Int): ResolvedLayout =
        if (orientation == Configuration.ORIENTATION_PORTRAIT) portraitFor(spec) else landscapeFor(spec)

    private fun landscapeFor(spec: TouchLayoutSpec): ResolvedLayout {
        val placements = mutableMapOf<GroupId, GroupPlacement>()
        when (spec.analog) {
            AnalogConfig.None -> placements[GroupId.DPAD] = GroupPlacement(0.10f, 0.78f)
            AnalogConfig.LeftOnly,
            AnalogConfig.LeftAndRight -> {
                placements[GroupId.LEFT_ANALOG] = GroupPlacement(0.10f, 0.78f)
                if (spec.dpad != DpadStyle.None && spec.dpad != DpadStyle.AnalogOnly) {
                    placements[GroupId.DPAD] = GroupPlacement(0.22f, 0.78f, scale = 0.7f)
                }
            }
        }
        placements[GroupId.FACE] = GroupPlacement(0.90f, 0.78f)
        if (spec.shoulders != ShoulderShape.None) {
            placements[GroupId.SHOULDERS] = GroupPlacement(0.5f, 0.10f)
        }
        placements[GroupId.SYSTEM] = GroupPlacement(0.5f, 0.92f)
        if (spec.analog == AnalogConfig.LeftAndRight) {
            placements[GroupId.RIGHT_ANALOG] = GroupPlacement(0.78f, 0.45f)
        }
        return ResolvedLayout(placements)
    }

    private fun portraitFor(spec: TouchLayoutSpec): ResolvedLayout {
        val placements = mutableMapOf<GroupId, GroupPlacement>()
        when (spec.analog) {
            AnalogConfig.None -> placements[GroupId.DPAD] = GroupPlacement(0.22f, 0.62f)
            AnalogConfig.LeftOnly,
            AnalogConfig.LeftAndRight -> {
                placements[GroupId.LEFT_ANALOG] = GroupPlacement(0.22f, 0.62f)
                if (spec.dpad != DpadStyle.None && spec.dpad != DpadStyle.AnalogOnly) {
                    placements[GroupId.DPAD] = GroupPlacement(0.22f, 0.32f, scale = 0.7f)
                }
            }
        }
        placements[GroupId.FACE] = GroupPlacement(0.78f, 0.62f)
        if (spec.shoulders != ShoulderShape.None) {
            placements[GroupId.SHOULDERS] = GroupPlacement(0.5f, 0.10f)
        }
        placements[GroupId.SYSTEM] = GroupPlacement(0.5f, 0.92f)
        if (spec.analog == AnalogConfig.LeftAndRight) {
            placements[GroupId.RIGHT_ANALOG] = GroupPlacement(0.78f, 0.32f)
        }
        return ResolvedLayout(placements)
    }
}

fun ResolvedLayout.applyHandedness(swap: Boolean): ResolvedLayout {
    if (!swap) return this
    return ResolvedLayout(placements.mapValues { (_, p) -> p.copy(anchorX = 1f - p.anchorX) })
}

fun ResolvedLayout.applySizeScale(scale: Float): ResolvedLayout {
    if (scale == 1.0f) return this
    return ResolvedLayout(placements.mapValues { (_, p) -> p.copy(scale = p.scale * scale) })
}

fun ResolvedLayout.applyMirror180(mirror: Boolean, rotation: Int, baseline: Int = 0): ResolvedLayout {
    if (!mirror) return this
    val flipped = ((rotation - baseline) % 4 + 4) % 4 == 2
    if (!flipped) return this
    return ResolvedLayout(placements.mapValues { (_, p) -> p.copy(anchorX = 1f - p.anchorX) })
}
