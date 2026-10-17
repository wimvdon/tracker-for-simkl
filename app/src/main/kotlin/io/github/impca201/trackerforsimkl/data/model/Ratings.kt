package io.github.impca201.trackerforsimkl.data.model

/** The titles the user rated, for the "My ratings" page. Plain Kotlin so it is unit tested. */
object Ratings {
    /** Rated titles of [type], highest rating first; equal ratings by title. */
    fun rated(
        items: List<LibraryItem>,
        type: MediaType,
    ): List<LibraryItem> =
        items
            .filter { it.type == type && it.userRating != null }
            .sortedWith(compareByDescending<LibraryItem> { it.userRating }.thenBy { it.title.lowercase() })
}
