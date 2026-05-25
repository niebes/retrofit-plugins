package net.niebes.retrofit.resilience4j

class HttpResponseException(
    val statusCode: Int,
    message: String,
) : RuntimeException("HTTP $statusCode - $message")
