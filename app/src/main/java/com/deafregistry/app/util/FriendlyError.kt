package com.deafregistry.app.util

import android.util.Log
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps a caught Throwable to a short, non-technical message safe to show directly to end users.
 * The real exception is still logged (Logcat, not the UI) for debugging - this is only what
 * appears on screen. Centralizing this in one place keeps the tone consistent everywhere it's
 * used, instead of every screen showing a raw "${e.message}" (which for a network failure or a
 * server bug can be things like "Unable to resolve host" or a stack-trace fragment).
 */
fun friendlyMessage(throwable: Throwable, tag: String = "DeafRegistry"): String {
    Log.e(tag, "Operation failed", throwable)
    return when (throwable) {
        is UnknownHostException, is SocketTimeoutException ->
            "Unable to connect. Please check your internet connection and try again."
        is HttpException -> when (throwable.code()) {
            401 -> "Your session has expired. Please log in again."
            403 -> "You don't have permission to do that."
            404 -> "The requested information could not be found."
            in 500..599 -> "Something went wrong on our end. Please try again in a moment."
            // 4xx other than the above almost always carries a message this app wrote itself
            // server-side (e.g. "Username must be at least 4 characters") - already friendly and
            // specific, worth showing rather than replacing with a vaguer generic line.
            else -> serverMessage(throwable) ?: "Unable to save your information. Please check the details you entered and try again."
        }
        is IOException -> "Unable to connect. Please check your internet connection and try again."
        else -> "Something went wrong. Please try again. If the problem continues, contact the administrator."
    }
}

private fun serverMessage(e: HttpException): String? = runCatching {
    val body = e.response()?.errorBody()?.string() ?: return null
    val parsed = Gson().fromJson(body, Map::class.java)
    (parsed?.get("message") as? String)?.takeIf { it.isNotBlank() && it.length < 200 }
}.getOrNull()
