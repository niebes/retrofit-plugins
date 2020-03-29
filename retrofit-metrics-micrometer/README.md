# retrofit metrics micrometer
this plugin adds rate and p99 metrics into `http.client.requests` for all requests including the tags

| tag           | purpose                                          |
| ------------- |:-------------------------------------------------|
| base_url      | base_url                                         |
| uri           | uri with placeholders                            |
| method        | http method                                      |
| async         | true for `execute()` false for `enqueue()`       |
| status        | response status                                  |
| series        | response status family                           |
| exception     | `simpleName` of the response exception           |


## usage
### add dependency
`implementation(group = "net.niebes", name = "retrofit-metrics-micrometer", version = "add a version")`

### code
add metrics via
`addCallAdapterFactory(MicrometerRetrofitMetricsFactory())` into your Retrofit Builder

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
            .addCallAdapterFactory(MicrometerRetrofitMetricsFactory())
            .build().create(MyApi::class.java)

        private fun retrofitObjectMapper(): ObjectMapper = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModules(
                KotlinModule()
            )
            .findAndRegisterModules()
```
