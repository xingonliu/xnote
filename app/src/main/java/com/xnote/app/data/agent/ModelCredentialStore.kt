package com.xnote.app.data.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.xnote.app.domain.agent.ModelError
import com.xnote.app.domain.agent.ModelException
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// -- Type Definitions

interface ModelCredentialStore {
    fun read(reference: String): String
    fun write(reference: String, secret: String)
    fun delete(reference: String)
}

class AndroidModelCredentialStore(context: Context) : ModelCredentialStore {
    // -- Constants

    private val alias = "xnote-model-credentials-v1"

    // -- State and Variables

    private val directory = File(context.noBackupFilesDir, "model-credentials").apply { mkdirs() }

    // -- Functions

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun file(reference: String): File {
        require(reference.matches(Regex("[a-zA-Z0-9-]+")))
        return File(directory, reference)
    }

    @Synchronized
    override fun write(reference: String, secret: String) {
        if (secret.isBlank() || secret.any { it == '\r' || it == '\n' }) throw ModelException(ModelError.MissingCredential)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
            val atomic = AtomicFile(file(reference))
            val stream = atomic.startWrite()
            try {
                stream.write(cipher.iv.size); stream.write(cipher.iv); stream.write(encrypted)
                atomic.finishWrite(stream)
            } catch (error: Exception) { atomic.failWrite(stream); throw error }
        } catch (_: Exception) { throw ModelException(ModelError.MissingCredential) }
    }

    @Synchronized
    override fun read(reference: String): String = try {
        val bytes = AtomicFile(file(reference)).readFully()
        val ivSize = bytes[0].toInt() and 255
        if (ivSize != 12 || bytes.size <= 1 + ivSize) throw ModelException(ModelError.MissingCredential)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 1 + ivSize)))
        }
        String(cipher.doFinal(bytes.copyOfRange(1 + ivSize, bytes.size)), Charsets.UTF_8)
    } catch (_: Exception) { throw ModelException(ModelError.MissingCredential) }

    @Synchronized
    override fun delete(reference: String) { AtomicFile(file(reference)).delete() }
}
