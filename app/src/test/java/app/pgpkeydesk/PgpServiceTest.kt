package app.pgpkeydesk

import app.pgpkeydesk.core.KeyCheckLevel
import app.pgpkeydesk.core.KeyCheckReason
import app.pgpkeydesk.core.PgpError
import app.pgpkeydesk.core.PgpException
import app.pgpkeydesk.core.PgpService
import app.pgpkeydesk.core.SignatureStatus
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

class PgpServiceTest {
    private val alice = TestKeys.alice
    private val bob = TestKeys.bob
    private val pass = TestKeys.PASS

    @Test fun roundTripWithoutSignature() {
        val c = PgpService.encryptText("Hallo Welt äöü €", bob.publicKey)
        assertTrue(c.contains("BEGIN PGP MESSAGE"))
        val r = PgpService.decryptText(c, bob.privateKey, pass)
        assertEquals("Hallo Welt äöü €", r.text)
        assertEquals(SignatureStatus.NOT_CHECKED, r.signature)
    }

    @Test fun signedMessageIsVerified() {
        val c = PgpService.encryptText("secret", bob.publicKey, alice.privateKey, pass)
        assertEquals(SignatureStatus.VALID, PgpService.decryptText(c, bob.privateKey, pass, alice.publicKey).signature)
        assertEquals(SignatureStatus.INVALID, PgpService.decryptText(c, bob.privateKey, pass, bob.publicKey).signature)
        assertEquals("secret", PgpService.decryptText(c, bob.privateKey, pass, bob.publicKey).text)
    }

    @Test fun unsignedMessageIsReportedAsNotSigned() {
        val c = PgpService.encryptText("x", bob.publicKey)
        assertEquals(SignatureStatus.NOT_SIGNED, PgpService.decryptText(c, bob.privateKey, pass, alice.publicKey).signature)
    }

    @Test fun wrongPassphrase() {
        val c = PgpService.encryptText("x", bob.publicKey)
        val e = assertThrows(PgpException::class.java) { PgpService.decryptText(c, bob.privateKey, "nope") }
        assertEquals(PgpError.WRONG_PASSPHRASE, e.error)
    }

    @Test fun wrongKey() {
        val c = PgpService.encryptText("x", bob.publicKey)
        val e = assertThrows(PgpException::class.java) { PgpService.decryptText(c, alice.privateKey, pass) }
        assertEquals(PgpError.NO_MATCHING_KEY, e.error)
    }

    @Test fun garbageIsRejected() {
        val e = assertThrows(PgpException::class.java) { PgpService.decryptText("hello", bob.privateKey, pass) }
        assertEquals(PgpError.NOT_ENCRYPTED, e.error)
    }

    @Test fun binaryFileRoundTrip() {
        val data = ByteArray(300_000) { (it * 31).toByte() }
        for (armor in listOf(false, true)) {
            val enc = ByteArrayOutputStream()
            PgpService.encrypt(ByteArrayInputStream(data), enc, bob.publicKey, alice.privateKey, pass, armor, "f.bin")
            val dec = ByteArrayOutputStream()
            val st = PgpService.decrypt(ByteArrayInputStream(enc.toByteArray()), dec, bob.privateKey, pass, alice.publicKey)
            assertEquals(SignatureStatus.VALID, st)
            assertArrayEquals(data, dec.toByteArray())
        }
    }

    @Test fun signAndVerifyFile() {
        val data = "content".toByteArray()
        val signed = ByteArrayOutputStream()
        PgpService.sign(ByteArrayInputStream(data), signed, alice.privateKey, pass)
        assertTrue(PgpService.verify(ByteArrayInputStream(signed.toByteArray()), alice.publicKey))
        assertFalse(PgpService.verify(ByteArrayInputStream(signed.toByteArray()), bob.publicKey))
    }

    @Test fun clearSignRoundTrip() {
        val text = "line one  \n- dashed line\n\nlast"
        val s = PgpService.signText(text, alice.privateKey, pass)
        assertTrue(s.startsWith("-----BEGIN PGP SIGNED MESSAGE-----"))
        assertTrue(PgpService.verifyText(s, alice.publicKey))
        assertFalse(PgpService.verifyText(s, bob.publicKey))
        assertFalse(PgpService.verifyText(s.replace("last", "LAST"), alice.publicKey))
    }

    @Test fun signWithWrongPassphraseFails() {
        val e = assertThrows(PgpException::class.java) { PgpService.signText("x", alice.privateKey, "bad") }
        assertEquals(PgpError.WRONG_PASSPHRASE, e.error)
    }

    @Test fun recipientChecks() {
        val now = Instant.now()
        assertEquals(KeyCheckLevel.OK, PgpService.checkRecipientKey(bob.publicKey, now).level)
        assertEquals(KeyCheckLevel.BLOCKED, PgpService.checkRecipientKey("nonsense", now).level)
        val soon = PgpService.checkRecipientKey(TestKeys.shortLived.publicKey, now)
        assertEquals(KeyCheckLevel.WARNING, soon.level)
        assertEquals(KeyCheckReason.EXPIRING_SOON, soon.reason)
        val gone = PgpService.checkRecipientKey(TestKeys.shortLived.publicKey, now.plusSeconds(11 * 86400L))
        assertEquals(KeyCheckReason.EXPIRED, gone.reason)
        assertThrows(PgpException::class.java) { PgpService.encryptText("x", "junk") }
    }

    @Test fun checkPassphrase() {
        assertTrue(PgpService.checkPassphrase(alice.privateKey, pass))
        assertFalse(PgpService.checkPassphrase(alice.privateKey, "x"))
    }
}
