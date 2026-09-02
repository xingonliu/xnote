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
    Agent(
        labelRes = R.string.navigation_agent,
        titleRes = R.string.agent_title,
        tabIconRes = R.drawable.ic_keyline_fill_star,
        navigationIconRes = R.drawable.ic_keyline_stroke_star,
    ),
    Profile(
        labelRes = R.string.navigation_profile,
        titleRes = R.string.profile_title,
        tabIconRes = R.drawable.ic_keyline_fill_user,
        navigationIconRes = R.drawable.ic_keyline_stroke_user,
    ),
}
