package io.github.impca201.trackerforsimkl.data

import io.github.impca201.trackerforsimkl.data.model.DetailFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailFactsTest {
    private val facts = listOf("rank", "genres", "status", "airs", "network")

    @Test
    fun collapsedShowsOnlyTheFirstFacts() {
        assertEquals(listOf("rank", "genres", "status"), DetailFacts.visible(facts, expanded = false))
    }

    @Test
    fun expandedShowsEverything() {
        assertEquals(facts, DetailFacts.visible(facts, expanded = true))
    }

    @Test
    fun fewFactsAreAllShownAndCannotExpand() {
        val few = listOf("rank", "genres")
        assertEquals(few, DetailFacts.visible(few, expanded = false))
        assertFalse(DetailFacts.canExpand(few.size))
        assertFalse(DetailFacts.canExpand(0))
        assertFalse(DetailFacts.canExpand(DetailFacts.COLLAPSED_COUNT))
    }

    @Test
    fun moreFactsThanTheLimitCanExpand() {
        assertTrue(DetailFacts.canExpand(DetailFacts.COLLAPSED_COUNT + 1))
        assertTrue(DetailFacts.canExpand(facts.size))
    }
}
