package net.niebes.resilience4j

import io.github.resilience4j.retry.Retry
import okhttp3.Request
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RetryingCall<T> internal constructor(
    private val wrappedCall: Call<T>,
    private val retry: Retry,
    private val runWithRetry: (Request) -> Boolean,
    private val retryContext: Retry.Context<Response<T>> = retry.context<Response<T>>() // otherwise we loose the retry count
) : Call<T> {

    override fun execute(): Response<T> =
        if (runWithRetry(request()))
            retry.executeCallable { executableCall().execute() }
        else
            executableCall().execute()

    override fun enqueue(callback: Callback<T>) = wrappedCall.enqueue(retriedCallback(callback))

    private fun retriedCallback(callback: Callback<T>): Callback<T> = object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) =
            if (runWithRetry(request()) && retryContext.onResult(response))
                executableCall().enqueue(retriedCallback(callback))
            else
                callback.onResponse(call, response)

        override fun onFailure(call: Call<T>, throwable: Throwable) =
            if (runWithRetry(request())) {
                try {
                    retryContext.onError(asException(throwable)) // throws the throwable when tries are exhausted
                    executableCall().enqueue(retriedCallback(callback))
                } catch (ignored: Throwable) {
                    callback.onFailure(call, throwable)
                }
            } else
                callback.onFailure(call, throwable)

        /**
         * resilience4j accepts exceptions only
         */
        private fun asException(throwable: Throwable) =
            if (throwable is Exception) throwable else RuntimeException("masked throwable", throwable)
    }

    override fun clone(): Call<T> = RetryingCall<T>(executableCall(), retry, runWithRetry, retryContext)

    private fun executableCall(): Call<T> =
        if (wrappedCall.isExecuted)
            wrappedCall.clone()
        else
            wrappedCall

    override fun isExecuted(): Boolean = wrappedCall.isExecuted

    override fun isCanceled(): Boolean = wrappedCall.isCanceled

    override fun cancel() = wrappedCall.cancel()

    override fun request(): Request = wrappedCall.request()
}
