package com.ultimateiptv.sports.core.network

import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.core.parser.M3uFormatException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class HttpStatusException(val code: Int, message: String = "HTTP $code") : IOException(message)

class AuthException(message: String) : IOException(message)

/** Maps technical failures to a user-facing string resource. */
object ErrorMapper {
    fun messageRes(t: Throwable): Int = when (t) {
        is UnknownHostException -> R.string.error_host
        is SocketTimeoutException -> R.string.error_timeout
        is ConnectException -> R.string.error_connect
        is SSLException -> R.string.error_ssl
        is AuthException -> R.string.error_auth
        is HttpStatusException -> when (t.code) {
            401, 403 -> R.string.error_auth
            404 -> R.string.error_not_found
            429 -> R.string.error_rate_limit
            in 500..599 -> R.string.error_server
            else -> R.string.error_http
        }
        is M3uFormatException -> R.string.error_format
        is FileNotFoundException -> R.string.error_not_found
        is SecurityException -> R.string.error_permission
        is kotlinx.serialization.SerializationException, is IllegalArgumentException -> R.string.error_format
        else -> R.string.error_generic
    }

    /** Short technical detail appended to messages (never contains credentials). */
    fun detail(t: Throwable): String = when (t) {
        is HttpStatusException -> "HTTP ${t.code}"
        else -> t.javaClass.simpleName
    }
}
