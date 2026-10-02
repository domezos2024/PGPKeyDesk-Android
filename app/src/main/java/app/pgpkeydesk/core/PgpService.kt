package app.pgpkeydesk.core

import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.BCPGOutputStream
import org.bouncycastle.bcpg.CompressionAlgorithmTags
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.Features
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPCompressedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPOnePassSignature
import org.bouncycastle.openpgp.PGPOnePassSignatureList
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.SecureRandom
import java.time.Instant
import java.util.Date

enum class SignatureStatus { NOT_CHECKED, NOT_SIGNED, VALID, INVALID }

enum class KeyCheckLevel { OK, WARNING, BLOCKED }

enum class KeyCheckReason { NONE, INVALID, REVOKED, NO_ENCRYPTION_KEY, EXPIRED, EXPIRING_SOON }

data class KeyCheckResult(
    val level: KeyCheckLevel,
    val reason: KeyCheckReason = KeyCheckReason.NONE,
    val detail: String = "",
    val expiresAt: Instant? = null
)

data class DecryptResult(val text: String, val signature: SignatureStatus)

data class GeneratedKeys(val publicKey: String, val privateKey: String)

/** UI-independent OpenPGP operations (text and streams, signing and verification) on top of BouncyCastle. All calls block. */
object PgpService {
    private const val BUF = 1 shl 16
    private val random = SecureRandom()
    private val calc get() = BcKeyFingerprintCalculator()
    private val digests get() = BcPGPDigestCalculatorProvider()

    // ------------------------------------------------------------ key checks

    fun checkRecipientKey(publicKey: String, now: Instant) = checkRecipientKey(KeyValidator.validatePublicKey(publicKey), now)

    /** BouncyCastle happily encrypts to expired/revoked keys, so this is checked first. */
    fun checkRecipientKey(v: KeyValidationResult, now: Instant): KeyCheckResult = when {
        !v.isValid -> KeyCheckResult(KeyCheckLevel.BLOCKED, KeyCheckReason.INVALID, v.errorMessage)
        v.isRevoked -> KeyCheckResult(KeyCheckLevel.BLOCKED, KeyCheckReason.REVOKED)
        !v.hasEncryptionKey -> KeyCheckResult(KeyCheckLevel.BLOCKED, KeyCheckReason.NO_ENCRYPTION_KEY)
        else -> when (v.expiryStatus(now)) {
            KeyExpiryStatus.EXPIRED -> KeyCheckResult(KeyCheckLevel.BLOCKED, KeyCheckReason.EXPIRED, expiresAt = v.expiresAt)
            KeyExpiryStatus.EXPIRING_SOON -> KeyCheckResult(KeyCheckLevel.WARNING, KeyCheckReason.EXPIRING_SOON, expiresAt = v.expiresAt)
            else -> KeyCheckResult(KeyCheckLevel.OK)
        }
    }

    private fun requireUsable(publicKey: String) {
        val c = checkRecipientKey(publicKey, Instant.now())
        if (c.level == KeyCheckLevel.BLOCKED) throw PgpException(PgpError.KEY_UNUSABLE, "${c.reason} ${c.detail}".trim())
    }

    // ------------------------------------------------------------ text

    fun encryptText(
        plain: String, recipientPublicKey: String, signerPrivateKey: String? = null, signerPassphrase: String? = null
    ): String {
        val out = ByteArrayOutputStream()
        val data = plain.toByteArray(Charsets.UTF_8)
        encrypt(ByteArrayInputStream(data), out, recipientPublicKey, signerPrivateKey, signerPassphrase, armor = true, fileName = PGPLiteralData.CONSOLE)
        return out.toString(Charsets.UTF_8.name())
    }

    fun decryptText(cipherText: String, privateKey: String, passphrase: String, senderPublicKey: String? = null): DecryptResult {
        val out = ByteArrayOutputStream()
        val status = decrypt(ByteArrayInputStream(cipherText.toByteArray(Charsets.UTF_8)), out, privateKey, passphrase, senderPublicKey)
        return DecryptResult(out.toString(Charsets.UTF_8.name()), status)
    }

