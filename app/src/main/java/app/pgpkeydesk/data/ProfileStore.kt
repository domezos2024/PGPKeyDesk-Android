package app.pgpkeydesk.data

import app.pgpkeydesk.core.KeyValidator
import java.io.File

/** Profiles (private/public key pairs), encrypted at rest through [SecretProtector]. */
class ProfileStore(file: File, protector: SecretProtector) :
    JsonFileStore<PgpProfile>(file, PgpProfile.serializer(), protector) {

    val profiles: List<PgpProfile> get() = all().toList()

    fun get(id: String?): PgpProfile? = all().firstOrNull { it.id == id }

    fun add(name: String, privateKey: String, publicKey: String): PgpProfile {
        val v = KeyValidator.validatePrivateKey(privateKey)
        require(v.isValid) { v.errorMessage }
        if (publicKey.isNotBlank()) {
            val p = KeyValidator.validatePublicKey(publicKey)
            require(p.isValid) { p.errorMessage }
        }
        val profile = PgpProfile(name = name.trim(), privateKey = privateKey.trim(), publicKey = publicKey.trim())
        all().add(profile)
        persist()
        return profile
    }

    fun remove(id: String): Boolean {
        val removed = all().removeAll { it.id == id }
        if (removed) persist()
        return removed
    }
}
