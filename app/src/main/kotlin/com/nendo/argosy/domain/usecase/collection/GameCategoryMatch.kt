package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.repository.GameCategoryTokens

fun GameCategoryTokens.matches(type: CategoryType, name: String): Boolean {
    val tokens = when (type) {
        CategoryType.GENRE -> genres
        CategoryType.GAME_MODE -> gameModes
        CategoryType.SERIES -> null
    }
    return tokens?.any { it.equals(name, ignoreCase = true) } == true
}
