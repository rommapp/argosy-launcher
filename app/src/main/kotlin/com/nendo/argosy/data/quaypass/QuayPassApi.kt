package com.nendo.argosy.data.quaypass

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface QuayPassApi {

    @GET("api/v1/clients/register/challenge")
    suspend fun getRegisterChallenge(
        @Header("Authorization") bearer: String
    ): Response<RegisterChallengeResponse>

    @POST("api/v1/clients/register")
    suspend fun registerClient(
        @Header("Authorization") bearer: String,
        @Body body: RegisterClientRequest
    ): Response<RegisterClientResponse>

    @POST("api/v1/quaypass/credentials/issue")
    suspend fun issueCredential(
        @Header("Authorization") bearer: String,
        @Body body: IssueCredentialRequest
    ): Response<CredentialResponse>

    @POST("api/v1/quaypass/credentials/refresh")
    suspend fun refreshCredential(
        @Header("Authorization") bearer: String,
        @Body body: IssueCredentialRequest
    ): Response<CredentialResponse>
}

@JsonClass(generateAdapter = true)
data class RegisterChallengeResponse(
    @Json(name = "challenge") val challenge: String,
    @Json(name = "expires_at") val expiresAtEpochSecs: Long
)

@JsonClass(generateAdapter = true)
data class RegisterClientRequest(
    @Json(name = "public_key") val publicKey: String,
    @Json(name = "public_key_alg") val publicKeyAlg: String,
    @Json(name = "apk_signing_cert_hash") val apkSigningCertHash: String,
    @Json(name = "fingerprint_hash") val fingerprintHash: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "challenge") val challenge: String,
    @Json(name = "challenge_signature") val challengeSignature: String
)

@JsonClass(generateAdapter = true)
data class RegisterClientResponse(
    @Json(name = "client_install_id") val clientInstallId: String
)

@JsonClass(generateAdapter = true)
data class IssueCredentialRequest(
    @Json(name = "client_install_id") val clientInstallId: String
)

@JsonClass(generateAdapter = true)
data class CredentialResponse(
    @Json(name = "credential") val credential: String,
    @Json(name = "expires_at") val expiresAtEpochSecs: Long
)
