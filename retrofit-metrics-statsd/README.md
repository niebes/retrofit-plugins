# retrofit metrics statsd
this plugin adds rate and p99 metrics into `http.client.requests` for all requests including the tags

| tag       | purpose                                          |
|-----------|:-------------------------------------------------|
| base_url  | base_url                                         |
| uri       | uri with placeholders                            |
| method    | http method                                      |
| async     | false for `execute()`, true for `enqueue()`       |
| status    | response status or `Exception`                   |
| series    | response status family or `EXCEPTION`            |
| exception | `simpleName` of the response exception or `None` |


## usage
### add dependency
`implementation(group = "net.niebes", name = "retrofit-metrics-statsd", version = "add a version")`

### code
add metrics via
`addCallAdapterFactory(StatsDRetrofitMetricsFactory(statsDClient))` into your Retrofit Builder

e.g.
```kotlin
        private fun myApi() = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(connectTimeoutDuration)
                    .readTimeout(readTimeoutDuration)
                    .writeTimeout(writeTimeoutDuration)
                    .callTimeout(callTimeoutDuration).build()
            )
            .addConverterFactory(JacksonConverterFactory.create(retrofitObjectMapper()))
            .addCallAdapterFactory(StatsDRetrofitMetricsFactory(statsDClient))
            .build().create(MyApi::class.java)

        private fun retrofitObjectMapper(): ObjectMapper = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModules(
                KotlinModule()
            )
            .findAndRegisterModules()
```
