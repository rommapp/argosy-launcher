package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.FolderAccent
import com.nendo.argosy.domain.model.FolderBackdropStyle
import com.nendo.argosy.domain.model.FolderCoverStyle
import com.nendo.argosy.domain.model.FolderMemberSize
import com.nendo.argosy.domain.model.StickerOutlineColor
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens.Domain.FolderAccent as Palette

@get:StringRes
val FolderCoverStyle.labelRes: Int
    get() = when (this) {
        FolderCoverStyle.MOSAIC -> R.string.ui_folder_appearance_cover_mosaic
        FolderCoverStyle.IMAGE -> R.string.ui_folder_appearance_cover_image
        FolderCoverStyle.STICKER -> R.string.ui_folder_appearance_cover_sticker
    }

@get:StringRes
val FolderBackdropStyle.labelRes: Int
    get() = when (this) {
        FolderBackdropStyle.NONE -> R.string.ui_folder_appearance_backdrop_none
        FolderBackdropStyle.COVER_BLURRED -> R.string.ui_folder_appearance_backdrop_cover
        FolderBackdropStyle.IMAGE -> R.string.ui_folder_appearance_backdrop_image
    }

@get:StringRes
val FolderAccent.labelRes: Int
    get() = when (this) {
        FolderAccent.THEME -> R.string.ui_folder_appearance_accent_theme
        FolderAccent.ROSE -> R.string.ui_folder_appearance_accent_rose
        FolderAccent.AMBER -> R.string.ui_folder_appearance_accent_amber
        FolderAccent.LIME -> R.string.ui_folder_appearance_accent_lime
        FolderAccent.SKY -> R.string.ui_folder_appearance_accent_sky
        FolderAccent.VIOLET -> R.string.ui_folder_appearance_accent_violet
    }

@get:StringRes
val FolderMemberSize.labelRes: Int
    get() = when (this) {
        FolderMemberSize.SMALL -> R.string.ui_folder_appearance_size_small
        FolderMemberSize.MEDIUM -> R.string.ui_folder_appearance_size_medium
        FolderMemberSize.LARGE -> R.string.ui_folder_appearance_size_large
    }

@StringRes
fun folderOutlineLabelRes(color: StickerOutlineColor?): Int = when (color) {
    null -> R.string.ui_folder_appearance_outline_off
    StickerOutlineColor.WHITE -> R.string.ui_folder_appearance_outline_white
    StickerOutlineColor.BLACK -> R.string.ui_folder_appearance_outline_black
    StickerOutlineColor.ACCENT -> R.string.ui_folder_appearance_outline_accent
}

val FolderAccent.color: Color
    @Composable get() {
        val dark = LocalLauncherTheme.current.isDarkTheme
        return when (this) {
            FolderAccent.THEME -> LocalArgosyTheme.current.focusAccent
            FolderAccent.ROSE -> if (dark) Palette.Rose.dark else Palette.Rose.light
            FolderAccent.AMBER -> if (dark) Palette.Amber.dark else Palette.Amber.light
            FolderAccent.LIME -> if (dark) Palette.Lime.dark else Palette.Lime.light
            FolderAccent.SKY -> if (dark) Palette.Sky.dark else Palette.Sky.light
            FolderAccent.VIOLET -> if (dark) Palette.Violet.dark else Palette.Violet.light
        }
    }
