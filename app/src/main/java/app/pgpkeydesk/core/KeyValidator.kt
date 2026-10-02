package app.pgpkeydesk.core

import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.util.encoders.Hex
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneOffset

data class KeyValidationResult(
    val isValid: Boolean,
    val errorMessage: String = "",
    val algorithm: String = "",
    val userId: String = "",
    val fingerprint: String = "",
    val createdOn: String = "",
    val expiresAt: Instant? = null,
    val isRevoked: Boolean = false,
    val hasEncryptionKey: Boolean = true
) {
    val keyInfo: String
        get() = listOf(algorithm, userId, createdOn).filter { it.isNotEmpty() }.joinToString("  ·  ")

    fun expiryStatus(now: Instant) = KeyExpiry.evaluate(expiresAt, now)
}

object KeyValidator {
    const val PRIVATE_HEADER = "-----BEGIN PGP PRIVATE KEY BLOCK-----"
    const val PUBLIC_HEADER = "-----BEGIN PGP PUBLIC KEY BLOCK-----"

    private val calc get() = BcKeyFingerprintCalculator()

    fun readPublicRings(text: String): PGPPublicKeyRingCollection =
        PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(ByteArrayInputStream(text.toByteArray())), calc)

    fun readSecretRings(text: String): PGPSecretKeyRingCollection =
        PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(ByteArrayInputStream(text.toByteArray())), calc)

    fun validatePrivateKey(text: String?): KeyValidationResult {
        if (text == null || !text.contains(PRIVATE_HEADER)) {
            return KeyValidationResult(false, "Missing $PRIVATE_HEADER header.")
        }
        return try {
            val master = readSecretRings(text).keyRings.asSequence()
                .flatMap { it.secretKeys.asSequence() }.firstOrNull { it.isMasterKey }
                ?: return KeyValidationResult(false, "No master key found in key block.")
            details(master.publicKey).copy(expiresAt = expiryOf(master.publicKey))
        } catch (e: Exception) {
            KeyValidationResult(false, e.message ?: e.javaClass.simpleName)
        }
    }

    fun validatePublicKey(text: String?): KeyValidationResult {
        if (text == null || !text.contains(PUBLIC_HEADER)) {
            return KeyValidationResult(false, "Missing $PUBLIC_HEADER header.")
        }
        return try {
            val rings = readPublicRings(text).keyRings.asSequence().toList()
            val ring = rings.firstOrNull { r -> r.publicKeys.asSequence().any { it.isMasterKey } }
                ?: return KeyValidationResult(false, "No master key found in key block.")
            val master = ring.publicKeys.asSequence().first { it.isMasterKey }
            encryptionExpiry(details(master), master, ring)
        } catch (e: Exception) {
            KeyValidationResult(false, e.message ?: e.javaClass.simpleName)
        }
    }

    /** Derives the armored public key from an armored private key. */
    fun publicKeyFromPrivate(privateKey: String): String {
        val ring = readSecretRings(privateKey).keyRings.next()
        val pub = PGPPublicKeyRing(ring.publicKeys.asSequence().toList())
        return PgpService.armor { pub.encode(it) }
    }

    private fun details(pub: PGPPublicKey): KeyValidationResult {
        val bits = if (pub.bitStrength > 0) " ${pub.bitStrength}" else ""
        return KeyValidationResult(
            isValid = true,
            algorithm = algorithmName(pub.algorithm) + bits,
            userId = pub.userIDs.asSequence().firstOrNull() ?: "",
            fingerprint = Hex.toHexString(pub.fingerprint).uppercase(),
            createdOn = pub.creationTime.toInstant().atZone(ZoneOffset.UTC).toLocalDate().toString(),
            isRevoked = pub.hasRevocation()
        )
    }

    fun expiryOf(key: PGPPublicKey): Instant? {
        val seconds = key.validSeconds
        return if (seconds <= 0) null else key.creationTime.toInstant().plusSeconds(seconds)
    }

    /** Effective expiry for encrypting: master must be valid and at least one non-revoked encryption key. */
    private fun encryptionExpiry(base: KeyValidationResult, master: PGPPublicKey, ring: PGPPublicKeyRing): KeyValidationResult {
        var found = false
        var neverExpires = false
        var latest: Instant? = null
        for (key in ring.publicKeys) {
            if (!key.isEncryptionKey || key.hasRevocation()) continue
            found = true
            val e = expiryOf(key)
            if (e == null) neverExpires = true else if (latest == null || e > latest) latest = e
        }
        if (neverExpires) latest = null
        return base.copy(hasEncryptionKey = found, expiresAt = KeyExpiry.earliest(expiryOf(master), if (found) latest else null))
    }

    private fun algorithmName(tag: Int) = when (tag) {
        PublicKeyAlgorithmTags.RSA_GENERAL, PublicKeyAlgorithmTags.RSA_ENCRYPT, PublicKeyAlgorithmTags.RSA_SIGN -> "RSA"
        PublicKeyAlgorithmTags.DSA -> "DSA"
        PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT, PublicKeyAlgorithmTags.ELGAMAL_GENERAL -> "ElGamal"
        PublicKeyAlgorithmTags.ECDH -> "ECDH"
        PublicKeyAlgorithmTags.ECDSA -> "ECDSA"
        PublicKeyAlgorithmTags.EDDSA_LEGACY -> "EdDSA"
        else -> "Key($tag)"
    }
}
