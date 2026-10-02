package app.pgpkeydesk.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * List-of-items JSON file with atomic saves. An unreadable file is moved aside (never overwritten or silently
 * emptied) and reported through [loadWarning].
 */
open class JsonFileStore<T>(
    private val file: File,
    serializer: KSerializer<T>,
    private val protector: SecretProtector = NoopProtector
) {
    private val listSerializer = ListSerializer(serializer)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var items: MutableList<T>? = null

    /** File name of the moved-aside unreadable file, if the last load failed. */
    var loadWarning: String? = null
        private set

    protected fun all(): MutableList<T> {
        items?.let { return it }
        val loaded = load()
        items = loaded
        return loaded
    }

    private fun load(): MutableList<T> {
        loadWarning = null
        if (!file.exists()) return mutableListOf()
        return try {
            val text = protector.unprotect(file.readBytes()).toString(Charsets.UTF_8)
            json.decodeFromString(listSerializer, text).toMutableList()
        } catch (e: Exception) {
            val aside = File(file.parentFile, file.name + ".unreadable-" +
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC).format(Instant.now()))
            loadWarning = try {
                Files.move(file.toPath(), aside.toPath(), StandardCopyOption.REPLACE_EXISTING)
                aside.name
            } catch (_: Exception) {
                file.name
            }
            mutableListOf()
        }
    }

    protected fun persist() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(protector.protect(json.encodeToString(listSerializer, all()).toByteArray(Charsets.UTF_8)))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun reload() {
        items = null
    }
}
