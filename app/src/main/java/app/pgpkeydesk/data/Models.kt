package app.pgpkeydesk.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class PgpProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val privateKey: String,
    val publicKey: String = "",
    val createdAt: String = Instant.now().toString()
)

@Serializable
data class RecipientKey(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val fingerprint: String,
    val publicKey: String,
    val addedAt: String = Instant.now().toString()
)
