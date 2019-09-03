package net.niebes.retrofit.metrics

import java.time.Duration

interface MetricsRecorder {
    fun recordTiming(tags: Map<String, String>, duration: Duration)
}
