package com.nendo.argosy.ui.screens.doodle

import androidx.compose.ui.graphics.Color

enum class DoodleColor(val index: Int, val hex: Long) {
    WHITE(0, 0xFFFFFFFF),
    BLACK(1, 0xFF000000),
    GRAY(2, 0xFF808080),
    RED(3, 0xFFFF0000),
    ORANGE(4, 0xFFFF8800),
    YELLOW(5, 0xFFFFFF00),
    GREEN(6, 0xFF00FF00),
    CYAN(7, 0xFF00FFFF),
    LIGHT_BLUE(8, 0xFF0088FF),
    BLUE(9, 0xFF0000FF),
    PURPLE(10, 0xFF8800FF),
    MAGENTA(11, 0xFFFF00FF),
    PINK(12, 0xFFFF88AA),
    BROWN(13, 0xFF884400),
    LIGHT_GREEN(14, 0xFF88FF88),
    SKIN(15, 0xFFFFCC88);

    val color: Color get() = Color(hex)

    companion object {
        fun fromIndex(index: Int): DoodleColor =
            entries.find { it.index == index } ?: WHITE
    }
}

enum class DoodleTool {
    PEN, LINE, FILL
}

enum class CanvasSize(val pixels: Int, val sizeEnum: Int) {
    SMALL(16, 0),
    MEDIUM(32, 1),
    LARGE(64, 2);

    companion object {
        fun fromEnum(value: Int): CanvasSize =
            entries.find { it.sizeEnum == value } ?: MEDIUM
    }
}

enum class DoodleSection {
    CANVAS, PALETTE, SIZE, CAPTION
}

enum class ZoomLevel(val scale: Float) {
    FIT(1f),
    X2(2f),
    X4(4f),
    X8(8f);

    fun next(): ZoomLevel = when (this) {
        FIT -> X2
        X2 -> X4
        X4 -> X8
        X8 -> FIT
    }
}

data class DoodleUiState(
    val canvasSize: CanvasSize = CanvasSize.MEDIUM,
    val pixels: Map<Pair<Int, Int>, DoodleColor> = emptyMap(),
    val selectedColor: DoodleColor = DoodleColor.BLACK,
    val selectedTool: DoodleTool = DoodleTool.PEN,
    val currentSection: DoodleSection = DoodleSection.CANVAS,
    val cursorX: Int = 0,
    val cursorY: Int = 0,
    val paletteFocusIndex: Int = 1,
    val sizeFocusIndex: Int = 1,
    val caption: String = "",
    val linkedGameId: Int? = null,
    val linkedGameTitle: String? = null,
    val isDrawing: Boolean = false,
    val lineStartX: Int? = null,
    val lineStartY: Int? = null,
    val showPostMenu: Boolean = false,
    val postMenuFocusIndex: Int = 0,
    val showDiscardDialog: Boolean = false,
    val discardDialogFocusIndex: Int = 0,
    val isPosting: Boolean = false,
    val zoomLevel: ZoomLevel = ZoomLevel.FIT,
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f
) {
    val hasContent: Boolean get() = pixels.isNotEmpty()

    val linePreview: List<Pair<Int, Int>>?
        get() = if (selectedTool == DoodleTool.LINE && lineStartX != null && lineStartY != null) {
            bresenhamLine(lineStartX, lineStartY, cursorX, cursorY)
        } else null
}

fun bresenhamLine(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
    val points = mutableListOf<Pair<Int, Int>>()

    var dx = kotlin.math.abs(x1 - x0)
    var dy = -kotlin.math.abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy

    var x = x0
    var y = y0

    while (true) {
        points.add(x to y)
        if (x == x1 && y == y1) break

        val e2 = 2 * err
        if (e2 >= dy) {
            if (x == x1) break
            err += dy
            x += sx
        }
        if (e2 <= dx) {
            if (y == y1) break
            err += dx
            y += sy
        }
    }

    return points
}

fun floodFill(
    pixels: Map<Pair<Int, Int>, DoodleColor>,
    startX: Int,
    startY: Int,
    newColor: DoodleColor,
    canvasSize: Int
): Map<Pair<Int, Int>, DoodleColor> {
    val targetColor = pixels[startX to startY] ?: DoodleColor.WHITE
    if (targetColor == newColor) return pixels

    val result = pixels.toMutableMap()
    val queue = ArrayDeque<Pair<Int, Int>>()
    val visited = mutableSetOf<Pair<Int, Int>>()

    queue.add(startX to startY)
    visited.add(startX to startY)

    while (queue.isNotEmpty()) {
        val (x, y) = queue.removeFirst()
        val currentColor = result[x to y] ?: DoodleColor.WHITE

        if (currentColor != targetColor) continue

        if (newColor == DoodleColor.WHITE) {
            result.remove(x to y)
        } else {
            result[x to y] = newColor
        }

        listOf(
            x - 1 to y,
            x + 1 to y,
            x to y - 1,
            x to y + 1
        ).filter { (nx, ny) ->
            nx in 0 until canvasSize &&
                ny in 0 until canvasSize &&
                (nx to ny) !in visited
        }.forEach { neighbor ->
            visited.add(neighbor)
            queue.add(neighbor)
        }
    }

    return result
}

data class DecodedDoodle(
    val size: CanvasSize,
    val pixels: Map<Pair<Int, Int>, DoodleColor>
)
