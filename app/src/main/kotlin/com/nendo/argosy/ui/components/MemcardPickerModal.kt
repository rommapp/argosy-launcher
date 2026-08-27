package com.nendo.argosy.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.sync.platform.MemcardInfo
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import android.content.Context
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
    val context = LocalContext.current
    Modal(
        title = stringResource(R.string.ui_memcard_picker_title),
        subtitle = stringResource(R.string.ui_memcard_picker_subtitle),
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
                    label = defaultCardLabel(context, card),
                    value = summarize(context, card, dateFormat),
                    isFocused = focusIndex == index,
                    isSelected = card.path == selectedCardPath,
                    onClick = { onSelectCard(card.path) }
                )
            }
        }
    }
}

private fun defaultCardLabel(context: Context, card: MemcardInfo): String {
    if (!card.isDefault) return card.name
    return if (card.name.isNotBlank()) {
        context.getString(R.string.ui_memcard_picker_default_named, card.name)
    } else {
        context.getString(R.string.ui_memcard_picker_default)
    }
}

private fun summarize(context: Context, card: MemcardInfo, dateFormat: DateFormat): String {
    val games = if (card.gameFolderCount == 0) {
        context.getString(R.string.ui_memcard_picker_empty)
    } else {
        context.resources.getQuantityString(
            R.plurals.ui_memcard_picker_save_count,
            card.gameFolderCount,
            card.gameFolderCount
        )
    }
    return if (card.lastModified > 0) {
        context.getString(
            R.string.ui_memcard_picker_summary_dated,
            games,
            dateFormat.format(Date(card.lastModified))
        )
    } else {
        games
    }
}
