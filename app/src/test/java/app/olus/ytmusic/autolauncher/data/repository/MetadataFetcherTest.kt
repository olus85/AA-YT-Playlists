package app.olus.ytmusic.autolauncher.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
