package net.niebes.retrofit.resilience4j

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
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
internal class CircuitBreakerCallFactoryTest {
    private lateinit var circuitBreaker: CircuitBreaker

    @BeforeEach
    fun before() {
        circuitBreaker =
            CircuitBreaker.of(
                "test",
                CircuitBreakerConfig
                    .custom()
                    .slidingWindowSize(2)
                    .minimumNumberOfCalls(2)
                    .failureRateThreshold(50f)
                    .waitDurationInOpenState(Duration.ofSeconds(60))
                    .build()
            )
    }

    @Test
    fun `successful response records success`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withBody("ok")))
        val client = createClient(wmRuntimeInfo, CircuitBreakerCallFactory(circuitBreaker))

        val response = client.get().execute()

        assertThat(response.code()).isEqualTo(200)
        assertThat(circuitBreaker.metrics.numberOfSuccessfulCalls).isEqualTo(1)
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(0)
        verify(1, getRequestedFor(urlEqualTo("/api/test")))
    }

    @Test
    fun `server error records failure with default predicate`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withStatus(500).withBody("error")))
        val client = createClient(wmRuntimeInfo, CircuitBreakerCallFactory(circuitBreaker))

        val response = client.get().execute()

        assertThat(response.code()).isEqualTo(500)
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(1)
    }

    @Test
    fun `custom success predicate treats 4xx as success`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withStatus(404).withBody("not found")))
        val client =
            createClient(
                wmRuntimeInfo,
                CircuitBreakerCallFactory(circuitBreaker) { it.code() < 500 }
            )

        val response = client.get().execute()

        assertThat(response.code()).isEqualTo(404)
        assertThat(circuitBreaker.metrics.numberOfSuccessfulCalls).isEqualTo(1)
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(0)
    }

    @Test
    fun `open circuit throws CallNotPermittedException on execute`(wmRuntimeInfo: WireMockRuntimeInfo) {
        circuitBreaker.transitionToOpenState()
        val client = createClient(wmRuntimeInfo, CircuitBreakerCallFactory(circuitBreaker))

        assertThrows(CallNotPermittedException::class.java) {
            client.get().execute()
        }
        verify(0, getRequestedFor(urlEqualTo("/api/test")))
    }

    @Test
    fun `open circuit calls onFailure on enqueue`(wmRuntimeInfo: WireMockRuntimeInfo) {
        circuitBreaker.transitionToOpenState()
        val client = createClient(wmRuntimeInfo, CircuitBreakerCallFactory(circuitBreaker))
        val latch = CountDownLatch(1)
        val failureRef = AtomicReference<Throwable>()

        client.get().enqueue(
            object : Callback<String> {
                override fun onResponse(
                    call: Call<String>,
                    response: Response<String>,
                ) {
                    fail("expected failure")
                }

                override fun onFailure(
                    call: Call<String>,
                    throwable: Throwable,
                ) {
                    failureRef.set(throwable)
                    latch.countDown()
                }
            }
        )

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
        assertThat(failureRef.get()).isInstanceOf(CallNotPermittedException::class.java)
        verify(0, getRequestedFor(urlEqualTo("/api/test")))
    }

    @Test
    fun `timeout records circuit breaker error`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withFixedDelay(2000)))
        val client = createClient(wmRuntimeInfo, CircuitBreakerCallFactory(circuitBreaker))

        try {
            client.get().execute()
        } catch (_: Exception) {
        }

        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(1)
    }

    @Test
    fun `async success callback records success`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withBody("ok")))
        val client = createClient(wmRuntimeInfo, CircuitBreakerCallFactory(circuitBreaker))
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

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
        assertThat(responseRef.get().code()).isEqualTo(200)
        assertThat(circuitBreaker.metrics.numberOfSuccessfulCalls).isEqualTo(1)
    }

    @Test
    fun `async error callback records failure`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withFixedDelay(2000)))
        val client = createClient(wmRuntimeInfo, CircuitBreakerCallFactory(circuitBreaker))
        val latch = CountDownLatch(1)

        client.get().enqueue(
            object : Callback<String> {
                override fun onResponse(
                    call: Call<String>,
                    response: Response<String>,
                ) {
                    fail("expected failure")
                }

                override fun onFailure(
                    call: Call<String>,
                    throwable: Throwable,
                ) {
                    latch.countDown()
                }
            }
        )

        latch.await(2, TimeUnit.SECONDS)
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(1)
    }

    @Test
    fun `async server error records failure`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withStatus(500).withBody("error")))
        val client = createClient(wmRuntimeInfo, CircuitBreakerCallFactory(circuitBreaker))
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
                    fail("expected onResponse with 500", throwable)
                }
            }
        )

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
        assertThat(responseRef.get().code()).isEqualTo(500)
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(1)
    }

    @Test
    fun `cancellation releases permission without recording error`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withFixedDelay(2000)))
        val client = createClient(wmRuntimeInfo, CircuitBreakerCallFactory(circuitBreaker))
        val call = client.get()

        kotlin.concurrent.thread {
            Thread.sleep(50)
            call.cancel()
        }

        try {
            call.execute()
        } catch (_: Exception) {
        }

        assertThat(call.isCanceled).isTrue()
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(0)
        assertThat(circuitBreaker.metrics.numberOfSuccessfulCalls).isEqualTo(0)
        assertThat(circuitBreaker.metrics.numberOfNotPermittedCalls).isEqualTo(0)
    }

    @Test
    fun `enough failures transition circuit to open`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withStatus(500).withBody("error")))
        val client = createClient(wmRuntimeInfo, CircuitBreakerCallFactory(circuitBreaker))

        client.get().execute()
        client.get().execute()

        assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.OPEN)
        assertThrows(CallNotPermittedException::class.java) {
            client.get().execute()
        }
    }

    @Test
    fun `clone produces usable independent call`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withBody("ok")))
        val client = createClient(wmRuntimeInfo, CircuitBreakerCallFactory(circuitBreaker))
        val original = client.get()
        original.execute()

        val cloned = original.clone()
        val response = cloned.execute()

        assertThat(response.code()).isEqualTo(200)
        verify(2, getRequestedFor(urlEqualTo("/api/test")))
    }

    @Test
    fun `of factory method creates working adapter`(wmRuntimeInfo: WireMockRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withBody("ok")))
        val factory = CircuitBreakerCallFactory.of(circuitBreaker) { it.code() < 500 }
        val client = createClient(wmRuntimeInfo, factory)

        val response = client.get().execute()

        assertThat(response.code()).isEqualTo(200)
        assertThat(circuitBreaker.metrics.numberOfSuccessfulCalls).isEqualTo(1)
    }

    private fun createClient(
        wmRuntimeInfo: WireMockRuntimeInfo,
        factory: CircuitBreakerCallFactory,
    ): TestClient =
        Retrofit
            .Builder()
            .client(
                OkHttpClient
                    .Builder()
                    .connectTimeout(Duration.ofMillis(500))
                    .readTimeout(Duration.ofMillis(500))
                    .writeTimeout(Duration.ofMillis(500))
                    .build()
            ).addConverterFactory(ScalarsConverterFactory.create())
            .addCallAdapterFactory(factory)
            .baseUrl(wmRuntimeInfo.httpBaseUrl)
            .build()
            .create(TestClient::class.java)
}