    /** Cleartext signature ("-----BEGIN PGP SIGNED MESSAGE-----"). */
    fun signText(text: String, signerPrivateKey: String, passphrase: String): String {
        val (secret, priv) = loadSigner(signerPrivateKey, passphrase)
        val lines = text.replace("\r\n", "\n").split('\n').map { it.trimEnd(' ', '\t', '\r') }
        val sigGen = newSignatureGenerator(secret, priv, PGPSignature.CANONICAL_TEXT_DOCUMENT)
        val bytes = ByteArrayOutputStream()
        val aOut = newArmor(bytes)
        aOut.beginClearText(HashAlgorithmTags.SHA256)
        lines.forEachIndexed { i, line ->
            val b = line.toByteArray(Charsets.UTF_8)
            if (i > 0) {
                sigGen.update('\r'.code.toByte())
                sigGen.update('\n'.code.toByte())
            }
            sigGen.update(b, 0, b.size)
            aOut.write(b)
            if (i < lines.lastIndex) aOut.write('\n'.code)
        }
        aOut.write('\n'.code)
        aOut.endClearText()
        sigGen.generate().encode(BCPGOutputStream(aOut))
        aOut.close()
        return bytes.toString(Charsets.UTF_8.name())
    }

    fun verifyText(clearSigned: String, signerPublicKey: String): Boolean = try {
        val aIn = ArmoredInputStream(ByteArrayInputStream(clearSigned.toByteArray(Charsets.UTF_8)))
        if (!aIn.isClearText) throw PgpException(PgpError.INVALID_INPUT, "not a cleartext signature")
        val raw = ByteArrayOutputStream()
        while (true) {
            val c = aIn.read()
            if (c < 0 || !aIn.isClearText) break
            raw.write(c)
        }
        val lines = raw.toString(Charsets.UTF_8.name()).replace("\r\n", "\n").removeSuffix("\n").split('\n')
            .map { it.trimEnd(' ', '\t', '\r') }
        val sig = (PGPObjectFactory(aIn, calc).nextObject() as PGPSignatureList)[0]
        val key = KeyValidator.readPublicRings(signerPublicKey).getPublicKey(sig.keyID)
        if (key == null) false else {
            sig.init(BcPGPContentVerifierBuilderProvider(), key)
            val canonical = lines.joinToString("\r\n").toByteArray(Charsets.UTF_8)
            sig.update(canonical, 0, canonical.size)
            sig.verify()
        }
    } catch (e: PgpException) {
        throw e
    } catch (e: Exception) {
        false
    }

    // ------------------------------------------------------------ streams (files)

