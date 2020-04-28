# retrofit retry

a retrofit wrapper for resilience4j retry


## usage
### add dependency
`implementation(group = "net.niebes", name = "retrofit-resilience4j-retry", version = "add a version")`

### code
```kotlin
Retrofit.Builder()
    .addConverterFactory(JacksonConverterFactory.create(retrofitObjectMapper()))
    .baseUrl(properties.baseUrl)
    .client(OkHttpClient.Builder()
         // your config
        .build())
    .addCallAdapterFactory(RetryCallFactory(Retry.of(
        "my-api-client", RetryConfig.custom<Response<out Any?>>()
        .retryOnResult { response -> response.code() in status500range() }
        .maxAttempts(properties.retries + 1)
        .intervalFunction(IntervalFunction.ofExponentialBackoff())
        .build())))
    .build().create(MyApi::class.java)
```

The request gets retried when it result's in either (1) any* exception or (1) your configured `retryOnResult` evaluates
 to true
* So far this wrapper retries all exceptions. While this might make sense for e.g. a SocketTimeout it might not for
 e.g. an SslException.


By default only GET requests get retried. To Retry different requests you can add your own clause
e.g.
```kotlin
    .addCallAdapterFactory(RetryCallFactory(
        retry = Retry.of("my-api-client", RetryConfig.custom<Response<out Any>>()
             [...]
            .build()),
        shouldRetry = {
            when {
                method == "GET" -> true
                method == "POST" && url.pathSegments.joinToString("/").contains("my/path") -> true
                else -> false
            }
        }))
```
