package com.swimvpn.app.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("api/v1/access/trial")
    suspend fun startTrial(@Body request: StartTrialRequest): AccessProfileResponse

    @GET("api/v1/access/{userNumber}")
    suspend fun getAccessProfile(@Path("userNumber") userNumber: String): AccessProfileResponse

    @POST("api/v1/subscription/import")
    suspend fun importSubscription(@Body request: ImportSubscriptionRequest): AccessProfileResponse

    @POST("api/v1/subscription/activate-code")
    suspend fun activateCode(@Body request: ActivateCodeRequest): AccessProfileResponse

    @GET("api/v1/servers")
    suspend fun getServers(@Header("x-user-number") userNumber: String): List<ServerNode>
}
