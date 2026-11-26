package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadsTest {
    private fun item(id: String, status: DownloadStatus = DownloadStatus.DONE, total: Long = 100, received: Long = 100) =
        DownloadItem(id, "$id.pdf", "application/pdf", "content://downloads/$id", total, received, status, 1L, "https://x.test/$id.pdf")

    @Test
    fun `newest download comes first and replaces its earlier entry`() {
        val list = Downloads.upsert(listOf(item("a"), item("b")), item("b", DownloadStatus.FAILED))
        assertEquals(listOf("b", "a"), list.map { it.id })
        assertEquals(DownloadStatus.FAILED, list[0].status)
    }

    @Test
    fun `downloads still running when the app stopped show as failed`() {
        val list = Downloads.afterRestart(listOf(item("a", DownloadStatus.RUNNING), item("b")))
        assertEquals(listOf(DownloadStatus.FAILED, DownloadStatus.DONE), list.map { it.status })
    }

    @Test
    fun `progress is known only when the size is`() {
        assertEquals(0.5f, item("a", total = 200, received = 100).progress)
        assertNull(item("a", total = 0, received = 100).progress)
    }

    @Test
    fun `sizes read like a file manager`() {
        assertEquals("512 B", Downloads.size(512))
        assertEquals("2 KB", Downloads.size(2048))
        assertEquals("1.5 MB", Downloads.size(1_572_864))
    }

    @Test
    fun `the list survives a round trip`() {
        val list = listOf(item("a"), item("b", DownloadStatus.RUNNING, total = 0, received = 5))
        assertEquals(list, Downloads.decode(Downloads.encode(list)))
    }
}
