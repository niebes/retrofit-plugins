package net.niebes.retrofit.metrics.statsd

import com.timgroup.statsd.StatsDClient
import net.niebes.retrofit.metrics.RetrofitMetricsFactory

class StatsDRetrofitMetricsFactory(
    statsDClient: StatsDClient
) : RetrofitMetricsFactory(StatsDMetricsRecorder(statsDClient))
