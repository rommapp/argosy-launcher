package com.nendo.argosy.data.social

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface SocialApi {

    @POST("auth/device-key")
    suspend fun generateDeviceKey(@Body request: DeviceKeyRequest): Response<DeviceKeyResponse>

    @GET("api/me")
    suspend fun getMe(@Header("Authorization") authorization: String): Response<MeResponse>
}
