package app.pgpkeydesk

import app.pgpkeydesk.core.KeyExpiry
import app.pgpkeydesk.core.KeyExpiryStatus
import app.pgpkeydesk.core.KeyValidator
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class KeyValidatorTest {
    @Test fun privateKeyDetails() {
        val v = KeyValidator.validatePrivateKey(TestKeys.alice.privateKey)
        assertTrue(v.isValid)
        assertEquals("Alice (Demo) <alice@example.org>", v.userId)
        assertEquals(40, v.fingerprint.length)
        assertTrue(v.algorithm.startsWith("RSA 2048"))
        assertNull(v.expiresAt)
    }

    @Test fun publicKeyDetailsAndExpiry() {
        assertNull(KeyValidator.validatePublicKey(TestKeys.bob.publicKey).expiresAt)
        val v = KeyValidator.validatePublicKey(TestKeys.shortLived.publicKey)
        assertNotNull(v.expiresAt)
        assertEquals(KeyExpiryStatus.EXPIRING_SOON, v.expiryStatus(Instant.now()))
    }

    @Test fun headersAreRequired() {
        assertFalse(KeyValidator.validatePrivateKey(TestKeys.bob.publicKey).isValid)
        assertFalse(KeyValidator.validatePublicKey(TestKeys.bob.privateKey).isValid)
        assertFalse(KeyValidator.validatePublicKey(null).isValid)
        assertFalse(KeyValidator.validatePublicKey("-----BEGIN PGP PUBLIC KEY BLOCK-----\n\nAAAA\n-----END PGP PUBLIC KEY BLOCK-----").isValid)
    }

    @Test fun publicKeyFromPrivate() {
        val pub = KeyValidator.publicKeyFromPrivate(TestKeys.alice.privateKey)
        assertEquals(KeyValidator.validatePublicKey(TestKeys.alice.publicKey).fingerprint, KeyValidator.validatePublicKey(pub).fingerprint)
    }

    @Test fun expiryEvaluation() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        assertEquals(KeyExpiryStatus.NEVER_EXPIRES, KeyExpiry.evaluate(null, now))
        assertEquals(KeyExpiryStatus.EXPIRED, KeyExpiry.evaluate(now, now))
        assertEquals(KeyExpiryStatus.EXPIRING_SOON, KeyExpiry.evaluate(now.plusSeconds(86400L * 30), now))
        assertEquals(KeyExpiryStatus.VALID, KeyExpiry.evaluate(now.plusSeconds(86400L * 31), now))
        assertEquals(1L, KeyExpiry.daysLeft(now.plusSeconds(3600), now))
        assertEquals(now, KeyExpiry.earliest(now, now.plusSeconds(5)))
        assertEquals(now, KeyExpiry.earliest(null, now))
    }
}
