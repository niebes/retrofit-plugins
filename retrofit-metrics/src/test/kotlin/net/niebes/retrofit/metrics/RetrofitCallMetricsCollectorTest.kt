package net.niebes.retrofit.metrics

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.time.Duration

internal class RetrofitCallMetricsCollectorTest {
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

    private val collector =
        RetrofitCallMetricsCollector(
            "https://api.example.com/",
            "users/{id}",
            metricsRecorder
        )

    private val request =
        Request
            .Builder()
            .url("https://api.example.com/users/42")
            .get()
            .build()

    @Test
    fun `measureRequestDuration records correct tags for successful response`() {
        val okResponse =
            okhttp3.Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("OK")
                .build()
        val response = Response.success<String>("body", okResponse)

        collector.measureRequestDuration(Duration.ofMillis(150), request, response, false)

        assertThat(recordedCalls).hasSize(1)
        val (tags, duration) = recordedCalls.first()
        assertThat(tags).containsEntry("base_url", "https://api.example.com/")
        assertThat(tags).containsEntry("uri", "users/{id}")
        assertThat(tags).containsEntry("method", "GET")
        assertThat(tags).containsEntry("async", "false")
        assertThat(tags).containsEntry("status", "200")
        assertThat(tags).containsEntry("series", "SUCCESSFUL")
        assertThat(tags).containsEntry("exception", "None")
        assertThat(duration).isEqualTo(Duration.ofMillis(150))
    }

    @Test
    fun `measureRequestDuration records async tag correctly`() {
        val okResponse =
            okhttp3.Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("OK")
                .build()
        val response = Response.success<String>("body", okResponse)

        collector.measureRequestDuration(Duration.ofMillis(50), request, response, true)

        val (tags, _) = recordedCalls.first()
        assertThat(tags).containsEntry("async", "true")
    }

    @Test
    fun `measureRequestDuration records server error series`() {
        val errorResponse =
            okhttp3.Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(503)
                .message("Service Unavailable")
                .build()
        val response =
            Response.error<String>("error".toResponseBody("application/json".toMediaType()), errorResponse)

        collector.measureRequestDuration(Duration.ofMillis(10), request, response, false)

        val (tags, _) = recordedCalls.first()
        assertThat(tags).containsEntry("status", "503")
        assertThat(tags).containsEntry("series", "SERVER_ERROR")
    }

    @Test
    fun `measureRequestException records exception tags`() {
        val exception = java.net.SocketTimeoutException("connect timed out")

        collector.measureRequestException(Duration.ofMillis(1000), request, exception, false)

        assertThat(recordedCalls).hasSize(1)
        val (tags, duration) = recordedCalls.first()
        assertThat(tags).containsEntry("base_url", "https://api.example.com/")
        assertThat(tags).containsEntry("uri", "users/{id}")
        assertThat(tags).containsEntry("method", "GET")
        assertThat(tags).containsEntry("async", "false")
        assertThat(tags).containsEntry("status", "Exception")
        assertThat(tags).containsEntry("series", "EXCEPTION")
        assertThat(tags).containsEntry("exception", "SocketTimeoutException")
        assertThat(duration).isEqualTo(Duration.ofMillis(1000))
    }

    @Test
    fun `measureRequestException records async exception`() {
        val exception = java.io.IOException("connection reset")

        collector.measureRequestException(Duration.ofMillis(500), request, exception, true)

        val (tags, _) = recordedCalls.first()
        assertThat(tags).containsEntry("async", "true")
        assertThat(tags).containsEntry("exception", "IOException")
    }
}
