package com.nendo.argosy.data.social

import retrofit2.Response
import retrofit2.http.POST

interface SocialApi {

    @POST("auth/device-key")
    suspend fun generateDeviceKey(): Response<DeviceKeyResponse>
}
