package io.github.impca201.trackerforsimkl.data.model

/** Which facts of a title the info card shows. Kept out of the composable so it can be unit tested. */
object DetailFacts {
    /** Facts shown while the card is collapsed: the first few, in the card's order (rank, genres, status). */
    const val COLLAPSED_COUNT = 3

    /** All the facts when [expanded], else only the first [COLLAPSED_COUNT]. */
    fun <T> visible(
        facts: List<T>,
        expanded: Boolean,
    ): List<T> = if (expanded) facts else facts.take(COLLAPSED_COUNT)

    /** Whether there is anything more to show; a card with few facts has no expand button. */
    fun canExpand(count: Int): Boolean = count > COLLAPSED_COUNT
}
