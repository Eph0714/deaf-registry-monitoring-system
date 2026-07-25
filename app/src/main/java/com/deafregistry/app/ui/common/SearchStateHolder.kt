package com.deafregistry.app.ui.common

/**
 * In-memory, app-process-lifetime holder for the "last used" selection on each search/filter
 * screen. Navigation-Compose clears a screen's ViewModel and rememberSaveable state once its
 * back-stack entry is popped (e.g. tapping the back button), so leaving a search screen and
 * reopening it later would otherwise always reset to defaults - this survives that because it
 * isn't scoped to any NavBackStackEntry, just process lifetime, same as the rest of the app's
 * transient (non-persisted-to-disk) UI state. Resets only when the app process is killed.
 */
object SearchStateHolder {
    var searchQuery: String = ""
    var individualsCategoryKey: String? = null
    var individualsStatusChoice: String? = null
    var individualsSearchText: String = ""
    var individualsSkillChoice: String? = null
    var reportsCategoryKey: String? = null
    var reportsStatusChoice: String? = null

    // Municipality Directory module - preserves the user's search/filter/sort/expand/scroll
    // state across navigating away (View/Edit/Add Record, etc.) and back, same reasoning as
    // every other field above.
    var municipalityDirectorySearchText: String = ""
    var municipalityDirectoryGroupMode: String? = null
    var municipalityDirectoryMunicipalityFilter: String? = null
    var municipalityDirectoryBarangayFilter: String? = null
    var municipalityDirectorySortOption: String? = null
    var municipalityDirectoryExpandedGroups: Set<String> = emptySet()
    var municipalityDirectoryScrollIndex: Int = 0
    var municipalityDirectoryScrollOffset: Int = 0
}
