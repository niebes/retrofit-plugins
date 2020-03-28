package net.niebes.retrofit.metrics

import com.google.common.base.Stopwatch
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MeasuredCall<T> internal constructor(
    private val wrappedCall: Call<T>,
    private val metrics: RetrofitCallMetricsCollector
) : Call<T> {

    override fun execute(): Response<T> {
        val stopwatch = Stopwatch.createStarted()
        val request = wrappedCall.request()
        try {
            val response = wrappedCall.execute()
            metrics.measureRequestDuration(stopwatch.elapsed(), request, response, false)
            return response
        } catch (exception: Exception) {
            metrics.measureRequestException(stopwatch.elapsed(), request, exception, false)
            throw exception
        }
    }

    override fun enqueue(callback: Callback<T>) = wrappedCall.enqueue(measuredCallback(wrappedCall.request(), callback))

    private fun measuredCallback(request: Request, callback: Callback<T>): Callback<T> = object : Callback<T> {
        val stopwatch = Stopwatch.createStarted()
        override fun onResponse(call: Call<T>, response: Response<T>) {
            metrics.measureRequestDuration(stopwatch.elapsed(), request, response, true)
            callback.onResponse(call, response)
        }

        override fun onFailure(call: Call<T>, throwable: Throwable) {
            metrics.measureRequestException(stopwatch.elapsed(), request, throwable, true)
            callback.onFailure(call, throwable)
        }
    }

    override fun isExecuted(): Boolean = wrappedCall.isExecuted

    override fun isCanceled(): Boolean = wrappedCall.isCanceled

    override fun cancel() = wrappedCall.cancel()

    override fun clone(): Call<T> = MeasuredCall<T>(wrappedCall.clone(), metrics)

    override fun request(): Request = wrappedCall.request()

    override fun timeout(): Timeout = wrappedCall.timeout()
}
