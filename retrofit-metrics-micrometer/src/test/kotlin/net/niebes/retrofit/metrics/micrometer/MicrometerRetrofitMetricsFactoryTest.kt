package net.niebes.retrofit.metrics.micrometer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.Objects
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.niebes.retrofit.metrics.HttpSeries
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterEach
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

internal class MicrometerRetrofitMetricsFactoryTest {
    private val RESPONSE_BODY = "{ \"name\": \"The body with no name\" }"
    private val RESPONSE_OBJECT = NamedObject("The body with no name")

    private lateinit var server: MockWebServer
    private lateinit var client: SomeClient
    private lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun before() {
        server = MockWebServer()
        server.start()
        meterRegistry = SimpleMeterRegistry()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(1000, TimeUnit.MILLISECONDS)
            .readTimeout(1000, TimeUnit.MILLISECONDS)
            .writeTimeout(1000, TimeUnit.MILLISECONDS)
            .build()
        val baseUrl = server.url("/")
        val retrofit = Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create(jacksonObjectMapper()))
            .addCallAdapterFactory(MicrometerRetrofitMetricsFactory(meterRegistry))
            .baseUrl(baseUrl.toString())
            .build()
        client = retrofit.create(SomeClient::class.java)
    }

    private fun addResponse(mockResponse: MockResponse) {
        server.enqueue(mockResponse)
    }

    @AfterEach
    fun after() {
        server.shutdown()
    }

    @Test
    fun root() {
        addResponse(MockResponse().apply {
            setBody(RESPONSE_BODY)
        })
        val response = client.root().execute()
        assertResponse(response, 200, RESPONSE_OBJECT)

        assertThat(meter("GET", "/", baseUrl(), "200").count()).isEqualTo(1)
    }

    @Test
    fun rootWithTimeout() {
        assertThatThrownBy { client.root().execute() }

        assertThat(exceptionMeter("GET", "/", baseUrl(), "SocketTimeoutException").count()).isEqualTo(1)
    }

    @Test
    fun rootWith500() {
        addResponse(MockResponse().apply {
            setBody(RESPONSE_BODY)
            setResponseCode(500)
        })
        val response = client.root().execute()
        assertThat(response.code()).isEqualTo(500)

        assertThat(meter("GET", "/", baseUrl(), "500").count()).isEqualTo(1)
    }

    @Test
    fun dotNotation() {
        addResponse(MockResponse().apply {
            setBody(RESPONSE_BODY)
        })
        val response = client.dotNotation().execute()
        assertResponse(response, 200, RESPONSE_OBJECT)

        assertThat(meter("GET", ".", baseUrl(), "200").count()).isEqualTo(1)
    }

    @Test
    fun customHttpMethod() {
        addResponse(MockResponse().apply {
            setBody(RESPONSE_BODY)
        })
        val response = client.customHTTPMethod().execute()
        assertResponse(response, 200, RESPONSE_OBJECT)

        assertThat(meter("FOO", "/custom/method", baseUrl(), "200").count()).isEqualTo(1)
    }

    @Test
    fun usesPlaceholder() {
        addResponse(MockResponse().apply {
            setBody(RESPONSE_BODY)
        })

        val response = client.getWithPlaceHolderValue("foo", "bar").execute()
        assertResponse(response, 200, RESPONSE_OBJECT)
        assertThat(meter("GET", "api/users/{userId}/foo", baseUrl(), "200").count()).isEqualTo(1)
    }

    @Test
    fun async() {
        addResponse(MockResponse().setBody(RESPONSE_BODY))
        val latch = CountDownLatch(1)
        client.getWithPlaceHolderValue("userId", "headerValue").enqueue(object : Callback<NamedObject> {
            @Override
            override fun onResponse(call: Call<NamedObject>, response: Response<NamedObject>) {
                latch.countDown()
            }

            @Override
            override fun onFailure(call: Call<NamedObject>, t: Throwable) {
                fail<Any>("no exception expected", t)
            }
        })
        latch.await(1, TimeUnit.SECONDS) // wait for async to complete
        assertThat(meter("GET", "api/users/{userId}/foo", baseUrl(), "200").count()).isEqualTo(1)
    }

    private fun baseUrl() = server.url("/").toString()

    private fun assertResponse(response: Response<NamedObject>, status: Int, body: Any?) {
        assertThat(response.code()).isEqualTo(status)
        assertThat(response.body()).isEqualTo(body)
    }

    private fun meter(method: String, path: String, baseUrl: String, status: String): Timer =
        meterRegistry.get("http.client.requests")
            .tag("method", method)
            .tag("uri", path)
            .tag("base_url", baseUrl)
            .tag("status", status)
            .tag("series", HttpSeries.fromHttpStatus(status.toInt())!!.name)
            .tag("exception", "None")
            .timer()

    private fun exceptionMeter(method: String, path: String, baseUrl: String, exception: String): Timer =
        meterRegistry.get("http.client.requests")
            .tag("method", method)
            .tag("uri", path)
            .tag("base_url", baseUrl)
            .tag("exception", exception)
            .timer()

    @Test
    fun methodWithoutCallWrapper() {
        assertThatThrownBy {
            client.methodWithoutCallWrapper()
        }.hasCauseInstanceOf(IllegalArgumentException::class.java)
    }

    /**
     * A test client interface
     */
    interface SomeClient {
        @GET("/")
        fun root(): Call<NamedObject>

        @GET(".") // uses base url without path
        fun dotNotation(): Call<NamedObject>

        @GET("/throws/exception")
        fun methodWithoutCallWrapper(): NamedObject

        @HTTP(method = "FOO", path = "/custom/method")
        fun customHTTPMethod(): Call<NamedObject>

        @GET("api/users/{userId}/foo")
        fun getWithPlaceHolderValue(@Path("userId") userId: String, @Header("some") someHeader: String): Call<NamedObject>
    }

    /**
     * A test data class
     */
    class NamedObject(val name: String) {

        override fun hashCode(): Int = Objects.hashCode(this.name)

        override fun equals(other: Any?): Boolean = (other as NamedObject).name == name
    }
}
