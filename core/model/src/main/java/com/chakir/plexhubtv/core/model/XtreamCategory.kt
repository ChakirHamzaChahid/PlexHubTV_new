package com.chakir.plexhubtv.core.model

data class XtreamCategory(
    val categoryId: Int,
    val categoryName: String,
    val parentId: Int = 0,
)
