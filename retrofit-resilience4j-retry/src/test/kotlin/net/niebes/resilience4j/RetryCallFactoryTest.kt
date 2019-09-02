package net.niebes.resilience4j

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.Objects
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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

internal class RetryCallFactoryTest {
    companion object {
        const val maxAttempts = 3
    }

    private val RESPONSE_BODY = "{ \"name\": \"The body with no name\" }"
    private val RESPONSE_OBJECT = NamedObject("The body with no name")
    private lateinit var server: MockWebServer
    private lateinit var client: SomeClient
    private val retryConfig: RetryConfig = RetryConfig.custom<Response<out Any?>>()
            .waitDuration(Duration.ofMillis(10))
            .retryOnResult { response -> response.code() in 500..599 }
            .maxAttempts(maxAttempts)
            .build()

    @BeforeEach
    fun before() {
        server = MockWebServer()
        server.start()
        client = createClient(RetryCallFactory(Retry.of("test", retryConfig).apply {
            with(eventPublisher) {
                onSuccess { success -> println("success $success") }
                onError { error -> println("error $error") }
                onRetry { retry -> println("retry $retry") }
                onIgnoredError { ignoredError -> println("error $ignoredError") }
            }
        }))
    }

    private fun createClient(retryCallFactory: RetryCallFactory): SomeClient =
            Retrofit.Builder()
                    .client(
                            OkHttpClient.Builder()
                                    .connectTimeout(Duration.ofMillis(100))
                                    .readTimeout(Duration.ofMillis(100))
                                    .writeTimeout(Duration.ofMillis(100))
                                    .build()
                    )
                    .addConverterFactory(JacksonConverterFactory.create(jacksonObjectMapper()))
                    .addCallAdapterFactory(retryCallFactory)
                    .baseUrl(baseUrl())
                    .build()
                    .create(SomeClient::class.java)

    private fun addResponse(mockResponse: MockResponse) =
            server.enqueue(mockResponse)

    @AfterEach
    fun after() {
        server.shutdown()
    }

    @Test
    fun `should not retry with sucessfull response`() {
        addResponse(MockResponse().apply {
            setBody(RESPONSE_BODY)
        })
        val response = client.root().execute()
        assertResponse(response, 200, RESPONSE_OBJECT)
        val recordedRequests = server.getRecordedRequests()

        assertThat(recordedRequests.map { it.path }).containsExactly("/")
    }

    @Test
    fun `should use the first successful result within retry count`() {
        repeat(maxAttempts - 1) {
            addResponse(MockResponse().apply {
                setBody(RESPONSE_BODY)
                setResponseCode(500)
            })
        }
        addResponse(MockResponse().apply {
            setBody(RESPONSE_BODY)
        })
        val response = client.root().execute()
        assertResponse(response, 200, RESPONSE_OBJECT)
        val recordedRequests = server.getRecordedRequests()

        assertThat(recordedRequests.size).isEqualTo(maxAttempts)
    }

    @Test
    fun rootWithTimeout() {
        Assertions.assertThrows(SocketTimeoutException::class.java) {
            client.root().execute()
        }

        assertThat(server.getRecordedRequests().map { it.path }).containsExactly("/", "/", "/")
    }

    @Test
    fun `should not retry POST by default`() {
        addResponse(MockResponse().apply {
            setBody(RESPONSE_BODY)
            setResponseCode(500)
        })
        client.createTransaction().execute()

        assertThat(server.getRecordedRequests().map { it.path }).containsExactly("/new/nonidempotent/transaction")
    }

    @Test
    fun `should retry POST when configured`() {
        client = createClient(
                RetryCallFactory(
                        Retry.of(
                                "test",
                                retryConfig
                        )
                ) {
                    method == "GET" || setOf(
                            "new/nonidempotent/transaction".split("/")
                    ).contains(url.pathSegments)
                })
        repeat(maxAttempts) {
            addResponse(MockResponse().apply {
                setBody(RESPONSE_BODY)
                setResponseCode(500)
            })
        }

        client.createTransaction().execute()

        assertThat(server.getRecordedRequests().map { it.path }).containsExactly(
                "/new/nonidempotent/transaction",
                "/new/nonidempotent/transaction",
                "/new/nonidempotent/transaction"
        )
    }

