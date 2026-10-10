package com.nendo.argosy.ui.components

enum class BoxArtRoute { SPINE_RENDER, BOX_3D_IMAGE, FLAT_COVER, TEXT }

/**
 * The ways a game's box art can be drawn, best first, ending in [BoxArtRoute.TEXT]. With
 * [useBoxArt] the spine render leads, then the 3D box image, then the flat cover; without it the
 * flat cover leads, then the 3D box image, and the spine render is never offered. The spine render
 * is offered only where [allowSpineRender], and needs the spine and the cover as local files.
 */
fun boxArtRoutes(
    useBoxArt: Boolean,
    spinePath: String?,
    box3dPath: String?,
    coverPath: String?,
    allowSpineRender: Boolean = true
): List<BoxArtRoute> {
    val spineRender = BoxArtRoute.SPINE_RENDER.takeIf {
        allowSpineRender && useBoxArt &&
            spinePath?.startsWith("/") == true && coverPath?.startsWith("/") == true
    }
    val box3dImage = BoxArtRoute.BOX_3D_IMAGE.takeIf { !box3dPath.isNullOrEmpty() }
    val flatCover = BoxArtRoute.FLAT_COVER.takeIf { !coverPath.isNullOrEmpty() }
    val images = if (useBoxArt) listOf(spineRender, box3dImage, flatCover) else listOf(flatCover, box3dImage)
    return images.filterNotNull() + BoxArtRoute.TEXT
}

fun List<BoxArtRoute>.firstWorking(failed: Set<BoxArtRoute>): BoxArtRoute =
    firstOrNull { it !in failed } ?: BoxArtRoute.TEXT
