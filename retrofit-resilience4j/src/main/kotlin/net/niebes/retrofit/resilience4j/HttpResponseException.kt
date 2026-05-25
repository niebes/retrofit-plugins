package net.niebes.retrofit.resilience4j

class HttpResponseException(
    val statusCode: Int,
    message: String?,
) : RuntimeException("HTTP $statusCode" + if (message != null) " - $message" else "")
