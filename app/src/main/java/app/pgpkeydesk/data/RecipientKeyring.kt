package app.pgpkeydesk.data

import app.pgpkeydesk.core.KeyValidator
import java.io.File

/** Recipients' public keys, de-duplicated by fingerprint. */
class RecipientKeyring(file: File) : JsonFileStore<RecipientKey>(file, RecipientKey.serializer()) {

    val keys: List<RecipientKey> get() = all().toList()

    fun findByFingerprint(fingerprint: String): RecipientKey? =
        all().firstOrNull { it.fingerprint.equals(fingerprint, ignoreCase = true) }

    fun addOrUpdate(name: String?, publicKeyText: String): RecipientKey {
        val v = KeyValidator.validatePublicKey(publicKeyText)
        require(v.isValid) { v.errorMessage }
        require(!v.isRevoked) { "This key has been revoked." }
        require(v.hasEncryptionKey) { "This key contains no encryption key." }
        val display = name?.trim().takeUnless { it.isNullOrEmpty() }
            ?: v.userId.ifEmpty { v.fingerprint }
        val list = all()
        val existing = findByFingerprint(v.fingerprint)
        val entry = if (existing != null) {
            existing.copy(name = display, publicKey = publicKeyText.trim()).also { list[list.indexOf(existing)] = it }
        } else {
            RecipientKey(name = display, fingerprint = v.fingerprint, publicKey = publicKeyText.trim()).also { list.add(it) }
        }
        persist()
        return entry
    }

    fun remove(id: String): Boolean {
        val removed = all().removeAll { it.id == id }
        if (removed) persist()
        return removed
    }
}
