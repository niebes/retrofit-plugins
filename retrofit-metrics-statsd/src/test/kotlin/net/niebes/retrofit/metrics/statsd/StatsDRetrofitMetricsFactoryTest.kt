package net.niebes.retrofit.metrics.statsd

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.timgroup.statsd.StatsDClient
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Path
import java.net.HttpURLConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RetrofitMetricsFactoryTest {
    private val RESPONSE_BODY = "{ \"name\": \"The body with no name\" }"
    private val RESPONSE_OBJECT = NamedObject("The body with no name")
    private val statsD = mock(StatsDClient::class.java)
    private lateinit var server: MockWebServer
    private lateinit var client: SomeClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(1000, TimeUnit.MILLISECONDS)
            .readTimeout(1000, TimeUnit.MILLISECONDS)
            .writeTimeout(1000, TimeUnit.MILLISECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create(ObjectMapper().registerKotlinModule()))
            .addCallAdapterFactory(StatsDRetrofitMetricsFactory(statsD))
            .baseUrl(server.url("/").toString())
            .build()
        client = retrofit.create(SomeClient::class.java)
        reset(statsD)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun addResponse(mockResponse: MockResponse) {
        server.enqueue(mockResponse)
    }

    private fun baseUrl(): String = server.url("/").toString()

    private fun assertResponse(response: Response<NamedObject>, status: Int, body: Any) {
        assertThat(response.code(), `is`(status))
        assertThat(response.body(), `is`(body))
    }

    @Test
    fun root() {
        addResponse(MockResponse().setBody(RESPONSE_BODY))

        val response = client.root().execute()

        assertResponse(response, HttpURLConnection.HTTP_OK, RESPONSE_OBJECT)
        verifyRequestMetrics(
            baseUrl = baseUrl(),
            path = "/",
            method = "GET",
            status = "200",
            series = "SUCCESSFUL",
            aysnc = "false",
            exception = "None"
        )
    }

    @Test
    fun dotNotation() {
        addResponse(MockResponse().setBody(RESPONSE_BODY))

        val response = client.dotNotation().execute()

        assertResponse(response, HttpURLConnection.HTTP_OK, RESPONSE_OBJECT)
        verifyRequestMetrics(
            baseUrl = baseUrl(),
            path = ".",
            method = "GET",
            status = "200",
            series = "SUCCESSFUL",
            aysnc = "false",
            exception = "None"
        )
    }

    @Test
    fun rootWith500() {
        addResponse(MockResponse().setBody(RESPONSE_BODY).setResponseCode(500))

        val response = client.root().execute()

        assertThat(response.code(), `is`(500))
        verifyRequestMetrics(
            baseUrl = baseUrl(),
            path = "/",
            method = "GET",
            status = "500",
            series = "SERVER_ERROR",
            aysnc = "false",
            exception = "None"
        )
    }

    @Test
    fun rootWithTimeout() {
        try {
            client.root().execute()
            fail("exception expected")
        } catch (ignored: Exception) {
        }

        verifyExceptionMetrics(
            baseUrl = baseUrl(),
            path = "/",
            method = "GET",
            aysnc = "false",
            exception = "SocketTimeoutException"
        )
    }

    @Test
    fun customHttpMethod() {
        addResponse(MockResponse().setBody(RESPONSE_BODY))

        val response = client.customHTTPMethod().execute()

        assertResponse(response, HttpURLConnection.HTTP_OK, RESPONSE_OBJECT)
        verifyRequestMetrics(
            baseUrl = baseUrl(),
            path = "/custom/method",
            method = "FOO",
            status = "200",
            series = "SUCCESSFUL",
            aysnc = "false",
            exception = "None"
        )
    }

    @Test
    fun useUriPlaceHolder() {
        addResponse(MockResponse().setBody(RESPONSE_BODY))

        val response = client.getWithPlaceHolderValue("userId", "headerValue").execute()

        assertResponse(response, HttpURLConnection.HTTP_OK, RESPONSE_OBJECT)
        verifyRequestMetrics(
            baseUrl = baseUrl(),
            path = "api/users/{userId}/foo",
            method = "GET",
            status = "200",
            series = "SUCCESSFUL",
            aysnc = "false",
            exception = "None"
        )
    }

    @Test
    fun async() {
        addResponse(MockResponse().setBody(RESPONSE_BODY))

        val latch = CountDownLatch(1)
        client.getWithPlaceHolderValue("userId", "headerValue").enqueue(object : Callback<NamedObject?> {
            override fun onResponse(call: Call<NamedObject?>, response: Response<NamedObject?>) {
                latch.countDown()
            }

            override fun onFailure(call: Call<NamedObject?>, t: Throwable) {
                fail("no exception expected")
            }
        })
        latch.await(1, TimeUnit.SECONDS) // wait for async to complete

        verifyRequestMetrics(
            baseUrl = baseUrl(),
            path = "api/users/{userId}/foo",
            method = "GET",
            status = "200",
            series = "SUCCESSFUL",
            aysnc = "true",
            exception = "None"
        )
    }

    private fun verifyRequestMetrics(
        baseUrl: String,
        path: String,
        method: String,
        status: String,
        series: String,
        aysnc: String,
        exception: String
    ) {
        verify(statsD).histogram(eq("http.client.requests"), anyLong(),
            eq("base_url:$baseUrl"),
            eq("uri:$path"),
            eq("method:$method"),
            eq("series:$series"),
            eq("status:$status"),
            eq("async:$aysnc"),
            eq("exception:$exception")
        )
    }

    private fun verifyExceptionMetrics(
        baseUrl: String,
        path: String,
        method: String,
        aysnc: String,
        exception: String
    ) {
        verify(statsD).histogram(eq("http.client.requests"), anyLong(),
            eq("base_url:$baseUrl"),
            eq("method:$method"),
            eq("uri:$path"),
            eq("async:$aysnc"),
            eq("exception:$exception")
        )
    }

    /**
     * A test client interface
     */
    internal interface SomeClient {
        @GET("/")
        fun root(): Call<NamedObject>

        @GET(".")
        fun dotNotation(): Call<NamedObject> //uses base url without path

        @HTTP(method = "FOO", path = "/custom/method")
        fun customHTTPMethod(): Call<NamedObject>

        @GET("api/users/{userId}/foo")
        fun getWithPlaceHolderValue(
            @Path("userId") userId: String,
            @Header("some") someHeader: String
        ): Call<NamedObject>
    }

    data class NamedObject(val name: String)
}
