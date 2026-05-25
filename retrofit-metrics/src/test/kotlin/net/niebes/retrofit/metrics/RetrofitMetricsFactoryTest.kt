package net.niebes.retrofit.metrics

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.any
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.head
import com.github.tomakehurst.wiremock.client.WireMock.options
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.assertj.core.api.Assertions.assertThat
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

@WireMockTest
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

    private lateinit var retrofit: Retrofit
    private lateinit var baseUrl: String

    @BeforeEach
    fun setUp(wmRuntimeInfo: WireMockRuntimeInfo) {
        baseUrl = wmRuntimeInfo.httpBaseUrl + "/"
        retrofit =
            Retrofit
                .Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(JacksonConverterFactory.create(jacksonObjectMapper()))
                .addCallAdapterFactory(RetrofitMetricsFactory(metricsRecorder))
                .build()
    }

    @Test
    fun `records metrics for GET request`() {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withBody("""{"value":"ok"}""")))
        val client = retrofit.create(TestClient::class.java)

        client.get().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "GET")
        assertThat(recordedCalls.first().first).containsEntry("uri", "api/test")
    }

    @Test
    fun `records metrics for POST request`() {
        stubFor(post(urlEqualTo("/api/test")).willReturn(aResponse().withBody("""{"value":"ok"}""")))
        val client = retrofit.create(TestClient::class.java)

        client.post().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "POST")
        assertThat(recordedCalls.first().first).containsEntry("uri", "api/test")
    }

    @Test
    fun `records metrics for PUT request`() {
        stubFor(put(urlEqualTo("/api/test")).willReturn(aResponse().withBody("""{"value":"ok"}""")))
        val client = retrofit.create(TestClient::class.java)

        client.put().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "PUT")
    }

    @Test
    fun `records metrics for DELETE request`() {
        stubFor(delete(urlEqualTo("/api/test")).willReturn(aResponse().withBody("""{"value":"ok"}""")))
        val client = retrofit.create(TestClient::class.java)

        client.delete().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "DELETE")
    }

    @Test
    fun `records metrics for PATCH request`() {
        stubFor(patch(urlEqualTo("/api/test")).willReturn(aResponse().withBody("""{"value":"ok"}""")))
        val client = retrofit.create(TestClient::class.java)

        client.patch().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "PATCH")
    }

    @Test
    fun `records metrics for OPTIONS request`() {
        stubFor(options(urlEqualTo("/api/test")).willReturn(aResponse().withBody("""{"value":"ok"}""")))
        val client = retrofit.create(TestClient::class.java)

        client.options().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "OPTIONS")
    }

    @Test
    fun `records metrics for HEAD request`() {
        stubFor(head(urlEqualTo("/api/test")).willReturn(aResponse()))
        val client = retrofit.create(TestClient::class.java)

        client.head().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "HEAD")
    }

    @Test
    fun `records metrics for custom HTTP method`() {
        stubFor(any(urlEqualTo("/api/custom")).willReturn(aResponse().withBody("""{"value":"ok"}""")))
        val client = retrofit.create(TestClient::class.java)

        client.custom().execute()

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("method", "CUSTOM")
        assertThat(recordedCalls.first().first).containsEntry("uri", "api/custom")
    }

    @Test
    fun `records base_url tag`() {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withBody("""{"value":"ok"}""")))
        val client = retrofit.create(TestClient::class.java)

        client.get().execute()

        assertThat(recordedCalls.first().first).containsEntry("base_url", baseUrl)
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
