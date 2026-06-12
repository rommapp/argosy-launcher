package com.nendo.argosy.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.sync.platform.MemcardInfo
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import java.text.DateFormat
import java.util.Date

@Composable
fun MemcardPickerModal(
    cards: List<MemcardInfo>,
    focusIndex: Int,
    selectedCardPath: String?,
    onSelectCard: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Modal(
        title = "SELECT MEMORY CARD",
        subtitle = "Save sync uses the selected folder memory card",
        onDismiss = onDismiss
    ) {
        val listState = rememberLazyListState()
        FocusedScroll(listState = listState, focusedIndex = focusIndex)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            itemsIndexed(cards) { index, card ->
                OptionItem(
                    label = card.name,
                    value = summarize(card, dateFormat),
                    isFocused = focusIndex == index,
                    isSelected = card.path == selectedCardPath,
                    onClick = { onSelectCard(card.path) }
                )
            }
        }
    }
}

private fun summarize(card: MemcardInfo, dateFormat: DateFormat): String {
    val games = when (card.gameFolderCount) {
        0 -> "empty"
        1 -> "1 game save"
        else -> "${card.gameFolderCount} game saves"
    }
    return if (card.lastModified > 0) {
        "$games · ${dateFormat.format(Date(card.lastModified))}"
    } else {
        games
    }
}
