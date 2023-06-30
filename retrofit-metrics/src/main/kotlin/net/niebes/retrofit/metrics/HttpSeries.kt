package net.niebes.retrofit.metrics

enum class HttpSeries(private val value: Int) {
    INFORMATIONAL(1),
    SUCCESSFUL(2),
    REDIRECTION(3),
    CLIENT_ERROR(4),
    SERVER_ERROR(5),
    ;

    companion object {
        fun fromHttpStatus(status: Int): HttpSeries? =
            values().firstOrNull { it.value == status / 100 }
    }
}
