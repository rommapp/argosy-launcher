package com.nendo.argosy.data.quaypass

import com.squareup.moshi.Json
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface QuayPassApi {

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

data class RegisterClientRequest(
    @Json(name = "public_key") val publicKey: String,           // base64
    @Json(name = "public_key_alg") val publicKeyAlg: String,    // "ed25519" | "ec-p256"
    @Json(name = "apk_signing_cert_hash") val apkSigningCertHash: String,  // hex sha256
    @Json(name = "fingerprint_hash") val fingerprintHash: String,           // hex sha256
    @Json(name = "device_token") val deviceToken: String                    // android_id
)

data class RegisterClientRequestSigned(
    @Json(name = "previous_public_key") val previousPublicKey: String,      // base64
    @Json(name = "rotation_signature") val rotationSignature: String        // base64; sig over new pubkey by old key
)

data class RegisterClientResponse(
    @Json(name = "client_install_id") val clientInstallId: String
)

data class IssueCredentialRequest(
    @Json(name = "client_install_id") val clientInstallId: String
)

data class CredentialResponse(
    @Json(name = "credential") val credential: String,         // base64 of signed bundle
    @Json(name = "expires_at") val expiresAtEpochSecs: Long
)
