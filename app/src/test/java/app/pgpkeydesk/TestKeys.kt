package app.pgpkeydesk

import app.pgpkeydesk.core.GeneratedKeys
import app.pgpkeydesk.core.PgpService

object TestKeys {
    const val PASS = "correct horse"
    val alice: GeneratedKeys by lazy { PgpService.generateKeyPair("Alice (Demo) <alice@example.org>", PASS, 2048) }
    val bob: GeneratedKeys by lazy { PgpService.generateKeyPair("Bob (Demo) <bob@example.org>", PASS, 2048) }
    val shortLived: GeneratedKeys by lazy { PgpService.generateKeyPair("Soon (Demo)", PASS, 2048, expiryDays = 10) }
}
