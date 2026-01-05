package com.nendo.argosy.domain.model

import com.nendo.argosy.domain.usecase.collection.CategoryType

sealed class PinnedCollection {
    abstract val id: Long
    abstract val displayOrder: Int
    abstract val displayName: String
    abstract val gameCount: Int

    data class Regular(
        override val id: Long,
        val collectionId: Long,
        override val displayName: String,
        override val displayOrder: Int,
        override val gameCount: Int
    ) : PinnedCollection()

    data class Virtual(
        override val id: Long,
        val type: CategoryType,
        val categoryName: String,
        override val displayOrder: Int,
        override val gameCount: Int
    ) : PinnedCollection() {
        override val displayName: String
            get() = categoryName
    }
}
