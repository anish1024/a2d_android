package com.a2d.app.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for the a2d-cloud API endpoints.
 *
 * The Bearer token is passed explicitly so the SyncEngine can refresh and retry on 401.
 */
interface A2DSyncApi {
    @GET("/healthz")
    fun healthCheck(): Call<HealthResponse>

    @FormUrlEncoded
    @POST("/v1/auth/login")
    fun login(
        @Field("email") email: String,
        @Field("password") password: String
    ): Call<AuthResponse>

    @GET("/v1/auth/refresh")
    fun refreshToken(
        @Header("Idempotency-Key") idempotencyKey: String?,
    ): Call<AuthResponse>

    @POST("/v1/sync/backup")
    fun syncBackup(
        @Header("Idempotency-Key") idempotencyKey: String?,
        @Body() request: SyncBackupRequest
    ): Call<SyncBackupResponse>

    @GET("/v1/sync/updates")
    fun syncUpdates(
        @Query("lastDownloadAt") lastDownloadAt: String,
        @Query("limit") limit: Int = 500
    ): Call<SyncUpdatesResponse>

    @POST("/v1/auth/logout")
    fun logout(): Call<Unit>
}

/**
 * Response model for login / refresh. The backend uses snake_case JSON keys.
 */
data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("user_id") val userId: String,
    @SerializedName("email") val email: String = ""
)