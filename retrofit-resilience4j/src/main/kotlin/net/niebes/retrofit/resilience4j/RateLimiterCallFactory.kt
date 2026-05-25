package net.niebes.retrofit.resilience4j

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.Type

class RateLimiterCallFactory(
    private val rateLimiter: RateLimiter,
) : CallAdapter.Factory() {
    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit,
    ): CallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java) {
            return null
        }
        val nextCallAdapter = retrofit.nextCallAdapter(this, returnType, annotations)
        return RateLimiterCallAdapter(nextCallAdapter, rateLimiter)
    }

    companion object {
        @JvmStatic
        fun of(rateLimiter: RateLimiter): RateLimiterCallFactory = RateLimiterCallFactory(rateLimiter)
    }
}

private class RateLimiterCallAdapter<OriginalType, TargetType : Any>(
    private val nextCallAdapter: CallAdapter<OriginalType, TargetType>,
    private val rateLimiter: RateLimiter,
) : CallAdapter<OriginalType, TargetType> {
    override fun responseType(): Type = nextCallAdapter.responseType()

    override fun adapt(call: Call<OriginalType>): TargetType = nextCallAdapter.adapt(RateLimitingCall(call, rateLimiter))
}

private class RateLimitingCall<T>(
    private val wrappedCall: Call<T>,
    private val rateLimiter: RateLimiter,
) : Call<T> {
    @Volatile
    private var executed = false

    override fun execute(): Response<T> {
        executed = true
        try {
            RateLimiter.waitForPermission(rateLimiter)
        } catch (e: RequestNotPermitted) {
            return tooManyRequestsError()
        } catch (e: IllegalStateException) {
            return tooManyRequestsError()
        }
        return wrappedCall.execute()
    }

    override fun enqueue(callback: Callback<T>) {
        executed = true
        try {
            RateLimiter.waitForPermission(rateLimiter)
        } catch (e: RequestNotPermitted) {
            callback.onResponse(wrappedCall, tooManyRequestsError())
            return
        } catch (e: IllegalStateException) {
            callback.onResponse(wrappedCall, tooManyRequestsError())
            return
        }
        wrappedCall.enqueue(callback)
    }

    private fun tooManyRequestsError(): Response<T> =
        Response.error(
            429,
            "Too many requests for the client".toResponseBody("text/plain".toMediaType())
        )

    override fun clone(): Call<T> = RateLimitingCall(wrappedCall.clone(), rateLimiter)

    override fun isExecuted(): Boolean = executed

    override fun isCanceled(): Boolean = wrappedCall.isCanceled

    override fun cancel() = wrappedCall.cancel()

    override fun request(): Request = wrappedCall.request()

    override fun timeout(): Timeout = wrappedCall.timeout()
}
