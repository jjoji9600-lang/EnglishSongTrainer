package com.englishsongtrainer
import org.junit.Assert.*
import org.junit.Test
class LyricsTest {
    @Test fun lrc() {
        val c=Lyrics.parse("[offset:100]\n[00:01.20][00:03.2]Hello\n[00:05.000]World")
        assertEquals(listOf(1300L,3300L,5100L),c.map{it.start})
        assertEquals(1300L to 3300L,Lyrics.range(c,0,0,10000))
        assertEquals(5100L to 10000L,Lyrics.range(c,2,0,10000))
    }
    @Test fun srtVtt() {
        val c=Lyrics.parse("1\r\n00:00:01,000 --> 00:00:02,500\r\nHello\r\n")
        assertEquals(Cue(1000,2500,"Hello"),c.single())
        assertEquals(c,Lyrics.parse("WEBVTT\n\n00:01.000 --> 00:02.500 align:start\nHello\n"))
    }
    @Test fun duplicates() {
        assertEquals("Hello\n你好",Lyrics.parse("[00:01]Hello\n[00:01]你好").single().text)
        assertTrue(Lyrics.parse("not timed").isEmpty())
    }
}
