package net.niebes.retrofit.metrics.micrometer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.any
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.niebes.retrofit.metrics.HttpSeries
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@WireMockTest
internal class MicrometerRetrofitMetricsFactoryTest {
    private val responseBody = """{ "name": "The body with no name" }"""
    private val responseObject = NamedObject("The body with no name")

    private lateinit var client: SomeClient
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var baseUrl: String

    @BeforeEach
    fun before(wmRuntimeInfo: WireMockRuntimeInfo) {
        meterRegistry = SimpleMeterRegistry()
        baseUrl = wmRuntimeInfo.httpBaseUrl + "/"

        val okHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(1000, TimeUnit.MILLISECONDS)
                .readTimeout(1000, TimeUnit.MILLISECONDS)
                .writeTimeout(1000, TimeUnit.MILLISECONDS)
                .build()
        val retrofit =
            Retrofit
                .Builder()
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create(jacksonObjectMapper()))
                .addCallAdapterFactory(MicrometerRetrofitMetricsFactory(meterRegistry))
                .baseUrl(baseUrl)
                .build()
        client = retrofit.create(SomeClient::class.java)
    }

    @Test
    fun root() {
        stubFor(get(urlEqualTo("/")).willReturn(aResponse().withBody(responseBody)))

        val response = client.root().execute()

        assertResponse(response, 200, responseObject)
        assertThat(meter("GET", "/", baseUrl, "200").count()).isEqualTo(1)
    }

    @Test
    fun rootWithTimeout() {
        stubFor(get(urlEqualTo("/")).willReturn(aResponse().withFixedDelay(5000)))

        assertThatThrownBy { client.root().execute() }

        assertThat(exceptionMeter("GET", "/", baseUrl, "SocketTimeoutException").count()).isEqualTo(1)
    }

    @Test
    fun rootWith500() {
        stubFor(get(urlEqualTo("/")).willReturn(aResponse().withStatus(500).withBody(responseBody)))

        val response = client.root().execute()

        assertThat(response.code()).isEqualTo(500)
        assertThat(meter("GET", "/", baseUrl, "500").count()).isEqualTo(1)
    }

    @Test
    fun dotNotation() {
        stubFor(get(urlEqualTo("/")).willReturn(aResponse().withBody(responseBody)))

        val response = client.dotNotation().execute()

        assertResponse(response, 200, responseObject)
        assertThat(meter("GET", ".", baseUrl, "200").count()).isEqualTo(1)
    }

    @Test
    fun customHttpMethod() {
        stubFor(any(urlEqualTo("/custom/method")).willReturn(aResponse().withBody(responseBody)))

        val response = client.customHTTPMethod().execute()

        assertResponse(response, 200, responseObject)
        assertThat(meter("FOO", "/custom/method", baseUrl, "200").count()).isEqualTo(1)
    }

    @Test
    fun usesPlaceholder() {
        stubFor(get(urlEqualTo("/api/users/foo/foo")).willReturn(aResponse().withBody(responseBody)))

        val response = client.getWithPlaceHolderValue("foo", "bar").execute()

        assertResponse(response, 200, responseObject)
        assertThat(meter("GET", "api/users/{userId}/foo", baseUrl, "200").count()).isEqualTo(1)
    }

    @Test
    fun async() {
        stubFor(get(urlEqualTo("/api/users/userId/foo")).willReturn(aResponse().withBody(responseBody)))

        val latch = CountDownLatch(1)
        client.getWithPlaceHolderValue("userId", "headerValue").enqueue(
            object : Callback<NamedObject> {
                override fun onResponse(
                    call: Call<NamedObject>,
                    response: Response<NamedObject>,
                ) {
                    latch.countDown()
                }

                override fun onFailure(
                    call: Call<NamedObject>,
                    t: Throwable,
                ) {
                    fail<Any>("no exception expected", t)
                }
            }
        )
        latch.await(1, TimeUnit.SECONDS)
        assertThat(meter("GET", "api/users/{userId}/foo", baseUrl, "200").count()).isEqualTo(1)
    }

    private fun assertResponse(
        response: Response<NamedObject>,
        status: Int,
        body: Any?,
    ) {
        assertThat(response.code()).isEqualTo(status)
        assertThat(response.body()).isEqualTo(body)
    }

    private fun meter(
        method: String,
        path: String,
        baseUrl: String,
        status: String,
    ): Timer =
        meterRegistry
            .get("http.client.requests")
            .tag("base_url", baseUrl)
            .tag("uri", path)
            .tag("method", method)
            .tag("status", status)
            .tag("series", HttpSeries.fromHttpStatus(status.toInt())!!.name)
            .tag("exception", "None")
            .timer()

    private fun exceptionMeter(
        method: String,
        path: String,
        baseUrl: String,
        exception: String,
    ): Timer =
        meterRegistry
            .get("http.client.requests")
            .tag("base_url", baseUrl)
            .tag("uri", path)
            .tag("method", method)
            .tag("status", "Exception")
            .tag("series", "EXCEPTION")
            .tag("exception", exception)
            .timer()

    @Test
    fun methodWithoutCallWrapper() {
        assertThatThrownBy {
            client.methodWithoutCallWrapper()
        }.hasCauseInstanceOf(IllegalArgumentException::class.java)
    }

    interface SomeClient {
        @GET("/")
        fun root(): Call<NamedObject>

        @GET(".")
        fun dotNotation(): Call<NamedObject>

        @GET("/throws/exception")
        fun methodWithoutCallWrapper(): NamedObject

        @HTTP(method = "FOO", path = "/custom/method")
        fun customHTTPMethod(): Call<NamedObject>

        @GET("api/users/{userId}/foo")
        fun getWithPlaceHolderValue(
            @Path("userId") userId: String,
            @Header("some") someHeader: String,
        ): Call<NamedObject>
    }

    data class NamedObject(
        val name: String,
    )
}
