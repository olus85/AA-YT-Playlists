package app.olus.ytmusic.autolauncher.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import app.olus.ytmusic.autolauncher.util.SearchQueryCleaner

class MetadataFetcherTest {

    private val fetcher = MetadataFetcher()

    @Test
    fun testExtractPlaylistId_standardUrl() {
        val url = "https://music.youtube.com/playlist?list=PL_test_123"
        assertEquals("PL_test_123", fetcher.extractPlaylistId(url))
    }

    @Test
    fun testExtractPlaylistId_wwwUrl() {
        val url = "https://www.youtube.com/playlist?list=PL_test_456"
        assertEquals("PL_test_456", fetcher.extractPlaylistId(url))
    }

    @Test
    fun testExtractPlaylistId_shortUrl() {
        val url = "https://youtu.be/somevideo?list=PL_test_789"
        assertEquals("PL_test_789", fetcher.extractPlaylistId(url))
    }

    @Test
    fun testExtractPlaylistId_noHttp() {
        val url = "music.youtube.com/playlist?list=PL_test_nohttp"
        assertEquals("PL_test_nohttp", fetcher.extractPlaylistId(url))
    }

    @Test
    fun testExtractPlaylistId_multipleParams() {
        val url = "https://youtube.com/watch?v=abcd&list=PL_test_multi&index=2"
        assertEquals("PL_test_multi", fetcher.extractPlaylistId(url))
    }

    @Test
    fun testExtractPlaylistId_invalidUrl() {
        val url = "https://youtube.com/watch?v=abcd" // No list param
        assertNull(fetcher.extractPlaylistId(url))
    }

    @Test
    fun testFetchTracks_LZK() = kotlinx.coroutines.runBlocking {
        val url = "https://www.youtube.com/playlist?list=PL6Ui4jEbpx7DthiT2IKtQMyl6HadG6-_5"
        
        val result = fetcher.fetchTracks(url)
        assert(result.isSuccess)
        val tracks = result.getOrNull()
        println("LZK tracks size: ${tracks?.size}")
        assertEquals(66, tracks?.size)
        
        val firstTrack = tracks?.get(0)
        assertEquals("XgJKOXCTU6g", firstTrack?.videoId)
        assertEquals("Ich glaub ich", firstTrack?.title)
    }

    @Test
    fun testFetchMetadata_LZK() = kotlinx.coroutines.runBlocking {
        val url = "https://music.youtube.com/playlist?list=PL6Ui4jEbpx7DthiT2IKtQMyl6HadG6-_5"
        val result = fetcher.fetchMetadata(url)
        assert(result.isSuccess)
        val meta = result.getOrNull()
        println("LZK title: ${meta?.title}, trackCount: ${meta?.trackCount}, author: ${meta?.duration}")
        assertEquals("LZK", meta?.title)
        assertEquals("66 Songs", meta?.trackCount)
    }

    @Test
    fun testCleanVoiceQuery() {
        val clean = { q: String? -> SearchQueryCleaner.cleanVoiceQuery(q) }
        
        assertEquals("nur die nacht", clean("spiele nur die nacht auf playlist launcher"))
        assertEquals("levels", clean("play levels on playlist launcher"))
        assertEquals("standard", clean("suche nach standard auf youtube music"))
        assertEquals("some track", clean("some track"))
        assertEquals("launcher", clean("launcher"))
        assertEquals("play", clean("play"))
        assertEquals("nur die nacht", clean("suche spiele nur die nacht auf playlist launcher auf youtube music"))
        assertEquals("", clean(null))
        assertEquals("", clean("  "))
    }
}
