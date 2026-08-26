package com.xnote.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.xnote.app.R

// -- Type Definitions

enum class AppDestination(
    @param:StringRes val labelRes: Int,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    Notes(
        labelRes = R.string.navigation_notes,
        titleRes = R.string.notes_title,
        iconRes = R.drawable.ic_notes,
    ),
    Agent(
        labelRes = R.string.navigation_agent,
        titleRes = R.string.agent_title,
        iconRes = R.drawable.ic_agent,
    ),
    Profile(
        labelRes = R.string.navigation_profile,
        titleRes = R.string.profile_title,
        iconRes = R.drawable.ic_profile,
    ),
}
