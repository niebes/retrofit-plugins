package net.niebes.retrofit.metrics

import okhttp3.Protocol
import okhttp3.Request
import okio.Timeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class MeasuredCallTest {
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

    private val request = Request.Builder().url("https://example.com/test").get().build()
    private val collector = RetrofitCallMetricsCollector("https://example.com/", "test", metricsRecorder)

    @Test
    fun `execute records metrics on success`() {
        val okResponse =
            okhttp3.Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("OK")
                .build()
        val innerCall = FakeCall(Response.success("result", okResponse))
        val measuredCall = MeasuredCall(innerCall, collector)

        val response = measuredCall.execute()

        assertThat(response.body()).isEqualTo("result")
        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("async", "false")
        assertThat(recordedCalls.first().first).containsEntry("status", "200")
    }

    @Test
    fun `execute records metrics on exception`() {
        val innerCall = FakeCall<String>(exception = IOException("network error"))
        val measuredCall = MeasuredCall(innerCall, collector)

        assertThatThrownBy { measuredCall.execute() }
            .isInstanceOf(IOException::class.java)
            .hasMessage("network error")

        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("exception", "IOException")
        assertThat(recordedCalls.first().first).containsEntry("series", "EXCEPTION")
    }

    @Test
    fun `enqueue records metrics on async success`() {
        val okResponse =
            okhttp3.Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(201)
                .message("Created")
                .build()
        val innerCall = FakeCall(Response.success("created", okResponse))
        val measuredCall = MeasuredCall(innerCall, collector)

        val latch = CountDownLatch(1)
        var receivedBody: String? = null

        measuredCall.enqueue(
            object : Callback<String> {
                override fun onResponse(
                    call: Call<String>,
                    response: Response<String>,
                ) {
                    receivedBody = response.body()
                    latch.countDown()
                }

                override fun onFailure(
                    call: Call<String>,
                    t: Throwable,
                ) {
                    latch.countDown()
                }
            },
        )
        latch.await(1, TimeUnit.SECONDS)

        assertThat(receivedBody).isEqualTo("created")
        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("async", "true")
        assertThat(recordedCalls.first().first).containsEntry("status", "201")
    }

    @Test
    fun `enqueue records metrics on async failure`() {
        val innerCall = FakeCall<String>(exception = IOException("timeout"))
        val measuredCall = MeasuredCall(innerCall, collector)

        val latch = CountDownLatch(1)
        var receivedError: Throwable? = null

        measuredCall.enqueue(
            object : Callback<String> {
                override fun onResponse(
                    call: Call<String>,
                    response: Response<String>,
                ) {
                    latch.countDown()
                }

                override fun onFailure(
                    call: Call<String>,
                    t: Throwable,
                ) {
                    receivedError = t
                    latch.countDown()
                }
            },
        )
        latch.await(1, TimeUnit.SECONDS)

        assertThat(receivedError).isInstanceOf(IOException::class.java)
        assertThat(recordedCalls).hasSize(1)
        assertThat(recordedCalls.first().first).containsEntry("exception", "IOException")
    }

    @Test
    fun `clone returns new MeasuredCall`() {
        val okResponse =
            okhttp3.Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("OK")
                .build()
        val innerCall = FakeCall(Response.success("result", okResponse))
        val measuredCall = MeasuredCall(innerCall, collector)

        val cloned = measuredCall.clone()

        assertThat(cloned).isNotSameAs(measuredCall)
        assertThat(cloned).isInstanceOf(MeasuredCall::class.java)
    }

    @Test
    fun `delegates cancel and status to wrapped call`() {
        val okResponse =
            okhttp3.Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("OK")
                .build()
        val innerCall = FakeCall(Response.success("result", okResponse))
        val measuredCall = MeasuredCall(innerCall, collector)

        assertThat(measuredCall.isCanceled).isFalse()
        measuredCall.cancel()
        assertThat(innerCall.isCanceled).isTrue()
    }

    @Test
    fun `request delegates to wrapped call`() {
        val okResponse =
            okhttp3.Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("OK")
                .build()
        val innerCall = FakeCall(Response.success("result", okResponse))
        val measuredCall = MeasuredCall(innerCall, collector)

        assertThat(measuredCall.request()).isEqualTo(request)
    }

    private class FakeCall<T>(
        private val response: Response<T>? = null,
        private val exception: Exception? = null,
    ) : Call<T> {
        private var executed = false
        private var canceled = false

        override fun execute(): Response<T> {
            executed = true
            exception?.let { throw it }
            return response!!
        }

        override fun enqueue(callback: Callback<T>) {
            executed = true
            if (exception != null) {
                callback.onFailure(this, exception)
            } else {
                callback.onResponse(this, response!!)
            }
        }

        override fun isExecuted(): Boolean = executed

        override fun isCanceled(): Boolean = canceled

        override fun cancel() {
            canceled = true
        }

        override fun clone(): Call<T> = FakeCall(response, exception)

        override fun request(): Request =
            response?.raw()?.request
                ?: Request.Builder().url("https://example.com/test").get().build()

        override fun timeout(): Timeout = Timeout.NONE
    }
}
