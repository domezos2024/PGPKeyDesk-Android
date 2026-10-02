package app.pgpkeydesk.core

enum class PgpError {
    WRONG_PASSPHRASE, NO_MATCHING_KEY, NOT_ENCRYPTED, INTEGRITY, KEY_UNUSABLE, NO_SIGNING_KEY, INVALID_INPUT, UNKNOWN
}

class PgpException(val error: PgpError, message: String, cause: Throwable? = null) : Exception(message, cause)
