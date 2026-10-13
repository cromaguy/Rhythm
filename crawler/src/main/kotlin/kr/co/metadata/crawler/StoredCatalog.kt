// SPDX-License-Identifier: GPL-3.0-or-later
package kr.co.metadata.crawler

import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Persistent, field-level records live outside a cache directory. */
data class StoredCatalog(val document: CatalogDocument, val retryAfter: Long = 0,
                         val fetchedAt: Long = System.currentTimeMillis(), val imageFile: String? = null, val attemptedFields: Set<CatalogField> = emptySet())

class CatalogFileStore(private val root: File) {
    companion object {
        private val locks = ConcurrentHashMap<String, Any>()
        fun hash(key: String): String = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun file(kind: EntityKind, key: String) = File(root, "${kind.name.lowercase()}-${hash(key)}.json")
    fun read(kind: EntityKind, key: String): StoredCatalog? = synchronized(locks.computeIfAbsent(file(kind, key).path) { Any() }) {
        val file = file(kind, key)
        if (!file.isFile || file.length() > 2_000_000) return@synchronized null
        runCatching {
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            require(json["version"]?.jsonPrimitive?.int == 1)
            val document = CatalogDocumentCodec.decode(json.getValue("document").toString())
            require(document.kind == kind)
            StoredCatalog(document, json["retryAfter"]?.jsonPrimitive?.long ?: 0,
                json["fetchedAt"]?.jsonPrimitive?.long ?: 0, json["imageFile"]?.jsonPrimitive?.contentOrNull,
                json["attemptedFields"]?.jsonArray.orEmpty().map { CatalogField.valueOf(it.jsonPrimitive.content) }.toSet())
        }.getOrNull()
    }
    fun merge(kind: EntityKind, key: String, incoming: CatalogDocument, retryAfter: Long = 0,
              imageFile: String? = null, replace: Boolean = false, attemptedFields: Set<CatalogField> = emptySet()): StoredCatalog = synchronized(locks.computeIfAbsent(file(kind, key).path) { Any() }) {
        require(incoming.kind == kind)
        val existing = read(kind, key)
        val fields = existing?.document?.values.orEmpty().associateBy { it.field }.toMutableMap()
        incoming.values.filter { replace || it.hasValue() }.forEach {
            if (replace || fields[it.field]?.hasValue() != true) fields[it.field] = it
        }
        val result = StoredCatalog(CatalogDocument(kind, fields.values.toList()), retryAfter,
            imageFile = imageFile ?: existing?.imageFile, attemptedFields = attemptedFields + existing?.attemptedFields.orEmpty())
        root.mkdirs()
        val content = buildJsonObject {
            put("version", 1); put("retryAfter", result.retryAfter); put("fetchedAt", result.fetchedAt)
            put("document", Json.parseToJsonElement(CatalogDocumentCodec.encode(result.document)))
            result.imageFile?.let { put("imageFile", it) }
            put("attemptedFields", JsonArray(result.attemptedFields.map { JsonPrimitive(it.name) }))
        }.toString()
        val destination = file(kind, key)
        val temp = File.createTempFile("record-", ".tmp", root)
        try {
            temp.outputStream().use { stream -> stream.write(content.toByteArray(Charsets.UTF_8)); stream.fd.sync() }
            check(temp.renameTo(destination)) { "Could not commit catalog record" }
        } finally { temp.delete() }
        result
    }
}
