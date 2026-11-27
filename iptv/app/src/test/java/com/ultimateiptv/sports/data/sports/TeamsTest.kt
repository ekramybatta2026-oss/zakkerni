package com.ultimateiptv.sports.data.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamsTest {
    @Test
    fun aliasesFromBothProviders() {
        assertEquals(33, Teams.find("Man United")?.apiFootballId)          // football-data shortName
        assertEquals(33, Teams.find("Manchester United FC")?.apiFootballId)
        assertEquals(529, Teams.find("Barça")?.apiFootballId)
        assertEquals(157, Teams.find("Bayern München")?.apiFootballId)     // API-Football name
        assertEquals(85, Teams.find("Paris Saint Germain")?.apiFootballId)
        assertEquals(2932, Teams.find("Al-Hilal Saudi FC")?.apiFootballId)
        assertNull(Teams.find("Unknown Town FC"))
    }

    @Test
    fun arabicNames() {
        assertEquals("ريال مدريد", Teams.displayName("Real Madrid", arabic = true))
        assertEquals("Real Madrid", Teams.displayName("Real Madrid", arabic = false))
        assertEquals("Some Club", Teams.displayName("Some Club", arabic = true))
        assertEquals("https://media.api-sports.io/football/teams/42.png", Teams.logo("Arsenal"))
    }

    @Test
    fun noDuplicateKeys() {
        val ids = Teams.all.map { it.apiFootballId }
        assertEquals(ids.size, ids.toSet().size)
    }
}
