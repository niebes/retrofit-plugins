package net.niebes.retrofit.metrics.micrometer

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import net.niebes.retrofit.metrics.RetrofitMetricsFactory

class MicrometerRetrofitMetricsFactory(
    meterRegistry: MeterRegistry = Metrics.globalRegistry,
    customMetricsKey: String? = null,
) : RetrofitMetricsFactory(MicrometerMetricsRecorder(meterRegistry, customMetricsKey))
