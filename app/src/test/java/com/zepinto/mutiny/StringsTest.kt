package com.zepinto.mutiny

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsTest {
    @Test
    fun everyLanguageIsComplete() {
        for (lang in Lang.entries) {
            val t = lang.strings
            assertEquals(8, t.rulesSections.size)
            t.rulesSections.forEach { assertTrue(it.title.isNotBlank() && it.lines.isNotEmpty()) }
            assertTrue(t.passTo("Ana").contains("Ana"))
            assertTrue(t.passPhoneTo("Ana").contains("Ana"))
            assertTrue(t.tapToFlip("Ana").contains("Ana"))
            assertTrue(t.voteQuestion("Ana").contains("Ana"))
            assertTrue(t.voyageQuestion("Ana").contains("Ana"))
            assertTrue(t.imName("Ana").contains("Ana"))
            assertTrue(t.captainIs("Ana").contains("Ana"))
            assertTrue(t.fellowMutineers("Ana, Rui").contains("Ana, Rui"))
            assertTrue(t.voyageOf(3).contains("3"))
            assertTrue(t.rejectedProposals(4).contains("4"))
            assertTrue(t.failsLine(2, 2).contains("2"))
            assertNotEquals(t.youAreLoyal, t.youAreMutineer)
            assertNotEquals(t.loyalWins, t.mutineersWin)
            assertNotEquals(t.voteApproved, t.voteRejected)
            assertNotEquals(t.success, t.fail)
        }
        assertEquals(Lang.PT, Lang.fromCode("pt"))
        assertNull(Lang.fromCode("fr"))
    }
}
