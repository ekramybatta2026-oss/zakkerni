package com.ultimateiptv.sports.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUtilsTest {
    @Test
    fun normalize() {
        assertEquals("bein sports 1", TextUtils.normalize("AR: beIN Sports 1 FHD"))
        assertEquals("ssc 1", TextUtils.normalize("SSC - 1 HD"))
        assertEquals("الاهلي", TextUtils.normalize("الأهلي"))
        assertEquals("atletico madrid", TextUtils.normalize("Atlético Madrid"))
    }

    @Test
    fun sports() {
        assertTrue(SportsClassifier.isSports("beIN Sports 2", null))
        assertTrue(SportsClassifier.isSports("Channel 5", "AR | SPORTS"))
        assertTrue(SportsClassifier.isSports("قناة", "قنوات رياضية"))
        assertFalse(SportsClassifier.isSports("Cartoon Network", "Kids"))
    }

    @Test
    fun teams() {
        assertEquals(listOf("manchester"), TextUtils.teamKeywords("Manchester United FC"))
        assertTrue(TextUtils.containsAny(TextUtils.normalize("Premier League: Arsenal v Chelsea"), TextUtils.teamKeywords("Arsenal FC")))
    }
}
