package dev.ryanness.lightsketch.data

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val TRASH_RETENTION_MS = TimeUnit.DAYS.toMillis(30)

/**
 * File-based persistence: one JSON file per document under `sketches/`, plus a small
 * `index.json` with just the metadata so the home screen list never has to parse stroke data.
 * Deleting a document soft-deletes it (stamps `deletedAtEpochMs`); it only disappears from
 * disk once permanently deleted or purged by [purgeExpiredTrash].
 */
class SketchRepository(filesDir: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(filesDir, "sketches").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    private fun documentFile(id: String) = File(dir, "$id.json")

    suspend fun listDocuments(): List<SketchDocumentMeta> = withContext(Dispatchers.IO) {
        readIndex().documents.filter { it.deletedAtEpochMs == null }.sortedByDescending { it.modifiedAtEpochMs }
    }

    suspend fun listTrash(): List<SketchDocumentMeta> = withContext(Dispatchers.IO) {
        readIndex().documents.filter { it.deletedAtEpochMs != null }.sortedByDescending { it.deletedAtEpochMs }
    }

    suspend fun isAutoEmptyTrashEnabled(): Boolean = withContext(Dispatchers.IO) {
        readIndex().autoEmptyTrashEnabled
    }

    suspend fun setAutoEmptyTrashEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        writeIndex(readIndex().copy(autoEmptyTrashEnabled = enabled))
    }

    suspend fun loadDocument(id: String): SketchDocumentData? = withContext(Dispatchers.IO) {
        val file = documentFile(id)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<SketchDocumentData>(file.readText()) }.getOrNull()
    }

    suspend fun createDocument(name: String): SketchDocumentData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val document = SketchDocumentData(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAtEpochMs = now,
            modifiedAtEpochMs = now,
        )
        saveDocument(document)
        document
    }

    suspend fun saveDocument(document: SketchDocumentData) = withContext(Dispatchers.IO) {
        documentFile(document.id).writeText(json.encodeToString(SketchDocumentData.serializer(), document))
        val index = readIndex()
        val meta = SketchDocumentMeta(
            id = document.id,
            name = document.name,
            createdAtEpochMs = document.createdAtEpochMs,
            modifiedAtEpochMs = document.modifiedAtEpochMs,
            deletedAtEpochMs = document.deletedAtEpochMs,
        )
        val updated = index.documents.filterNot { it.id == document.id } + meta
        writeIndex(index.copy(documents = updated))
    }

    suspend fun moveToTrash(id: String) = withContext(Dispatchers.IO) {
        val doc = loadDocument(id) ?: return@withContext
        saveDocument(doc.copy(deletedAtEpochMs = System.currentTimeMillis()))
    }

    suspend fun restoreFromTrash(id: String) = withContext(Dispatchers.IO) {
        val doc = loadDocument(id) ?: return@withContext
        saveDocument(doc.copy(deletedAtEpochMs = null))
    }

    suspend fun permanentlyDelete(id: String) = withContext(Dispatchers.IO) {
        documentFile(id).delete()
        val index = readIndex()
        writeIndex(index.copy(documents = index.documents.filterNot { it.id == id }))
    }

    /** Permanently deletes anything trashed more than 30 days ago. Returns how many were purged. */
    suspend fun purgeExpiredTrash(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val index = readIndex()
        val expired = index.documents.filter {
            it.deletedAtEpochMs != null && now - it.deletedAtEpochMs > TRASH_RETENTION_MS
        }
        expired.forEach { documentFile(it.id).delete() }
        if (expired.isNotEmpty()) {
            writeIndex(index.copy(documents = index.documents - expired.toSet()))
        }
        expired.size
    }

    private fun readIndex(): SketchIndexData {
        if (!indexFile.exists()) return SketchIndexData()
        return runCatching { json.decodeFromString<SketchIndexData>(indexFile.readText()) }.getOrDefault(SketchIndexData())
    }

    private fun writeIndex(index: SketchIndexData) {
        indexFile.writeText(json.encodeToString(SketchIndexData.serializer(), index))
    }
}
