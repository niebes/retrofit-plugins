package net.niebes.retrofit.resilience4j

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HttpResponseExceptionTest {
    @Test
    fun `formats message with status code and message`() {
        val exception = HttpResponseException(503, "Service Unavailable")

        assertThat(exception.message).isEqualTo("HTTP 503 - Service Unavailable")
        assertThat(exception.statusCode).isEqualTo(503)
    }

    @Test
    fun `handles null message`() {
        val exception = HttpResponseException(502, null)

        assertThat(exception.message).isEqualTo("HTTP 502")
        assertThat(exception.statusCode).isEqualTo(502)
    }
}
