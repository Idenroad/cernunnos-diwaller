package com.cernunnos.authenticator.constants

/**
 * UI theme, list mode, sort, and widget constants.
 */
object UiConstants {
    // Themes
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"
    const val THEME_SYSTEM = "system"
    const val DEFAULT_THEME = THEME_DARK

    // List modes
    const val LIST_MODE_ALL = "all"
    const val LIST_MODE_CATEGORIES = "categories"
    const val DEFAULT_LIST_MODE = LIST_MODE_ALL

    // Sort
    const val SORT_NAME = "name"
    const val SORT_ISSUER = "issuer"
    const val SORT_DATE = "date"
    const val SORT_FAVORITES = "favorites"
    const val SORT_MANUAL = "manual"
    const val DEFAULT_SORT = SORT_NAME

    // View modes
    const val VIEW_MODE_LIST = "list"
    const val VIEW_MODE_TILES = "tiles"
    const val VIEW_MODE_COMPACT = "compact"
    const val DEFAULT_VIEW_MODE = VIEW_MODE_LIST

    // Widget
    const val WIDGET_MODE_FAVORITES = "favorites"
    const val WIDGET_MODE_CATEGORY = "category"
    const val WIDGET_MODE_ALL = "all"
    const val DEFAULT_WIDGET_MODE = WIDGET_MODE_FAVORITES
    const val WIDGET_MAX_ENTRIES = 10
}
