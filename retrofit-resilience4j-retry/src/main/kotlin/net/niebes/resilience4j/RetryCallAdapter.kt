package net.niebes.resilience4j

import io.github.resilience4j.retry.Retry
import java.lang.reflect.Type
import okhttp3.Request
import retrofit2.Call
import retrofit2.CallAdapter

class RetryCallAdapter<OriginalType, TargetType>(
    private val nextCallAdapter: CallAdapter<OriginalType, TargetType>,
    private val retry: Retry,
    private val shouldRetry: (Request) -> Boolean
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
