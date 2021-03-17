package net.niebes.retrofit.metrics.micrometer

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.time.Duration
import net.niebes.retrofit.metrics.MetricsRecorder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MicrometerMetricsRecorder(
    private val meterRegistry: MeterRegistry,
    customMetricsKey: String?
) : MetricsRecorder {
    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val metricsKey = customMetricsKey ?: DEFAULT_METRICS_KEY

    override fun recordTiming(tags: Map<String, String>, duration: Duration) {
        log.info("measure {} with tags {} duration {}ms", metricsKey, tags, duration.toMillis())
        meterRegistry.timer(metricsKey, asTags(tags)).record(duration)
    }

    companion object {
        private fun asTags(tags: Map<String, String>) =
            tags.map { Tag.of(it.key, it.value) }

        const val DEFAULT_METRICS_KEY = "http.client.requests"
    }
}
