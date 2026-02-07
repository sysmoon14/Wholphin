package com.github.sysmoon.wholphin.ui.detail

import com.github.sysmoon.wholphin.data.model.BaseItem

/**
 * Represents a "More from [collection name]" row: collection title and items (siblings, excluding current).
 */
data class CollectionRow(
    val collectionName: String,
    val items: List<BaseItem>,
)
