package app.pgpkeydesk.core

import java.time.Duration
import java.time.Instant

enum class KeyExpiryStatus { NEVER_EXPIRES, VALID, EXPIRING_SOON, EXPIRED }

object KeyExpiry {
    const val DEFAULT_WARN_DAYS = 30L

    fun evaluate(expiresAt: Instant?, now: Instant, warnDays: Long = DEFAULT_WARN_DAYS): KeyExpiryStatus = when {
        expiresAt == null -> KeyExpiryStatus.NEVER_EXPIRES
        !expiresAt.isAfter(now) -> KeyExpiryStatus.EXPIRED
        !expiresAt.isAfter(now.plus(Duration.ofDays(warnDays))) -> KeyExpiryStatus.EXPIRING_SOON
        else -> KeyExpiryStatus.VALID
    }

    fun daysLeft(expiresAt: Instant, now: Instant): Long =
        maxOf(0L, Math.ceil(Duration.between(now, expiresAt).toMillis() / 86_400_000.0).toLong())

    fun earliest(a: Instant?, b: Instant?): Instant? = when {
        a == null -> b
        b == null -> a
        else -> if (a <= b) a else b
    }
}
