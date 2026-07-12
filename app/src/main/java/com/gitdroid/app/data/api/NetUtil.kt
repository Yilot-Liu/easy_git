package com.gitdroid.app.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.gitdroid.app.R
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object NetUtil {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    fun friendlyMessage(context: Context, throwable: Throwable?): String {
        if (throwable == null) return context.getString(R.string.network_error)
        val msg = throwable.message.orEmpty()
        return when {
            throwable is UnknownHostException || msg.contains("Unable to resolve host", true) ||
                msg.contains("unknown host", true) ->
                context.getString(R.string.error_no_network)
            throwable is SocketTimeoutException || msg.contains("timeout", true) ||
                msg.contains("timed out", true) ->
                context.getString(R.string.error_timeout)
            throwable is SSLException || msg.contains("ssl", true) ->
                context.getString(R.string.error_ssl)
            msg.contains("Failed to connect", true) || msg.contains("Connection refused", true) ||
                msg.contains("ECONNREFUSED", true) ->
                context.getString(R.string.error_connect_failed)
            msg.contains("401", true) || msg.contains("Unauthorized", true) ->
                context.getString(R.string.error_unauthorized)
            msg.contains("403", true) || msg.contains("Forbidden", true) ->
                context.getString(R.string.error_forbidden)
            msg.contains("404", true) || msg.contains("Not Found", true) ->
                context.getString(R.string.error_not_found)
            msg.contains("429", true) || msg.contains("rate limit", true) ->
                context.getString(R.string.error_rate_limit)
            msg.contains("5", true) && msg.contains("Server", true) ->
                context.getString(R.string.error_server)
            msg.isNotBlank() -> msg
            else -> context.getString(R.string.network_error)
        }
    }
}