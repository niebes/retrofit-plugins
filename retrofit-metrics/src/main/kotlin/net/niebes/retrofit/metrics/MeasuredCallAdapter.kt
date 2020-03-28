package net.niebes.retrofit.metrics

import retrofit2.Call
import retrofit2.CallAdapter
import java.lang.reflect.Type

class MeasuredCallAdapter<OriginalType, TargetType> internal constructor(
    private val nextCallAdapter: CallAdapter<OriginalType, TargetType>,
    private val metricsCollector: RetrofitCallMetricsCollector
) : CallAdapter<OriginalType, TargetType> {

    override fun responseType(): Type = nextCallAdapter.responseType()
    override fun adapt(call: Call<OriginalType>): TargetType = nextCallAdapter.adapt(
        MeasuredCall(
            call,
            metricsCollector
        )
    )
}
