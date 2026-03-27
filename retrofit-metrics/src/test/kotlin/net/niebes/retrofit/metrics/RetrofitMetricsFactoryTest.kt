package net.niebes.retrofit.metrics

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.HTTP
import retrofit2.http.OPTIONS
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import java.time.Duration

internal class RetrofitMetricsFactoryTest {
    private val recordedCalls = mutableListOf<Pair<Map<String, String>, Duration>>()

    private val metricsRecorder =
        object : MetricsRecorder {
            override fun recordTiming(
                tags: Map<String, String>,
                duration: Duration,
            ) {
                recordedCalls.add(tags to duration)
            }
        }

    private lateinit var server: MockWebServer
    private lateinit var retrofit: Retrofit

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        retrofit =
            Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(JacksonConverterFactory.create(jacksonObjectMapper()))
                .addCallAdapterFactory(RetrofitMetricsFactory(metricsRecorder))
                .build()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `records metrics for GET request`() {
        server.enqueue(MockResponse(body = """{"value":"ok"}"""))
        val client = retrofit.create(TestClient::class.java)

        client.get().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "GET")
        assertThat(recordedCalls.first().first).containsEntry("uri", "api/test")
    }

    @Test
    fun `records metrics for POST request`() {
        server.enqueue(MockResponse(body = """{"value":"ok"}"""))
        val client = retrofit.create(TestClient::class.java)

        client.post().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "POST")
        assertThat(recordedCalls.first().first).containsEntry("uri", "api/test")
    }

    @Test
    fun `records metrics for PUT request`() {
        server.enqueue(MockResponse(body = """{"value":"ok"}"""))
        val client = retrofit.create(TestClient::class.java)

        client.put().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "PUT")
    }

    @Test
    fun `records metrics for DELETE request`() {
        server.enqueue(MockResponse(body = """{"value":"ok"}"""))
        val client = retrofit.create(TestClient::class.java)

        client.delete().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "DELETE")
    }

    @Test
    fun `records metrics for PATCH request`() {
        server.enqueue(MockResponse(body = """{"value":"ok"}"""))
        val client = retrofit.create(TestClient::class.java)

        client.patch().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "PATCH")
    }

    @Test
    fun `records metrics for OPTIONS request`() {
        server.enqueue(MockResponse(body = """{"value":"ok"}"""))
        val client = retrofit.create(TestClient::class.java)

        client.options().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "OPTIONS")
    }

    @Test
    fun `records metrics for HEAD request`() {
        server.enqueue(MockResponse())
        val client = retrofit.create(TestClient::class.java)

        client.head().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "HEAD")
    }

    @Test
    fun `records metrics for custom HTTP method`() {
        server.enqueue(MockResponse(body = """{"value":"ok"}"""))
        val client = retrofit.create(TestClient::class.java)

        client.custom().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "CUSTOM")
        assertThat(recordedCalls.first().first).containsEntry("uri", "api/custom")
    }

    @Test
    fun `records base_url tag`() {
        server.enqueue(MockResponse(body = """{"value":"ok"}"""))
        val client = retrofit.create(TestClient::class.java)

        client.get().execute()

        assertThat(recordedCalls.first().first).containsEntry("base_url", server.url("/").toString())
    }

    interface TestClient {
        @GET("api/test")
        fun get(): Call<TestResponse>

        @POST("api/test")
        fun post(): Call<TestResponse>

        @PUT("api/test")
        fun put(): Call<TestResponse>

        @DELETE("api/test")
        fun delete(): Call<TestResponse>

        @PATCH("api/test")
        fun patch(): Call<TestResponse>

        @OPTIONS("api/test")
        fun options(): Call<TestResponse>

        @HEAD("api/test")
        fun head(): Call<Void>

        @HTTP(method = "CUSTOM", path = "api/custom")
        fun custom(): Call<TestResponse>
    }

    data class TestResponse(
        val value: String = "",
    )
}
