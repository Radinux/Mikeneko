package app.mikeneko.cast

import org.junit.Assert.assertEquals
import org.junit.Test

/** Le codage protobuf des messages Cast (3.4.0). */
class CastMessageTest {
    @Test fun roundTrip() {
        val m = CastMessage.Message("sender-mikeneko", "receiver-0", CastMessage.NS_RECEIVER, """{"type":"LAUNCH","appId":"CC1AD845","requestId":1,"titre":"Écoute ça ♪"}""")
        assertEquals(m, CastMessage.decode(CastMessage.encode(m)))
    }

    @Test fun longPayloadUsesMultiByteLengths() {
        val m = CastMessage.Message("a", "b", CastMessage.NS_MEDIA, "x".repeat(70_000))
        assertEquals(m, CastMessage.decode(CastMessage.encode(m)))
    }

    @Test fun knownBytesFromTheProtocol() {
        // version 0, source « s », destination « d », espace « n », type texte, texte « p »
        val expected = byteArrayOf(0x08, 0x00, 0x12, 0x01, 's'.code.toByte(), 0x1a, 0x01, 'd'.code.toByte(), 0x22, 0x01, 'n'.code.toByte(), 0x28, 0x00, 0x32, 0x01, 'p'.code.toByte())
        assertEquals(expected.toList(), CastMessage.encode(CastMessage.Message("s", "d", "n", "p")).toList())
    }

    @Test fun binaryFieldsAreSkipped() {
        // Le même message, avec un champ 7 (binaire) en plus : ignoré
        val bytes = CastMessage.encode(CastMessage.Message("s", "d", "n", "p")) + byteArrayOf(0x3a, 0x02, 0x01, 0x02)
        assertEquals(CastMessage.Message("s", "d", "n", "p"), CastMessage.decode(bytes))
    }
}
