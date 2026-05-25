package net.niebes.retrofit.resilience4j

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST

internal interface TestClient {
    @GET("api/test")
    fun get(): Call<String>

    @POST("api/test")
    fun post(): Call<String>
}
