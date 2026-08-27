package com.nendo.argosy.ui.screens.settings.menu

import android.content.Context
import com.nendo.argosy.ui.components.ListSection

enum class DisabledBehavior {
    HIDDEN,
    LOCKED
}

class SettingsLayout<Item, State>(
    private val allItems: List<Item>,
    private val isFocusable: (Item) -> Boolean,
    private val visibleWhen: (Item, State) -> Boolean,
    private val disabledBehavior: (Item) -> DisabledBehavior = { DisabledBehavior.HIDDEN },
    private val sectionOf: (Item) -> String? = { null },
    private val sectionTitleRes: (String) -> Int? = { null }
) {
    /**
     * What the pane shows, with headings for sections that lost all their settings left out.
     *
     * A section empties whenever the choices above it hide the rows below - picking a grid layout
     * takes the background rows away, for instance - and a heading with nothing under it reads as a
     * section that failed to load. Only decoration is dropped: anything focusable survives, so this
     * can never hide a setting.
     */
    fun visibleItems(state: State): List<Item> {
        val shown = allItems.filter {
            visibleWhen(it, state) || disabledBehavior(it) == DisabledBehavior.LOCKED
        }
        val sectionsWithSettings = shown.filter { isFocusable(it) }
            .mapNotNull { sectionOf(it) }
            .toSet()
        return shown.filter { item ->
            val section = sectionOf(item)
            isFocusable(item) || section == null || section in sectionsWithSettings
        }
    }

    fun focusableItems(state: State): List<Item> =
        visibleItems(state).filter { isFocusable(it) }

    fun focusIndexOf(item: Item, state: State): Int =
        focusableItems(state).indexOf(item)

    fun itemAtFocusIndex(index: Int, state: State): Item? =
        focusableItems(state).getOrNull(index)

    fun maxFocusIndex(state: State): Int =
        (focusableItems(state).size - 1).coerceAtLeast(0)

    fun focusToListIndex(focusIndex: Int, state: State): Int {
        val item = focusableItems(state).getOrNull(focusIndex) ?: return focusIndex
        return visibleItems(state).indexOf(item)
    }

    /**
     * Section spans for the pane, with rail titles resolved only when a [context] is supplied.
     * Input handlers jump by index and pass none; render sites pass one so the rail can be named.
     */
    fun buildSections(state: State, context: Context? = null): List<ListSection> {
        val visible = visibleItems(state)
        val focusable = focusableItems(state)
        val sectionNames = visible.mapNotNull { sectionOf(it) }.distinct()

        return sectionNames.mapNotNull { sectionName ->
            val sectionItems = visible.filter { sectionOf(it) == sectionName }
            val sectionFocusable = focusable.filter { sectionOf(it) == sectionName }
            if (sectionItems.isEmpty() || sectionFocusable.isEmpty()) return@mapNotNull null

            ListSection(
                name = context?.let { ctx -> sectionTitleRes(sectionName)?.let(ctx::getString) },
                listStartIndex = visible.indexOf(sectionItems.first()),
                listEndIndex = visible.indexOf(sectionItems.last()),
                focusStartIndex = focusable.indexOf(sectionFocusable.first()),
                focusEndIndex = focusable.indexOf(sectionFocusable.last())
            )
        }
    }
}