    fun encrypt(
        input: InputStream, output: OutputStream, recipientPublicKey: String,
        signerPrivateKey: String? = null, signerPassphrase: String? = null,
        armor: Boolean = false, fileName: String = PGPLiteralData.CONSOLE
    ) {
        requireUsable(recipientPublicKey)
        val encKey = selectEncryptionKey(recipientPublicKey)
        val signer = if (signerPrivateKey.isNullOrEmpty()) null else loadSigner(signerPrivateKey, signerPassphrase.orEmpty())

        val armored = if (armor) newArmor(output) else null
        val target: OutputStream = armored ?: output
        val encGen = PGPEncryptedDataGenerator(
            BcPGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256).setWithIntegrityPacket(true).setSecureRandom(random)
        )
        encGen.addMethod(BcPublicKeyKeyEncryptionMethodGenerator(encKey))
        val encOut = encGen.open(target, ByteArray(BUF))
        val comp = PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP)
        val compOut = BCPGOutputStream(comp.open(encOut))
        val sigGen = signer?.let { (secret, priv) ->
            newSignatureGenerator(secret, priv, PGPSignature.BINARY_DOCUMENT).also { it.generateOnePassVersion(false).encode(compOut) }
        }
        val litOut = PGPLiteralDataGenerator().open(compOut, PGPLiteralData.BINARY, fileName, PGPLiteralData.NOW, ByteArray(BUF))
        copy(input, litOut) { b, n -> sigGen?.update(b, 0, n) }
        litOut.close()
        sigGen?.generate()?.encode(compOut)
        comp.close()
        encOut.close()
        armored?.close()
        output.flush()
    }

    /** Decrypts; if [senderPublicKey] is given, the embedded signature is verified as well. */
    fun decrypt(
        input: InputStream, output: OutputStream, privateKey: String, passphrase: String, senderPublicKey: String? = null
    ): SignatureStatus {
        val secrets = try {
            KeyValidator.readSecretRings(privateKey)
        } catch (e: Exception) {
            throw PgpException(PgpError.INVALID_INPUT, e.message ?: "invalid private key", e)
        }
        val senders = if (senderPublicKey.isNullOrBlank()) null else KeyValidator.readPublicRings(senderPublicKey)

        var factory: PGPObjectFactory
        var obj: Any?
        try {
            factory = PGPObjectFactory(PGPUtil.getDecoderStream(input), calc)
            obj = factory.nextObject()
            while (obj != null && obj !is PGPEncryptedDataList) obj = factory.nextObject()
        } catch (e: Exception) {
            throw PgpException(PgpError.NOT_ENCRYPTED, e.message ?: "not an OpenPGP message", e)
        }
        val encList = obj as? PGPEncryptedDataList ?: throw PgpException(PgpError.NOT_ENCRYPTED, "no encrypted data found")

        var privKey: PGPPrivateKey? = null
        var data: PGPPublicKeyEncryptedData? = null
        var keyMatched = false
        for (candidate in encList) {
            if (candidate !is PGPPublicKeyEncryptedData) continue
            val secret = secrets.getSecretKey(candidate.keyID) ?: continue
            keyMatched = true
            privKey = try {
                secret.extractPrivateKey(BcPBESecretKeyDecryptorBuilder(digests).build(passphrase.toCharArray()))
            } catch (e: PGPException) {
                throw PgpException(PgpError.WRONG_PASSPHRASE, "wrong passphrase", e)
            }
            data = candidate
            break
        }
        if (data == null || privKey == null) {
            throw PgpException(if (keyMatched) PgpError.WRONG_PASSPHRASE else PgpError.NO_MATCHING_KEY, "no matching secret key")
        }

        factory = PGPObjectFactory(data.getDataStream(BcPublicKeyDataDecryptorFactory(privKey)), calc)
        var onePass: PGPOnePassSignature? = null
        var sigSeen = false
        var verified = false
        var literalSeen = false
        var o = factory.nextObject()
        while (o != null) {
            when (o) {
                is PGPCompressedData -> {
                    factory = PGPObjectFactory(o.dataStream, calc)
                    o = factory.nextObject()
                    continue
                }
                is PGPOnePassSignatureList -> if (senders != null && !o.isEmpty) {
                    sigSeen = true
                    val ops = o[0]
                    val pub = senders.getPublicKey(ops.keyID)
                    if (pub != null) {
                        ops.init(BcPGPContentVerifierBuilderProvider(), pub)
                        onePass = ops
                    }
                }
                is PGPLiteralData -> {
                    literalSeen = true
                    copy(o.inputStream, output) { b, n -> onePass?.update(b, 0, n) }
                }
                is PGPSignatureList -> if (onePass != null && !o.isEmpty) verified = onePass.verify(o[0])
            }
            o = factory.nextObject()
        }
        if (!literalSeen) throw PgpException(PgpError.INVALID_INPUT, "no literal data in message")
        if (data.isIntegrityProtected && !data.verify()) throw PgpException(PgpError.INTEGRITY, "message was modified")
        output.flush()
        return when {
            senders == null -> SignatureStatus.NOT_CHECKED
            !sigSeen -> SignatureStatus.NOT_SIGNED
            verified -> SignatureStatus.VALID
            else -> SignatureStatus.INVALID
        }
    }

    /** Signs a stream (signature embedded in the output, content not encrypted). */
    fun sign(input: InputStream, output: OutputStream, signerPrivateKey: String, passphrase: String, armor: Boolean = true, fileName: String = PGPLiteralData.CONSOLE) {
        val (secret, priv) = loadSigner(signerPrivateKey, passphrase)
        val armored = if (armor) newArmor(output) else null
        val comp = PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP)
        val compOut = BCPGOutputStream(comp.open(armored ?: output))
        val sigGen = newSignatureGenerator(secret, priv, PGPSignature.BINARY_DOCUMENT)
        sigGen.generateOnePassVersion(false).encode(compOut)
        val litOut = PGPLiteralDataGenerator().open(compOut, PGPLiteralData.BINARY, fileName, PGPLiteralData.NOW, ByteArray(BUF))
        copy(input, litOut) { b, n -> sigGen.update(b, 0, n) }
        litOut.close()
        sigGen.generate().encode(compOut)
        comp.close()
        armored?.close()
        output.flush()
    }

    /** Verifies an embedded (one-pass) signature; the signed content is discarded. */
    fun verify(signed: InputStream, signerPublicKey: String): Boolean = try {
        val rings = KeyValidator.readPublicRings(signerPublicKey)
        var factory = PGPObjectFactory(PGPUtil.getDecoderStream(signed), calc)
        var onePass: PGPOnePassSignature? = null
        var result = false
        var o = factory.nextObject()
        while (o != null) {
            when (o) {
                is PGPCompressedData -> {
                    factory = PGPObjectFactory(o.dataStream, calc)
                    o = factory.nextObject()
                    continue
                }
                is PGPOnePassSignatureList -> {
                    val ops = o[0]
                    val pub = rings.getPublicKey(ops.keyID)
                    if (pub != null) {
                        ops.init(BcPGPContentVerifierBuilderProvider(), pub)
                        onePass = ops
                    }
                }
                is PGPLiteralData -> copy(o.inputStream, null) { b, n -> onePass?.update(b, 0, n) }
                is PGPSignatureList -> if (onePass != null && !o.isEmpty) result = onePass.verify(o[0])
            }
            o = factory.nextObject()
        }
        result
    } catch (e: Exception) {
        false
    }

    // ------------------------------------------------------------ keys

    /** RSA master key (sign/certify) plus RSA encryption subkey. [expiryDays] 0 = never expires. Slow for 4096 bit. */
    fun generateKeyPair(identity: String, passphrase: String, bits: Int = 2048, expiryDays: Int = 0): GeneratedKeys {
        require(bits == 2048 || bits == 3072 || bits == 4096) { "unsupported key size" }
        val gen = RSAKeyPairGenerator().apply {
            init(RSAKeyGenerationParameters(BigInteger.valueOf(0x10001), random, bits, 80))
        }
        val now = Date()
        val master = BcPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, gen.generateKeyPair(), now)
        val enc = BcPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, gen.generateKeyPair(), now)
        val sha1 = digests.get(HashAlgorithmTags.SHA1)
        val expirySeconds = expiryDays * 86_400L

        fun subpackets(flags: Int) = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(false, flags)
            setPreferredSymmetricAlgorithms(false, intArrayOf(SymmetricKeyAlgorithmTags.AES_256, SymmetricKeyAlgorithmTags.AES_192, SymmetricKeyAlgorithmTags.AES_128))
            setPreferredHashAlgorithms(false, intArrayOf(HashAlgorithmTags.SHA256, HashAlgorithmTags.SHA384, HashAlgorithmTags.SHA512, HashAlgorithmTags.SHA224))
            setPreferredCompressionAlgorithms(false, intArrayOf(CompressionAlgorithmTags.ZLIB, CompressionAlgorithmTags.BZIP2, CompressionAlgorithmTags.ZIP, CompressionAlgorithmTags.UNCOMPRESSED))
            setFeature(false, Features.FEATURE_MODIFICATION_DETECTION)
            if (expirySeconds > 0) setKeyExpirationTime(false, expirySeconds)
        }.generate()

        val encryptor = BcPBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, digests.get(HashAlgorithmTags.SHA256), 0x60)
            .build(passphrase.toCharArray())
        val ringGen = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION, master, identity, sha1,
            subpackets(KeyFlags.SIGN_DATA or KeyFlags.CERTIFY_OTHER), null,
            BcPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_GENERAL, HashAlgorithmTags.SHA256), encryptor
        )
        ringGen.addSubKey(enc, subpackets(KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE), null)
        return GeneratedKeys(
            publicKey = armor { ringGen.generatePublicKeyRing().encode(it) },
            privateKey = armor { ringGen.generateSecretKeyRing().encode(it) }
        )
    }

    /** True if [passphrase] unlocks the signing/master key of [privateKey]. */
    fun checkPassphrase(privateKey: String, passphrase: String): Boolean = try {
        loadSigner(privateKey, passphrase)
        true
    } catch (e: Exception) {
        false
    }

    // ------------------------------------------------------------ helpers

    private fun newArmor(out: OutputStream) = ArmoredOutputStream.builder().setVersion("PGPKeyDesk").build(out)

    internal fun armor(write: (OutputStream) -> Unit): String {
        val bytes = ByteArrayOutputStream()
        val a = newArmor(bytes)
        write(a)
        a.close()
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun selectEncryptionKey(publicKey: String): PGPPublicKey {
        val now = Instant.now()
        val candidates = KeyValidator.readPublicRings(publicKey).keyRings.asSequence()
            .flatMap { it.publicKeys.asSequence() }
            .filter { it.isEncryptionKey && !it.hasRevocation() && KeyValidator.expiryOf(it).let { e -> e == null || e.isAfter(now) } }
            .toList()
        return candidates.filter { !it.isMasterKey }.maxByOrNull { it.creationTime }
            ?: candidates.firstOrNull()
            ?: throw PgpException(PgpError.KEY_UNUSABLE, "no usable encryption key")
    }

    private fun loadSigner(privateKey: String, passphrase: String?): Pair<PGPSecretKey, PGPPrivateKey> {
        val ring = KeyValidator.readSecretRings(privateKey).keyRings.asSequence()
            .flatMap { it.secretKeys.asSequence() }.toList()
        val secret = ring.firstOrNull { it.isSigningKey && it.isMasterKey } ?: ring.firstOrNull { it.isSigningKey }
            ?: throw PgpException(PgpError.NO_SIGNING_KEY, "no signing key in profile")
        val priv = try {
            secret.extractPrivateKey(BcPBESecretKeyDecryptorBuilder(digests).build(passphrase.orEmpty().toCharArray()))
        } catch (e: PGPException) {
            throw PgpException(PgpError.WRONG_PASSPHRASE, "wrong passphrase", e)
        }
        return secret to priv
    }

    private fun newSignatureGenerator(secret: PGPSecretKey, priv: PGPPrivateKey, type: Int): PGPSignatureGenerator {
        val gen = PGPSignatureGenerator(BcPGPContentSignerBuilder(secret.publicKey.algorithm, HashAlgorithmTags.SHA256))
        gen.init(type, priv)
        secret.publicKey.userIDs.asSequence().firstOrNull()?.let {
            gen.setHashedSubpackets(PGPSignatureSubpacketGenerator().apply { addSignerUserID(false, it) }.generate())
        }
        return gen
    }

    private inline fun copy(input: InputStream, output: OutputStream?, onChunk: (ByteArray, Int) -> Unit) {
        val buf = ByteArray(BUF)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output?.write(buf, 0, n)
            onChunk(buf, n)
        }
    }
}
