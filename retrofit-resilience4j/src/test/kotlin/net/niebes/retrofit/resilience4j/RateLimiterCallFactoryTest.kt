package net.niebes.retrofit.resilience4j

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@WireMockTest
internal class RateLimiterCallFactoryTest {
    @Test
    fun `request permitted passes through`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withBody("ok")))
        val rateLimiter = createRateLimiter(limitForPeriod = 10)
        val client = createClient(wmRuntimeInfo, RateLimiterCallFactory(rateLimiter))

        val response = client.get().execute()

        assertThat(response.code()).isEqualTo(200)
        assertThat(response.body()).isEqualTo("ok")
        verify(1, getRequestedFor(urlEqualTo("/api/test")))
    }

    @Test
    fun `rate limited execute returns 429`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val rateLimiter = createExhaustedRateLimiter()
        val client = createClient(wmRuntimeInfo, RateLimiterCallFactory(rateLimiter))

        val response = client.get().execute()

        assertThat(response.code()).isEqualTo(429)
        verify(0, getRequestedFor(urlEqualTo("/api/test")))
    }

    @Test
    fun `rate limited enqueue returns 429 via onResponse`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val rateLimiter = createExhaustedRateLimiter()
        val client = createClient(wmRuntimeInfo, RateLimiterCallFactory(rateLimiter))
        val latch = CountDownLatch(1)
        val responseRef = AtomicReference<Response<String>>()

        client.get().enqueue(
            object : Callback<String> {
                override fun onResponse(
                    call: Call<String>,
                    response: Response<String>,
                ) {
                    responseRef.set(response)
                    latch.countDown()
                }

                override fun onFailure(
                    call: Call<String>,
                    throwable: Throwable,
                ) {
                    fail("expected onResponse with 429, not onFailure", throwable)
                }
            }
        )

        latch.await(1, TimeUnit.SECONDS)
        assertThat(responseRef.get().code()).isEqualTo(429)
        verify(0, getRequestedFor(urlEqualTo("/api/test")))
    }

    @Test
    fun `multiple requests within limit succeed`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withBody("ok")))
        val rateLimiter = createRateLimiter(limitForPeriod = 5)
        val client = createClient(wmRuntimeInfo, RateLimiterCallFactory(rateLimiter))

        repeat(3) {
            val response = client.get().execute()
            assertThat(response.code()).isEqualTo(200)
        }

        verify(3, getRequestedFor(urlEqualTo("/api/test")))
    }

    @Test
    fun `rate limited 429 response contains expected body`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val rateLimiter = createExhaustedRateLimiter()
        val client = createClient(wmRuntimeInfo, RateLimiterCallFactory(rateLimiter))

        val response = client.get().execute()

        assertThat(response.errorBody()?.string()).isEqualTo("Too many requests for the client")
    }

    @Test
    fun `async request permitted passes through`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withBody("ok")))
        val rateLimiter = createRateLimiter(limitForPeriod = 10)
        val client = createClient(wmRuntimeInfo, RateLimiterCallFactory(rateLimiter))
        val latch = CountDownLatch(1)
        val responseRef = AtomicReference<Response<String>>()

        client.get().enqueue(
            object : Callback<String> {
                override fun onResponse(
                    call: Call<String>,
                    response: Response<String>,
                ) {
                    responseRef.set(response)
                    latch.countDown()
                }

                override fun onFailure(
                    call: Call<String>,
                    throwable: Throwable,
                ) {
                    fail("no exception expected", throwable)
                }
            }
        )

        latch.await(1, TimeUnit.SECONDS)
        assertThat(responseRef.get().code()).isEqualTo(200)
        assertThat(responseRef.get().body()).isEqualTo("ok")
        verify(1, getRequestedFor(urlEqualTo("/api/test")))
    }

    @Test
    fun `clone produces usable independent call`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withBody("ok")))
        val rateLimiter = createRateLimiter(limitForPeriod = 10)
        val client = createClient(wmRuntimeInfo, RateLimiterCallFactory(rateLimiter))
        val original = client.get()
        original.execute()

        val cloned = original.clone()
        val response = cloned.execute()

        assertThat(response.code()).isEqualTo(200)
        verify(2, getRequestedFor(urlEqualTo("/api/test")))
    }

    private fun createRateLimiter(limitForPeriod: Int): RateLimiter =
        RateLimiter.of(
            "test",
            RateLimiterConfig
                .custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(100))
                .build()
        )

    private fun createExhaustedRateLimiter(): RateLimiter {
        val rateLimiter =
            RateLimiter.of(
                "test",
                RateLimiterConfig
                    .custom()
                    .limitForPeriod(1)
                    .limitRefreshPeriod(Duration.ofSeconds(60))
                    .timeoutDuration(Duration.ZERO)
                    .build()
            )
        rateLimiter.acquirePermission()
        return rateLimiter
    }

    private fun createClient(
        wmRuntimeInfo: WireMockRuntimeInfo,
        factory: RateLimiterCallFactory,
    ): TestClient =
        Retrofit
            .Builder()
            .client(
                OkHttpClient
                    .Builder()
                    .connectTimeout(Duration.ofSeconds(1))
                    .readTimeout(Duration.ofSeconds(1))
                    .writeTimeout(Duration.ofSeconds(1))
                    .build()
            ).addConverterFactory(ScalarsConverterFactory.create())
            .addCallAdapterFactory(factory)
            .baseUrl(wmRuntimeInfo.httpBaseUrl)
            .build()
            .create(TestClient::class.java)
}
