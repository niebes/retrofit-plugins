package net.niebes.retrofit.metrics

import okhttp3.Request
import retrofit2.Response
import java.time.Duration

class RetrofitCallMetricsCollector(
    private val baseUrl: String,
    private val uri: String,
    private val metricsRecorder: MetricsRecorder,
) {

    fun measureRequestDuration(
        duration: Duration,
        request: Request,
        response: Response<out Any?>,
        async: Boolean,
    ) = metricsRecorder.recordTiming(
        mapOf(
            "base_url" to baseUrl,
            "uri" to uri,
            "method" to request.method,
            "async" to async.toString(),
            "series" to (HttpSeries.fromHttpStatus(response.code())?.name ?: "UNKNOWN"),
            "status" to response.code().toString(),
            "exception" to "None"
        ),
        duration
    )

    fun measureRequestException(
        duration: Duration,
        request: Request,
        throwable: Throwable,
        async: Boolean,
    ) = metricsRecorder.recordTiming(
        mapOf(
            "base_url" to baseUrl,
            "uri" to uri,
            "method" to request.method,
            "async" to async.toString(),
            "exception" to throwable.javaClass.simpleName,
            "series" to "EXCEPTION",
            "status" to "Exception"
        ),
        duration
    )
}
