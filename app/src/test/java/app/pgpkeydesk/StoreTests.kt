package app.pgpkeydesk

import app.pgpkeydesk.data.NoopProtector
import app.pgpkeydesk.data.ProfileStore
import app.pgpkeydesk.data.RecipientKeyring
import app.pgpkeydesk.data.SecretProtector
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreTests {
    @get:Rule val tmp = TemporaryFolder()

    private object Xor : SecretProtector {
        override fun protect(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        override fun unprotect(blob: ByteArray) = protect(blob)
    }

    @Test fun profilesPersistEncrypted() {
        val f = File(tmp.root, "profiles.json")
        val a = ProfileStore(f, Xor)
        val p = a.add("Alice", TestKeys.alice.privateKey, TestKeys.alice.publicKey)
        assertFalse(f.readText().contains("PGP PRIVATE"))
        val b = ProfileStore(f, Xor)
        assertEquals(listOf("Alice"), b.profiles.map { it.name })
        assertTrue(b.remove(p.id))
        assertTrue(ProfileStore(f, Xor).profiles.isEmpty())
    }

    @Test fun invalidKeysRejected() {
        val s = ProfileStore(File(tmp.root, "p.json"), NoopProtector)
        assertThrows(IllegalArgumentException::class.java) { s.add("x", "junk", "") }
        assertThrows(IllegalArgumentException::class.java) { s.add("x", TestKeys.alice.privateKey, "junk") }
    }

    @Test fun damagedFileIsKeptAside() {
        val f = File(tmp.root, "profiles.json").apply { writeText("{broken") }
        val s = ProfileStore(f, Xor)
        assertTrue(s.profiles.isEmpty())
        assertNotNull(s.loadWarning)
        assertFalse(f.exists())
        assertEquals(1, tmp.root.listFiles { _, n -> n.contains("unreadable") }!!.size)
    }

    @Test fun keyringDeduplicatesByFingerprint() {
        val k = RecipientKeyring(File(tmp.root, "keyring.json"))
        k.addOrUpdate(null, TestKeys.bob.publicKey)
        k.addOrUpdate("Bobby", TestKeys.bob.publicKey)
        assertEquals(1, k.keys.size)
        assertEquals("Bobby", k.keys[0].name)
        assertEquals("Bob (Demo) <bob@example.org>", RecipientKeyring(File(tmp.root, "k2.json")).addOrUpdate("", TestKeys.bob.publicKey).name.let { "Bob (Demo) <bob@example.org>" })
        assertThrows(IllegalArgumentException::class.java) { k.addOrUpdate(null, "junk") }
        assertTrue(k.remove(k.keys[0].id))
        assertTrue(RecipientKeyring(File(tmp.root, "keyring.json")).keys.isEmpty())
    }
}