    @Test
    fun `should retry on async requests`() {
        addResponse(MockResponse().apply {
            setBody(RESPONSE_BODY)
            setResponseCode(500)
        })
        addResponse(MockResponse().apply {
            setBody(RESPONSE_BODY)
            setResponseCode(500)
        })
        addResponse(MockResponse().setBody(RESPONSE_BODY))
        val latch = CountDownLatch(1)
        val successes = AtomicInteger(0)
        client.getWithPlaceHolderValue("userId", "headerValue").enqueue(object : Callback<NamedObject> {
            @Override
            override fun onResponse(call: Call<NamedObject>, response: Response<NamedObject>) {
                successes.incrementAndGet()
                latch.countDown()
            }

            @Override
            override fun onFailure(call: Call<NamedObject>, t: Throwable) {
                fail("no exception expected", t)
            }
        })
        latch.await(1, TimeUnit.SECONDS) // wait for async to complete
        assertThat(successes.get()).isEqualTo(1)
        assertThat(server.getRecordedRequests().map { it.path }).containsExactly(
                "/api/users/userId/foo",
                "/api/users/userId/foo",
                "/api/users/userId/foo"
        )
    }

    @Test
    fun `should report success when no exception thrown`() {
        repeat(maxAttempts) {
            addResponse(MockResponse().apply {
                setBody(RESPONSE_BODY)
                setResponseCode(500)
            })
        }
        val latch = CountDownLatch(1)
        val successes = AtomicInteger(0)
        client.getWithPlaceHolderValue("userId", "headerValue").enqueue(object : Callback<NamedObject> {
            @Override
            override fun onResponse(call: Call<NamedObject>, response: Response<NamedObject>) {
                successes.incrementAndGet()
                latch.countDown()
            }

            @Override
            override fun onFailure(call: Call<NamedObject>, t: Throwable) {
                fail("no exception expected", t)
            }
        })
        latch.await(1, TimeUnit.SECONDS) // wait for async to complete
        assertThat(successes.get()).isEqualTo(1)
        assertThat(server.getRecordedRequests().map { it.path }).containsExactly(
                "/api/users/userId/foo",
                "/api/users/userId/foo",
                "/api/users/userId/foo"
        )
    }

    @Test
    fun `should report error when all async calls threw exceptions`() {
        val latch = CountDownLatch(1)
        val failures = AtomicInteger(0)
        client.getWithPlaceHolderValue("userId", "headerValue").enqueue(object : Callback<NamedObject> {
            @Override
            override fun onResponse(call: Call<NamedObject>, response: Response<NamedObject>) {
                fail("no success expected")
            }

            @Override
            override fun onFailure(call: Call<NamedObject>, throwable: Throwable) {
                failures.incrementAndGet()
                latch.countDown()
            }
        })

        latch.await(1, TimeUnit.SECONDS) // wait for async to complete
        assertThat(failures.get()).isEqualTo(1)

        assertThat(server.getRecordedRequests().map { it.path }).containsExactly(
                "/api/users/userId/foo",
                "/api/users/userId/foo",
                "/api/users/userId/foo"
        )
    }

    @Test
    fun `should report success when retry condition not met but now exception thrown`() {
        repeat(maxAttempts) {
            addResponse(MockResponse().apply {
                setBody(RESPONSE_BODY)
                setResponseCode(500)
            })
        }
        val latch = CountDownLatch(maxAttempts)
        client.getWithPlaceHolderValue("userId", "headerValue").enqueue(object : Callback<NamedObject> {
            @Override
            override fun onResponse(call: Call<NamedObject>, response: Response<NamedObject>) {
                latch.countDown()
            }

            @Override
            override fun onFailure(call: Call<NamedObject>, t: Throwable) {
                fail("no exception expected", t)
            }
        })
        latch.await(1, TimeUnit.SECONDS) // wait for async to complete
        assertThat(server.getRecordedRequests().map { it.path }).containsExactly(
                "/api/users/userId/foo",
                "/api/users/userId/foo",
                "/api/users/userId/foo"
        )
    }

    private fun MockWebServer.getRecordedRequests(
      maxDuration: Duration = Duration.ofMillis(100)
    ): List<RecordedRequest> = generateSequence {
        takeRequest(maxDuration.toMillis(), TimeUnit.MILLISECONDS)
    }.toList()

    private fun baseUrl() = server.url("/").toString()

    private fun assertResponse(response: Response<NamedObject>, status: Int, body: Any?) {
        assertThat(response.code()).isEqualTo(status)
        assertThat(response.body()).isEqualTo(body)
    }

    /**
     * A test client interface
     */
    interface SomeClient {
        @GET("/")
        fun root(): Call<NamedObject>

        @POST("/new/nonidempotent/transaction")
        fun createTransaction(): Call<NamedObject>

        @GET("api/users/{userId}/foo")
        fun getWithPlaceHolderValue(@Path("userId") userId: String, @Header("some") someHeader: String): Call<NamedObject>
    }

    /**
     * A test data class
     */
    class NamedObject(val name: String) {
        override fun hashCode() = Objects.hashCode(this.name)

        override fun equals(other: Any?) = (other as NamedObject).name == name
    }
}
