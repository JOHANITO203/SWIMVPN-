package com.swimvpn.app.data.api

import com.swimvpn.app.data.model.CreateOrderRequest
import com.swimvpn.app.data.model.OrderResponse
import com.swimvpn.app.data.model.Plan
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET("store/plans")
    suspend fun getPlans(): List<Plan>

    @POST("orders")
    suspend fun createOrder(@Body request: CreateOrderRequest): OrderResponse
}
