# retrofit-plugins

A collection of Retrofit `CallAdapter.Factory` plugins for metrics, resilience, and observability.

## modules

- **[retrofit-resilience4j](./retrofit-resilience4j)** - Circuit breaker and rate limiter for Retrofit using resilience4j 2.4. Drop-in replacement for the discontinued `io.github.resilience4j:resilience4j-retrofit`.
- **[retrofit-resilience4j-retry](./retrofit-resilience4j-retry)** - Retry with per-request filtering using resilience4j.
- **[retrofit-metrics](./retrofit-metrics)** - HTTP call metrics recording.
  - **[retrofit-metrics-micrometer](./retrofit-metrics-micrometer)** - Micrometer implementation.
  - **[retrofit-metrics-statsd](./retrofit-metrics-statsd)** - StatsD implementation.
