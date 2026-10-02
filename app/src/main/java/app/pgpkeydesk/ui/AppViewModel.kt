package app.pgpkeydesk.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import app.pgpkeydesk.core.KeyExpiryStatus
import app.pgpkeydesk.core.KeyValidator
import app.pgpkeydesk.data.KeystoreSecretProtector
import app.pgpkeydesk.data.PgpProfile
import app.pgpkeydesk.data.ProfileStore
import app.pgpkeydesk.data.RecipientKey
import app.pgpkeydesk.data.RecipientKeyring
import java.io.File
import java.time.Instant

enum class ProfilesAction { GENERATE, IMPORT }

data class ExpiryWarning(val name: String, val status: KeyExpiryStatus, val expiresAt: Instant)

/** Content received from outside (share sheet / "open with"). */
data class Incoming(val text: String? = null, val file: Uri? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dir = File(app.filesDir, "data")
    private val prefs = app.getSharedPreferences("pgpkeydesk", 0)
    private val profileStore = ProfileStore(File(dir, "profiles.json"), KeystoreSecretProtector())
    private val keyring = RecipientKeyring(File(dir, "keyring.json"))

    val profiles = mutableStateListOf<PgpProfile>()
    val keys = mutableStateListOf<RecipientKey>()
    var selectedProfileId by mutableStateOf<String?>(null)
        private set
    var storageWarning by mutableStateOf<String?>(null)
        private set
    val expiryWarnings = mutableStateListOf<ExpiryWarning>()
    var incoming by mutableStateOf<Incoming?>(null)
    var profilesAction by mutableStateOf<ProfilesAction?>(null)

    val selectedProfile: PgpProfile? get() = profiles.firstOrNull { it.id == selectedProfileId }

    init {
        refresh()
        selectedProfileId = prefs.getString("selected", null)?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id
        computeExpiryWarnings()
    }

    private fun refresh() {
        profiles.clear(); profiles.addAll(profileStore.profiles)
        keys.clear(); keys.addAll(keyring.keys)
        storageWarning = listOfNotNull(profileStore.loadWarning, keyring.loadWarning).firstOrNull()
    }

    fun select(id: String?) {
        selectedProfileId = id
        prefs.edit().putString("selected", id).apply()
    }

    fun addProfile(name: String, privateKey: String, publicKey: String): PgpProfile {
        val p = profileStore.add(name, privateKey, publicKey)
        refresh()
        select(p.id)
        return p
    }

    fun removeProfile(id: String) {
        profileStore.remove(id)
        refresh()
        if (selectedProfileId == id) select(profiles.firstOrNull()?.id)
    }

    fun addKey(name: String?, publicKey: String): RecipientKey = keyring.addOrUpdate(name, publicKey).also { refresh() }

    fun removeKey(id: String) {
        keyring.remove(id)
        refresh()
    }

    fun dismissExpiryWarnings() = expiryWarnings.clear()

    fun dismissStorageWarning() {
        storageWarning = null
    }

    /** Public key of a profile: stored one, or derived from the private key. */
    fun publicKeyOf(p: PgpProfile): String =
        p.publicKey.ifBlank { runCatching { KeyValidator.publicKeyFromPrivate(p.privateKey) }.getOrDefault("") }

    private fun computeExpiryWarnings() {
        val now = Instant.now()
        fun check(name: String, v: app.pgpkeydesk.core.KeyValidationResult) {
            val st = v.expiryStatus(now)
            val at = v.expiresAt
            if (v.isValid && at != null && (st == KeyExpiryStatus.EXPIRED || st == KeyExpiryStatus.EXPIRING_SOON)) {
                expiryWarnings.add(ExpiryWarning(name, st, at))
            }
        }
        profiles.forEach { check(it.name, KeyValidator.validatePrivateKey(it.privateKey)) }
        keys.forEach { check(it.name, KeyValidator.validatePublicKey(it.publicKey)) }
    }
}
