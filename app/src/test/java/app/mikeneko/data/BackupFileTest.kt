package app.mikeneko.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le fichier `.mikeneko` (3.6.1) et le bouton d'import qui reconnaît les fichiers. */
class BackupFileTest {
    private val video = VideoItem("https://www.youtube.com/watch?v=abcdefghijk", "Rain on leaves", "Calm Channel", "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg", durationSec = 300)
    private val live = VideoItem("https://www.youtube.com/watch?v=zyxwvutsrqp", "Forest live", "Nature", null, isLive = true)
    private val channelId = "UC" + "a".repeat(22)

    private fun sample() = MikenekoBackup.Content(
        subscriptions = listOf(Subscription("Calm Channel", "https://www.youtube.com/channel/$channelId", channelId, "https://avatar")),
        musicPlaylists = listOf("Chill, \"quoted\" & <odd>" to listOf(video, live)),
        videoPlaylists = listOf("Walks" to listOf(live)),
        albums = listOf(MusicCollection("https://music.youtube.com/browse/MPRE1", "Album", "Artist", null, 12)),
        artists = listOf(ChannelItem("https://music.youtube.com/channel/UCx", "Artist", null, "UCx")),
        favorites = listOf(video),
        later = listOf(live),
        liked = listOf(video),
        shorts = emptyList(),
        tastes = setOf("nature", "cooking"),
        tastesSkipped = true,
        history = listOf(WatchHistory.Entry(video.url, video.title, video.channel, channelId, 1_700_000_000_000, emptyList(), 0.75f, listOf("rain", "asmr"))),
        positions = listOf(WatchPositions.Saved("abcdefghijk", 42_000, 1_700_000_000_000, 300_000)),
        settings = mapOf(
            "reglages" to mapOf("amoled" to true, "dodo_minutes" to 45, "accent" to "sakura", "sponsorblock" to setOf("sponsor", "intro")),
            "lecteur" to mapOf("qualite" to 1080, "effet_audio" to "xstrm_bass", "x_long" to 7L, "x_float" to 0.5f),
        ),
    )

    private fun bytesOf(content: MikenekoBackup.Content): ByteArray =
        ByteArrayOutputStream().also { BackupFile.write(content, "3.6.1", "2026-11-16", it) }.toByteArray()

    @Test fun roundTripKeepsEverything() {
        val original = sample()
        val (meta, read) = BackupFile.read(bytesOf(original))!!
        assertEquals("3.6.1", meta.app)
        assertEquals("2026-11-16", meta.date)
        assertEquals("3.6.1", read.app)
        assertEquals(original.subscriptions, read.subscriptions)
        fun shape(lists: List<Pair<String, List<VideoItem>>>) = lists.map { (n, v) -> n to v.map { listOf(it.url, it.title, it.channel, it.durationSec, it.isLive) } }
        assertEquals(shape(original.musicPlaylists), shape(read.musicPlaylists))
        assertEquals(shape(original.videoPlaylists), shape(read.videoPlaylists))
        assertEquals(original.albums, read.albums)
        assertEquals(original.artists, read.artists)
        assertEquals(original.favorites, read.favorites)
        assertEquals(original.later.map { it.url }, read.later.map { it.url })
        assertEquals(original.liked, read.liked)
        assertEquals(original.tastes, read.tastes)
        assertTrue(read.tastesSkipped)
        assertEquals(original.history, read.history)
        assertEquals(original.positions, read.positions)
        assertEquals(original.settings, read.settings)
        // Une vidéo sans miniature retrouve celle de YouTube
        assertEquals(thumbnailFor(live.url), read.later.single().thumbnail)
    }

    @Test fun onlyKeepsTheChosenParts() {
        val content = sample()
        val some = BackupFile.only(content, setOf(BackupFile.Part.Subscriptions, BackupFile.Part.History))
        assertEquals(1, some.subscriptions.size)
        assertEquals(1, some.history.size)
        assertEquals(content.tastes, some.tastes)
        assertTrue(some.musicPlaylists.isEmpty() && some.favorites.isEmpty() && some.albums.isEmpty())
        assertNull(some.settings)
        assertEquals(content.settings, BackupFile.only(content, setOf(BackupFile.Part.Settings)).settings)
        assertEquals(2, BackupFile.count(content, BackupFile.Part.MusicPlaylists))
        assertEquals(8, BackupFile.count(content, BackupFile.Part.Settings))
    }

    @Test fun detectsABackup() {
        val found = Importer.detect(bytesOf(sample()), "whatever.zip")
        assertTrue(found is Importer.Found.Backup)
        assertEquals(1, (found as Importer.Found.Backup).content.subscriptions.size)
    }

    @Test fun anotherZipIsNotABackup() {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry("photo.jpg")); it.write(ByteArray(10)); it.closeEntry() }
        }.toByteArray()
        assertTrue(Importer.detect(zip, "archive.zip") is Importer.Found.Unknown)
    }

    @Test fun detectsSubscriptions() {
        val newPipe = """{"app_version":"0.27","subscriptions":[{"service_id":0,"url":"https://www.youtube.com/channel/$channelId","name":"Calm"}]}"""
        val takeout = "Channel Id,Channel Url,Channel Title\n$channelId,http://www.youtube.com/channel/$channelId,Calm\n"
        for ((text, name) in listOf(newPipe to "subscriptions.json", takeout to "abonnements.csv")) {
            val found = Importer.detect(text.toByteArray(), name)
            assertTrue("$name : $found", found is Importer.Found.Subscriptions)
            assertEquals(channelId, (found as Importer.Found.Subscriptions).list.single().channelId)
        }
    }

    @Test fun detectsAMusicExport() {
        val csv = "Track name,Artist name,Album,Playlist name,Type,ISRC\nRain Song,Calm Artist,Album,Chill,Playlist,XX\nWind Song,Calm Artist,Album,Chill,Playlist,YY\n"
        val found = Importer.detect(csv.toByteArray(), "My Library.csv")
        assertTrue(found.toString(), found is Importer.Found.Playlists)
        assertEquals(2, (found as Importer.Found.Playlists).preview.items.size)
    }

    @Test fun binaryIsUnknown() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(2000) { (it % 7).toByte() }
        assertTrue(Importer.detect(png, "cat.png") is Importer.Found.Unknown)
    }
}
