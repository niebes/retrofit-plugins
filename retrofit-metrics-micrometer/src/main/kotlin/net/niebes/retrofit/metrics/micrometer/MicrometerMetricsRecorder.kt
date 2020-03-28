package net.niebes.retrofit.metrics.micrometer

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import net.niebes.retrofit.metrics.MetricsRecorder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

class MicrometerMetricsRecorder(private val meterRegistry: MeterRegistry) : MetricsRecorder {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    override fun recordTiming(tags: Map<String, String>, duration: Duration) {
        log.info("measure {} with tags {} duration {}ms", METRICS_KEY, tags, duration.toMillis())
        meterRegistry.timer(METRICS_KEY, asTags(tags)).record(duration)
    }

    companion object {
        private fun asTags(tags: Map<String, String>) =
            tags.map { Tag.of(it.key, it.value) }

        const val METRICS_KEY = "http.client.requests"
    }
}
