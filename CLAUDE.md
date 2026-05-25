# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

- **Build & test all modules:** `./mvnw verify --no-transfer-progress`
- **Build without tests:** `./mvnw package -DskipTests`
- **Run a single test:** `./mvnw test -pl retrofit-metrics -Dtest=MicrometerRetrofitMetricsFactoryTest` (use `-pl <module>` to target a module)
- **Format code:** `./mvnw ktlint:format` (runs ktlint auto-format)
- **Check formatting:** `./mvnw ktlint:check` (also runs automatically during `./mvnw verify`)

## Project Overview

Kotlin library providing Retrofit2 `CallAdapter.Factory` plugins. Maven multi-module project targeting JVM 21. Published to Maven Central under `net.niebes` group. Current version: `1.20.0-SNAPSHOT`. Licensed under MIT.

### Modules

- **retrofit-metrics** — Core metrics module. Defines `MetricsRecorder` interface and wraps Retrofit calls (`MeasuredCall`) to record HTTP timing/tags via `RetrofitCallMetricsCollector`. Tags include base URL, URI template (not resolved path), HTTP method, and response status series.
- **retrofit-metrics-micrometer** — `MetricsRecorder` implementation using Micrometer. Depends on `retrofit-metrics`.
- **retrofit-metrics-statsd** — `MetricsRecorder` implementation using StatsD (via DataDog `java-dogstatsd-client`). Depends on `retrofit-metrics`.
- **retrofit-resilience4j** — Circuit breaker and rate limiter adapters for Retrofit using resilience4j 2.4.0. Ports the dropped `resilience4j-retrofit` module to work with Retrofit 3 and OkHttp 5. Contains `CircuitBreakerCallFactory` and `RateLimiterCallFactory`.
- **retrofit-resilience4j-retry** — Wraps Retrofit calls with resilience4j `Retry`. By default only retries GET requests; configurable via `shouldRetry` predicate.

### Architecture Pattern

Each plugin implements `CallAdapter.Factory`, which Retrofit calls for each service method. The factory delegates to the next adapter in the chain (`retrofit.nextCallAdapter(...)`) and wraps the resulting `Call` with its own behavior. This composable design means plugins are added via `Retrofit.Builder.addCallAdapterFactory(...)`.

Two variant patterns exist:
- **Metrics factories** (`RetrofitMetricsFactory` and subclasses) extract the URI template from Retrofit HTTP annotations (`@GET`, `@POST`, etc.) to record route-level metrics rather than per-path metrics. They return `null` from `get()` if no annotation is found, skipping non-HTTP methods.
- **Resilience4j factories** (`CircuitBreakerCallFactory`, `RateLimiterCallFactory`, `RetryCallFactory`) wrap every `Call` unconditionally — they don't inspect annotations. Each wraps `Call.execute()` and `Call.enqueue()` with the resilience4j primitive (circuit breaker state machine, rate limiter permit, retry loop).

### Test Infrastructure

All integration tests use WireMock (`@WireMockTest` annotation with `WireMockRuntimeInfo` injected into `@BeforeEach`). Tests use `ScalarsConverterFactory` from `retrofit2:converter-scalars` for simple string responses. Shared test interfaces (e.g., `TestClient`) define Retrofit service methods used across test classes within a module.

## Tech Stack

- Kotlin 2.3 with Maven (kotlin-maven-plugin), JVM target 21
- Retrofit 3 + OkHttp 5
- JUnit Jupiter 6 + AssertJ + Mockito + WireMock for tests
- ktlint via ktlint-maven-plugin (enforced in verify phase)
- CI: GitHub Actions (`maven.yml`), main branch is `develop`, runs `./mvnw verify`

## Code Style

- ktlint checks run automatically on `./mvnw verify` — run `./mvnw ktlint:format` before committing to auto-fix

## Workflow

- `develop` is the protected main branch — never push directly
- Create feature branches and open PRs against `develop`
- Ask before assuming scope — confirm with the user what to include in commits and PRs
