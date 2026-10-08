package com.xnote.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.xnote.app.R

// -- Type Definitions

enum class AppDestination(
    @param:StringRes val labelRes: Int,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val tabIconRes: Int,
    @param:DrawableRes val navigationIconRes: Int,
) {
    Notes(
        labelRes = R.string.navigation_notes,
        titleRes = R.string.notes_title,
        tabIconRes = R.drawable.ic_keyline_fill_file_text,
        navigationIconRes = R.drawable.ic_keyline_stroke_square_pen,
    ),
    Stickers(
        labelRes = R.string.navigation_stickers,
        titleRes = R.string.navigation_stickers,
        tabIconRes = R.drawable.ic_keyline_fill_grid_3x3,
        navigationIconRes = R.drawable.ic_keyline_stroke_grid_3x3,
    ),
    Profile(
        labelRes = R.string.navigation_profile,
        titleRes = R.string.profile_title,
        tabIconRes = R.drawable.ic_keyline_fill_user,
        navigationIconRes = R.drawable.ic_keyline_stroke_user,
    ),
}
