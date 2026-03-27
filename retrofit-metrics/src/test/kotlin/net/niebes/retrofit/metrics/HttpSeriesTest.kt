package net.niebes.retrofit.metrics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HttpSeriesTest {
    @Test
    fun `fromHttpStatus returns INFORMATIONAL for 1xx`() {
        assertThat(HttpSeries.fromHttpStatus(100)).isEqualTo(HttpSeries.INFORMATIONAL)
        assertThat(HttpSeries.fromHttpStatus(199)).isEqualTo(HttpSeries.INFORMATIONAL)
    }

    @Test
    fun `fromHttpStatus returns SUCCESSFUL for 2xx`() {
        assertThat(HttpSeries.fromHttpStatus(200)).isEqualTo(HttpSeries.SUCCESSFUL)
        assertThat(HttpSeries.fromHttpStatus(204)).isEqualTo(HttpSeries.SUCCESSFUL)
        assertThat(HttpSeries.fromHttpStatus(299)).isEqualTo(HttpSeries.SUCCESSFUL)
    }

    @Test
    fun `fromHttpStatus returns REDIRECTION for 3xx`() {
        assertThat(HttpSeries.fromHttpStatus(301)).isEqualTo(HttpSeries.REDIRECTION)
        assertThat(HttpSeries.fromHttpStatus(399)).isEqualTo(HttpSeries.REDIRECTION)
    }

    @Test
    fun `fromHttpStatus returns CLIENT_ERROR for 4xx`() {
        assertThat(HttpSeries.fromHttpStatus(400)).isEqualTo(HttpSeries.CLIENT_ERROR)
        assertThat(HttpSeries.fromHttpStatus(404)).isEqualTo(HttpSeries.CLIENT_ERROR)
        assertThat(HttpSeries.fromHttpStatus(499)).isEqualTo(HttpSeries.CLIENT_ERROR)
    }

    @Test
    fun `fromHttpStatus returns SERVER_ERROR for 5xx`() {
        assertThat(HttpSeries.fromHttpStatus(500)).isEqualTo(HttpSeries.SERVER_ERROR)
        assertThat(HttpSeries.fromHttpStatus(503)).isEqualTo(HttpSeries.SERVER_ERROR)
        assertThat(HttpSeries.fromHttpStatus(599)).isEqualTo(HttpSeries.SERVER_ERROR)
    }

    @Test
    fun `fromHttpStatus returns null for unknown status codes`() {
        assertThat(HttpSeries.fromHttpStatus(0)).isNull()
        assertThat(HttpSeries.fromHttpStatus(99)).isNull()
        assertThat(HttpSeries.fromHttpStatus(600)).isNull()
        assertThat(HttpSeries.fromHttpStatus(999)).isNull()
        assertThat(HttpSeries.fromHttpStatus(-1)).isNull()
    }

    @Test
    fun `all standard HTTP series are covered`() {
        assertThat(HttpSeries.entries).hasSize(5)
    }
}
