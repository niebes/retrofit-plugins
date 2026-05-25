# retrofit-resilience4j

Drop-in replacement for the discontinued `io.github.resilience4j:resilience4j-retrofit` module.

Provides circuit breaker and rate limiter `CallAdapter.Factory` implementations for Retrofit 3, OkHttp 5, and resilience4j 2.4.

## migrating from resilience4j-retrofit

The original `resilience4j-retrofit` module was removed in resilience4j 2.0 ([#1677](https://github.com/resilience4j/resilience4j/pull/1677)). This module provides equivalent functionality with no vavr dependency.

### dependency

Replace:
```kotlin
implementation("io.github.resilience4j:resilience4j-retrofit:1.7.1")
```
With:
```kotlin
implementation("net.niebes:retrofit-resilience4j:<version>")
```

### code changes

Circuit breaker — update import and factory name:
```diff
-import io.github.resilience4j.retrofit.CircuitBreakerCallAdapter
+import net.niebes.retrofit.resilience4j.CircuitBreakerCallFactory

 Retrofit.Builder()
-    .addCallAdapterFactory(CircuitBreakerCallAdapter.of(circuitBreaker) { response ->
+    .addCallAdapterFactory(CircuitBreakerCallFactory(circuitBreaker) { response ->
         response.code() < 500
     })
     .build()
```

Rate limiter — same pattern:
```diff
-import io.github.resilience4j.retrofit.RateLimiterCallAdapter
+import net.niebes.retrofit.resilience4j.RateLimiterCallFactory

 Retrofit.Builder()
-    .addCallAdapterFactory(RateLimiterCallAdapter.of(rateLimiter))
+    .addCallAdapterFactory(RateLimiterCallFactory(rateLimiter))
     .build()
```

### retry

The original module included a basic retry adapter. For retry support with configurable per-request filtering (e.g., only retry GET requests), use the [retrofit-resilience4j-retry](../retrofit-resilience4j-retry) module instead.
