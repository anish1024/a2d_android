package com.a2d.app.data.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import java.util.UUID

/**
 * Encrypted token store for cloud-sync credentials.
 *
 * Uses EncryptedSharedPreferences backed by the Android Keystore (AES-256-GCM value encryption,
 * AES-256-SIV key encryption). Stores access/refresh tokens, expiry, user id, email and a stable
 * per-install device id.
 */
class TokenStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val gson = Gson()

    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        expiresIn: Int,
        userId: String,
        email: String
    ) {
        val now = System.currentTimeMillis()
        val tokens = Tokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = now + expiresIn * 1000L,
            userId = userId,
            email = email,
            deviceId = getOrCreateDeviceId()
        )
        prefs.edit()
            .putString(KEY_TOKENS, gson.toJson(tokens))
            .apply()
    }

    /** Returns the access token if it is present and not expired; "" otherwise. */
    fun getAccessToken(): String {
        val tokens = getTokens() ?: return ""
        if (System.currentTimeMillis() >= tokens.expiresAt) return ""
        return tokens.accessToken
    }

    fun getRefreshToken(): String = getTokens()?.refreshToken ?: ""

    fun refreshTokens(accessToken: String, refreshToken: String, expiresIn: Int) {
        val existing = getTokens() ?: return
        val updated = existing.copy(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000L
        )
        prefs.edit().putString(KEY_TOKENS, gson.toJson(updated)).apply()
    }

    fun clearTokens() {
        prefs.edit().remove(KEY_TOKENS).apply()
    }

    fun isAuthenticated(): Boolean = getAccessToken().isNotBlank()

    fun getUserEmail(): String = getTokens()?.email ?: ""

    fun getDeviceId(): String = getTokens()?.deviceId ?: getOrCreateDeviceId()

    private fun getTokens(): Tokens? {
        val json = prefs.getString(KEY_TOKENS, null) ?: return null
        return try {
            gson.fromJson(json, Tokens::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    companion object {
        private const val PREFS_NAME = "a2d_sync_tokens"
        private const val KEY_TOKENS = "tokens"
        private const val KEY_DEVICE_ID = "device_id"

        private data class Tokens(
            val accessToken: String,
            val refreshToken: String,
            val expiresAt: Long,
            val userId: String,
            val email: String,
            val deviceId: String
        )
    }
}