package net.niebes.resilience4j

import io.github.resilience4j.retry.Retry
import okhttp3.Request
import retrofit2.Call
import retrofit2.CallAdapter
import java.lang.reflect.Type

class RetryCallAdapter<OriginalType, TargetType>(
    private val nextCallAdapter: CallAdapter<OriginalType, TargetType>,
    private val retry: Retry,
    private val shouldRetry: (Request) -> Boolean,
) : CallAdapter<OriginalType, TargetType> {
    override fun responseType(): Type = nextCallAdapter.responseType()
    override fun adapt(call: Call<OriginalType>): TargetType = nextCallAdapter.adapt(
        RetryingCall(
            call,
            retry,
            shouldRetry
        )
    )
}
