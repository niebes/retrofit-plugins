package net.niebes.resilience4j

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@WireMockTest
internal class RetryCallFactoryTest {
    companion object {
        const val MAX_ATTEMPTS = 3
    }

    private val responseBody = """{ "name": "The body with no name" }"""
    private val responseObject = NamedObject("The body with no name")
    private lateinit var client: SomeClient
    private val retryConfig: RetryConfig =
        RetryConfig
            .custom<Response<out Any?>>()
            .waitDuration(Duration.ofMillis(10))
            .retryOnResult { response -> response.code() in 500..599 }
            .maxAttempts(MAX_ATTEMPTS)
            .build()

    @BeforeEach
    fun before(wmRuntimeInfo: WireMockRuntimeInfo) {
        client =
            createClient(
                wmRuntimeInfo,
                RetryCallFactory(
                    Retry.of("test", retryConfig).apply {
                        with(eventPublisher) {
                            onSuccess { success -> println("success $success") }
                            onError { error -> println("error $error") }
                            onRetry { retry -> println("retry $retry") }
                            onIgnoredError { ignoredError -> println("error $ignoredError") }
                        }
                    }
                )
            )
    }

    private fun createClient(
        wmRuntimeInfo: WireMockRuntimeInfo,
        retryCallFactory: RetryCallFactory,
    ): SomeClient =
        Retrofit
            .Builder()
            .client(
                OkHttpClient
                    .Builder()
                    .connectTimeout(Duration.ofMillis(100))
                    .readTimeout(Duration.ofMillis(100))
                    .writeTimeout(Duration.ofMillis(100))
                    .build()
            ).addConverterFactory(JacksonConverterFactory.create(jacksonObjectMapper()))
            .addCallAdapterFactory(retryCallFactory)
            .baseUrl(wmRuntimeInfo.httpBaseUrl + "/")
            .build()
            .create(SomeClient::class.java)

    @Test
    fun `should not retry with successful response`() {
        stubFor(get(urlEqualTo("/")).willReturn(aResponse().withBody(responseBody)))

        val response = client.root().execute()

        assertThat(response.code()).isEqualTo(200)
        assertThat(response.body()).isEqualTo(responseObject)
        verify(1, getRequestedFor(urlEqualTo("/")))
    }

    @Test
    fun `should use the first successful result within retry count`() {
        stubFor(
            get(urlEqualTo("/"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500).withBody(responseBody))
                .willSetStateTo("attempt2")
        )
        stubFor(
            get(urlEqualTo("/"))
                .inScenario("retry")
                .whenScenarioStateIs("attempt2")
                .willReturn(aResponse().withStatus(500).withBody(responseBody))
                .willSetStateTo("attempt3")
        )
        stubFor(
            get(urlEqualTo("/"))
                .inScenario("retry")
                .whenScenarioStateIs("attempt3")
                .willReturn(aResponse().withBody(responseBody))
        )

        val response = client.root().execute()

        assertThat(response.code()).isEqualTo(200)
        assertThat(response.body()).isEqualTo(responseObject)
        verify(MAX_ATTEMPTS, getRequestedFor(urlEqualTo("/")))
    }

    @Test
    fun rootWithTimeout() {
        stubFor(get(urlEqualTo("/")).willReturn(aResponse().withFixedDelay(5000)))

        Assertions.assertThrows(SocketTimeoutException::class.java) {
            client.root().execute()
        }

        verify(MAX_ATTEMPTS, getRequestedFor(urlEqualTo("/")))
    }

    @Test
    fun `should not retry POST by default`() {
        stubFor(
            post(urlEqualTo("/new/nonidempotent/transaction"))
                .willReturn(aResponse().withStatus(500).withBody(responseBody))
        )

        client.createTransaction().execute()

        verify(1, postRequestedFor(urlEqualTo("/new/nonidempotent/transaction")))
    }

    @Test
    fun `should retry POST when configured`(wmRuntimeInfo: WireMockRuntimeInfo) {
        client =
            createClient(
                wmRuntimeInfo,
                RetryCallFactory(
                    Retry.of("test", retryConfig)
                ) {
                    method == "GET" ||
                        setOf(
                            "new/nonidempotent/transaction".split("/")
                        ).contains(url.pathSegments)
                }
            )
        stubFor(
            post(urlEqualTo("/new/nonidempotent/transaction"))
                .willReturn(aResponse().withStatus(500).withBody(responseBody))
        )

        client.createTransaction().execute()

        verify(MAX_ATTEMPTS, postRequestedFor(urlEqualTo("/new/nonidempotent/transaction")))
    }

