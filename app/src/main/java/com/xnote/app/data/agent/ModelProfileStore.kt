package com.xnote.app.data.agent

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.xnote.app.data.db.ModelProfileEntity
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

// -- Type Definitions

class ModelProfileStore(private val database: XNoteDatabase, private val credentials: ModelCredentialStore) {
    // -- Derived Values

    val profiles = database.agent().observeProfiles().map { rows -> rows.map { Json.decodeFromString<ModelProfile>(it.profileJson) } }

    // -- Functions

    suspend fun list(): List<ModelProfile> = database.agent().profiles().map { Json.decodeFromString(it.profileJson) }

    suspend fun active(): ModelProfile = list().singleOrNull { it.isDefault && it.enabled }
        ?: throw ModelException(ModelError.InvalidConfig)

    suspend fun credential(profile: ModelProfile): String = withContext(Dispatchers.IO) {
        val row = database.agent().profiles().singleOrNull { it.id == profile.id } ?: throw ModelException(ModelError.InvalidConfig)
        if (Json.decodeFromString<ModelProfile>(row.profileJson).version != profile.version) throw ModelException(ModelError.InvalidConfig)
        credentials.read(row.credentialReference)
    }

    suspend fun save(value: ModelProfile, newSecret: String?): ModelProfile = withContext(Dispatchers.IO) {
        value.validate()
        var obsoleteReference: String? = null
        val result = database.useWriterConnection { connection -> connection.immediateTransaction {
            val rows = database.agent().profiles()
            val oldRow = rows.find { it.id == value.id }
            val old = oldRow?.let { Json.decodeFromString<ModelProfile>(it.profileJson) }
            val changingDefault = value.isDefault && old?.isDefault != true
            ensureUnlocked(value.id, changingDefault)
            val reference = if (newSecret != null) UUID.randomUUID().toString() else
                oldRow?.credentialReference ?: throw ModelException(ModelError.MissingCredential)
            val sameService = old != null && old.providerId == value.providerId && old.protocol == value.protocol && old.baseUrl == value.baseUrl.trimEnd('/') &&
                old.modelId == value.modelId && old.contextTokens == value.contextTokens && old.outputTokens == value.outputTokens && newSecret == null
            val saved = value.copy(version = (old?.version ?: 0) + 1, baseUrl = value.baseUrl.trimEnd('/'),
                capabilities = if (sameService) old.capabilities else ModelCapabilities())
            try {
                if (newSecret != null) credentials.write(reference, newSecret)
                if (saved.isDefault) rows.filter { it.id != saved.id }.forEach { row ->
                    val other = Json.decodeFromString<ModelProfile>(row.profileJson)
                    if (other.isDefault) database.agent().saveProfile(row.copy(profileJson = Json.encodeToString(other.copy(isDefault = false))))
                }
                database.agent().saveProfile(ModelProfileEntity(saved.id, Json.encodeToString(saved), reference))
            } catch (error: Exception) {
                if (newSecret != null) credentials.delete(reference)
                throw error
            }
            if (newSecret != null) obsoleteReference = oldRow?.credentialReference
            saved
        } }
        obsoleteReference?.let(credentials::delete)
        result
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val reference = database.useWriterConnection { connection -> connection.immediateTransaction {
            ensureUnlocked(id, false)
            val row = database.agent().profiles().find { it.id == id }
            database.agent().deleteProfile(id)
            row?.credentialReference
        } }
        reference?.let(credentials::delete)
    }

    suspend fun recordCapabilities(profile: ModelProfile, capabilities: ModelCapabilities) = database.useWriterConnection { connection -> connection.immediateTransaction {
        val row = database.agent().profiles().find { it.id == profile.id } ?: return@immediateTransaction
        val current = Json.decodeFromString<ModelProfile>(row.profileJson)
        if (current.version == profile.version) database.agent().saveProfile(row.copy(profileJson = Json.encodeToString(current.copy(capabilities = capabilities))))
    } }

    private suspend fun ensureUnlocked(id: String, changingDefault: Boolean) {
        val runs = database.agent().unfinishedRuns()
        val queue = database.agent().pendingQueue()
        if (runs.any { changingDefault || it.profileId == id } || queue.any { changingDefault || it.profileId == id }) throw ModelException(ModelError.Busy)
    }
}
