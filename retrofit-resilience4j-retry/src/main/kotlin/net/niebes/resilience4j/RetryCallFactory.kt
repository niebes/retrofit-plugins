package net.niebes.resilience4j

import io.github.resilience4j.retry.Retry
import okhttp3.Request
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * @param retry a configured retrofit retry executor, which gets active whenever shouldRetry results to true
 * @param shouldRetry describes which requests should get retried. by default only get requests get retried
 */
class RetryCallFactory(
    private val retry: Retry = Retry.ofDefaults("RetryCallFactory-default"),
    private val shouldRetry: Request.() -> Boolean = { method == "GET" }
) : CallAdapter.Factory() {
    override fun get(returnType: Type, annotations: Array<Annotation>, retrofit: Retrofit): CallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java) {
            return null
        }
        val nextCallAdapter = retrofit.nextCallAdapter(this, returnType, annotations)
        return RetryCallAdapter(nextCallAdapter, retry, shouldRetry)
    }
}