    @Test
    fun `should retry on async requests`() {
        stubFor(
            get(urlEqualTo("/api/users/userId/foo"))
                .inScenario("async-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500).withBody(responseBody))
                .willSetStateTo("attempt2")
        )
        stubFor(
            get(urlEqualTo("/api/users/userId/foo"))
                .inScenario("async-retry")
                .whenScenarioStateIs("attempt2")
                .willReturn(aResponse().withStatus(500).withBody(responseBody))
                .willSetStateTo("attempt3")
        )
        stubFor(
            get(urlEqualTo("/api/users/userId/foo"))
                .inScenario("async-retry")
                .whenScenarioStateIs("attempt3")
                .willReturn(aResponse().withBody(responseBody))
        )
        val latch = CountDownLatch(1)
        val successes = AtomicInteger(0)

        client.getWithPlaceHolderValue("userId", "headerValue").enqueue(
            object : Callback<NamedObject> {
                override fun onResponse(
                    call: Call<NamedObject>,
                    response: Response<NamedObject>,
                ) {
                    successes.incrementAndGet()
                    latch.countDown()
                }

                override fun onFailure(
                    call: Call<NamedObject>,
                    t: Throwable,
                ) {
                    fail("no exception expected", t)
                }
            }
        )

        latch.await(1, TimeUnit.SECONDS)
        assertThat(successes.get()).isEqualTo(1)
        verify(MAX_ATTEMPTS, getRequestedFor(urlEqualTo("/api/users/userId/foo")))
    }

    @Test
    fun `should report success when no exception thrown`() {
        stubFor(
            get(urlEqualTo("/api/users/userId/foo"))
                .willReturn(aResponse().withStatus(500).withBody(responseBody))
        )
        val latch = CountDownLatch(1)
        val successes = AtomicInteger(0)

        client.getWithPlaceHolderValue("userId", "headerValue").enqueue(
            object : Callback<NamedObject> {
                override fun onResponse(
                    call: Call<NamedObject>,
                    response: Response<NamedObject>,
                ) {
                    successes.incrementAndGet()
                    latch.countDown()
                }

                override fun onFailure(
                    call: Call<NamedObject>,
                    t: Throwable,
                ) {
                    fail("no exception expected", t)
                }
            }
        )

        latch.await(1, TimeUnit.SECONDS)
        assertThat(successes.get()).isEqualTo(1)
        verify(MAX_ATTEMPTS, getRequestedFor(urlEqualTo("/api/users/userId/foo")))
    }

    @Test
    fun `should report error when all async calls threw exceptions`() {
        stubFor(
            get(urlEqualTo("/api/users/userId/foo"))
                .willReturn(aResponse().withFixedDelay(5000))
        )
        val latch = CountDownLatch(1)
        val failures = AtomicInteger(0)

        client.getWithPlaceHolderValue("userId", "headerValue").enqueue(
            object : Callback<NamedObject> {
                override fun onResponse(
                    call: Call<NamedObject>,
                    response: Response<NamedObject>,
                ) {
                    fail("no success expected")
                }

                override fun onFailure(
                    call: Call<NamedObject>,
                    throwable: Throwable,
                ) {
                    failures.incrementAndGet()
                    latch.countDown()
                }
            }
        )

        latch.await(1, TimeUnit.SECONDS)
        assertThat(failures.get()).isEqualTo(1)
        verify(MAX_ATTEMPTS, getRequestedFor(urlEqualTo("/api/users/userId/foo")))
    }

    @Test
    fun `should report success when retry condition not met but no exception thrown`() {
        stubFor(
            get(urlEqualTo("/api/users/userId/foo"))
                .willReturn(aResponse().withStatus(500).withBody(responseBody))
        )
        val latch = CountDownLatch(MAX_ATTEMPTS)

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
                    fail("no exception expected", t)
                }
            }
        )

        latch.await(1, TimeUnit.SECONDS)
        verify(MAX_ATTEMPTS, getRequestedFor(urlEqualTo("/api/users/userId/foo")))
    }

    interface SomeClient {
        @GET("/")
        fun root(): Call<NamedObject>

        @POST("/new/nonidempotent/transaction")
        fun createTransaction(): Call<NamedObject>

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
