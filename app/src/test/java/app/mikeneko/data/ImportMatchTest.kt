package app.mikeneko.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le choix du morceau à l'import (3.0.0) : plus de « même titre, autre chanson ». */
class ImportMatchTest {
    private fun score(title: String, artist: String, gotTitle: String, gotChannel: String) =
        PlaylistImport.matchScore(title, artist, gotTitle, gotChannel)

    @Test fun sameSongOnTopic() = assertEquals(1.0, score("Let's Go", "Jaden Bojsen", "Let's Go", "Jaden Bojsen - Topic"), 0.001)

    @Test fun sameTitleOtherArtistIsRejected() = assertEquals(0.0, score("Let's Go", "Jaden Bojsen", "Let's Go", "Calvin Harris"), 0.001)

    @Test fun oneOfSeveralArtistsIsEnough() =
        assertEquals(1.0, score("Let's Go", "Jaden Bojsen, David Guetta", "Let's Go", "David Guetta & Jaden Bojsen"), 0.001)

    @Test fun remixNotWantedIsRejected() = assertEquals(0.0, score("Levitating", "Dua Lipa", "Levitating (Remix)", "Dua Lipa"), 0.001)

    @Test fun liveWantedIsKept() = assertTrue(score("Creep - Live", "Radiohead", "Creep (Live)", "Radiohead") >= 0.6)

    @Test fun slowedNotWantedIsRejected() = assertEquals(0.0, score("Blinding Lights", "The Weeknd", "Blinding Lights (Slowed + Reverb)", "The Weeknd"), 0.001)

    @Test fun slowedWantedRejectsTheOriginal() =
        assertEquals(0.0, score("Golden Brown Slowed + reverb", "The Stranglers", "Golden Brown", "The Stranglers - Topic"), 0.001)

    @Test fun slowedWantedInVersionBracketRejectsTheOriginal() =
        assertEquals(0.0, score("Golden Brown (Slowed + Reverb Version)", "The Stranglers", "Golden Brown", "The Stranglers"), 0.001)

    @Test fun slowedWantedIsKept() =
        assertEquals(1.0, score("Golden Brown Slowed + reverb", "The Stranglers", "Golden Brown (Slowed + Reverb)", "The Stranglers"), 0.001)

    @Test fun slowedWantedAcceptsSlowedOnly() =
        assertTrue(score("Golden Brown Slowed + reverb", "The Stranglers", "Golden Brown (slowed)", "The Stranglers") >= 0.6)

    @Test fun liveWantedRejectsStudio() = assertEquals(0.0, score("Creep - Live", "Radiohead", "Creep", "Radiohead - Topic"), 0.001)

    @Test fun derivedWordInTheRealTitleIsFine() = assertEquals(1.0, score("Live Forever", "Oasis", "Live Forever", "Oasis - Topic"), 0.001)

    @Test fun officialVideoOnVevoIsKept() =
        assertEquals(1.0, score("Blinding Lights", "The Weeknd", "The Weeknd - Blinding Lights (Official Video)", "TheWeekndVEVO"), 0.001)

    @Test fun featInTitleIsKept() = assertEquals(1.0, score("Stay (feat. Justin Bieber)", "The Kid LAROI", "STAY", "The Kid LAROI - Topic"), 0.001)

    @Test fun longerOtherSongIsRejected() = assertTrue(score("Home", "Edward Sharpe", "Home Sweet Home Alabama Tonight", "Edward Sharpe") < 0.6)

    @Test fun accentsDoNotMatter() = assertEquals(1.0, score("Déjà Vu", "Beyoncé", "Deja Vu", "Beyonce - Topic"), 0.001)

    @Test fun articleAloneIsNotTheArtist() = assertEquals(0.0, score("Hello", "The Lumineers", "Hello", "The Kooks"), 0.001)

    @Test fun radioEditIsKept() = assertTrue(score("Titanium", "David Guetta", "Titanium (Radio Edit)", "David Guetta") >= 0.6)
}
