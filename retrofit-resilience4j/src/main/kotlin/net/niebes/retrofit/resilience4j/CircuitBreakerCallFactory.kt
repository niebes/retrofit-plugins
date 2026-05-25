package net.niebes.retrofit.resilience4j

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.core.StopWatch
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

class CircuitBreakerCallFactory(
    private val circuitBreaker: CircuitBreaker,
    private val successResponse: (Response<out Any?>) -> Boolean = { it.isSuccessful },
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
        return CircuitBreakerCallAdapter(nextCallAdapter, circuitBreaker, successResponse)
    }

    companion object {
        @JvmStatic
        fun of(circuitBreaker: CircuitBreaker): CircuitBreakerCallFactory = CircuitBreakerCallFactory(circuitBreaker)

        @JvmStatic
        fun of(
            circuitBreaker: CircuitBreaker,
            successResponse: (Response<out Any?>) -> Boolean,
        ): CircuitBreakerCallFactory = CircuitBreakerCallFactory(circuitBreaker, successResponse)
    }
}

private class CircuitBreakerCallAdapter<OriginalType, TargetType : Any>(
    private val nextCallAdapter: CallAdapter<OriginalType, TargetType>,
    private val circuitBreaker: CircuitBreaker,
    private val successResponse: (Response<out Any?>) -> Boolean,
) : CallAdapter<OriginalType, TargetType> {
    override fun responseType(): Type = nextCallAdapter.responseType()

    override fun adapt(call: Call<OriginalType>): TargetType =
        nextCallAdapter.adapt(CircuitBreakingCall(call, circuitBreaker, successResponse))
}

private class CircuitBreakingCall<T>(
    private val wrappedCall: Call<T>,
    private val circuitBreaker: CircuitBreaker,
    private val successResponse: (Response<out Any?>) -> Boolean,
) : Call<T> {
    override fun execute(): Response<T> {
        circuitBreaker.acquirePermission()
        val stopWatch = StopWatch.start()
        try {
            val response = wrappedCall.execute()
            val elapsed = stopWatch.stop().toNanos()
            if (successResponse(response)) {
                circuitBreaker.onResult(elapsed, TimeUnit.NANOSECONDS, response)
            } else {
                circuitBreaker.onError(
                    elapsed,
                    TimeUnit.NANOSECONDS,
                    Throwable("Response error: HTTP ${response.code()} - ${response.message()}")
                )
            }
            return response
        } catch (exception: Exception) {
            if (wrappedCall.isCanceled) {
                circuitBreaker.releasePermission()
            } else {
                circuitBreaker.onError(stopWatch.stop().toNanos(), TimeUnit.NANOSECONDS, exception)
            }
            throw exception
        }
    }

    override fun enqueue(callback: Callback<T>) {
        try {
            circuitBreaker.acquirePermission()
        } catch (e: CallNotPermittedException) {
            callback.onFailure(wrappedCall, e)
            return
        }
        val startNanos = System.nanoTime()
        wrappedCall.enqueue(
            object : Callback<T> {
                override fun onResponse(
                    call: Call<T>,
                    response: Response<T>,
                ) {
                    val elapsed = System.nanoTime() - startNanos
                    if (successResponse(response)) {
                        circuitBreaker.onResult(elapsed, TimeUnit.NANOSECONDS, response)
                    } else {
                        circuitBreaker.onError(
                            elapsed,
                            TimeUnit.NANOSECONDS,
                            Throwable("Response error: HTTP ${response.code()} - ${response.message()}")
                        )
                    }
                    callback.onResponse(call, response)
                }

                override fun onFailure(
                    call: Call<T>,
                    throwable: Throwable,
                ) {
                    if (call.isCanceled) {
                        circuitBreaker.releasePermission()
                    } else {
                        circuitBreaker.onError(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS, throwable)
                    }
                    callback.onFailure(call, throwable)
                }
            }
        )
    }

    override fun clone(): Call<T> = CircuitBreakingCall(wrappedCall.clone(), circuitBreaker, successResponse)

    override fun isExecuted(): Boolean = wrappedCall.isExecuted

    override fun isCanceled(): Boolean = wrappedCall.isCanceled

    override fun cancel() = wrappedCall.cancel()

    override fun request(): Request = wrappedCall.request()

    override fun timeout(): Timeout = wrappedCall.timeout()
}
