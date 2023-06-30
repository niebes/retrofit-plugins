package net.niebes.retrofit.metrics.statsd

import com.timgroup.statsd.StatsDClient
import net.niebes.retrofit.metrics.MetricsRecorder
import org.slf4j.LoggerFactory
import java.time.Duration

class StatsDMetricsRecorder(
    private val statsDClient: StatsDClient,
) : MetricsRecorder {
    private val log = LoggerFactory.getLogger(StatsDMetricsRecorder::class.java)
    override fun recordTiming(tags: Map<String, String>, duration: Duration) {
        log.info("measure {} with tags {}", KEY, tags)
        statsDClient.histogram(KEY, duration.toMillis(), *asTagsArray(tags))
    }

    private fun asTagsArray(tags: Map<String, String>): Array<String> =
        tags.entries.map(::tag).toTypedArray()

    private fun tag(entry: Map.Entry<String, String>): String =
        """${entry.key}:${entry.value}"""

    companion object {
        private const val KEY = "http.client.requests"
    }
}
