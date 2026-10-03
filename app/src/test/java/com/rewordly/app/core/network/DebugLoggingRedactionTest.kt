package com.rewordly.app.core.network

import com.rewordly.app.core.network.ai.AiRequestFactory
import com.rewordly.app.domain.model.AiApiStyle
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The debug logger is the one place a provider key could escape into logcat, so it is checked against the
 * header every vendor actually uses rather than against `Authorization` alone.
 *
 * `AiRequestFactory` is the source of truth for those headers, so the test drives it instead of hard-coding
 * names: a fourth vendor would be covered automatically.
 */
class DebugLoggingRedactionTest {
    /** Every request the app can make, one per vendor, all carrying the same secret. */
    private fun requests(): List<Request> = AiApiStyle.entries.map { style ->
        Request.Builder()
            .url("https://api.example.invalid/v1/thing")
            .apply { AiRequestFactory.headers(style, KEY).forEach { (name, value) -> header(name, value) } }
            .build()
    }

    /** Runs the requests through the real logger and returns everything it would have written. */
    private fun loggedLines(level: HttpLoggingInterceptor.Level): String {
        val lines = mutableListOf<String>()
        val logger = object : HttpLoggingInterceptor.Logger {
            override fun log(message: String) {
                lines += message
            }
        }
        val logging = DebugInterceptors.logging(logger).apply { this.level = level }
        val terminal = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("scripted")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(logging).addInterceptor(terminal).build()
        requests().forEach { client.newCall(it).execute().close() }
        return lines.joinToString("\n")
    }

    @Test
    fun atBasicLevel_noHeaderIsPrintedAtAll() {
        val log = loggedLines(HttpLoggingInterceptor.Level.BASIC)
        assertFalse("a header reached the log", log.contains(KEY))
        assertFalse("a header name reached the log", log.contains("x-goog-api-key"))
    }

    @Test
    fun atHeadersLevel_theBearerHeaderIsRedactedButTheOthersAreNot() {
        val log = loggedLines(HttpLoggingInterceptor.Level.HEADERS)
        // Control: the redaction mechanism itself works for the header it is applied to.
        assertTrue("Authorization should still be present as a name:\n$log", log.contains("Authorization"))
        assertFalse("the bearer token leaked:\n$log", log.contains("Bearer $KEY"))
        // The other two vendors put the same secret in a header that is not on the redaction list.
        assertFalse("a provider key reached the log:\n$log", log.contains(KEY))
    }

    @Test
    fun atBodyLevel_noVendorKeySurvives() {
        val log = loggedLines(HttpLoggingInterceptor.Level.BODY)
        assertFalse("a provider key reached the log:\n$log", log.contains(KEY))
    }

    private companion object {
        const val KEY = "sk-do-not-log-this"
    }
}
