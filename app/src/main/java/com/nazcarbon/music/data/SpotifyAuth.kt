package com.nazcarbon.music.data

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Spotify Authorization Code with PKCE for the native app. */
class SpotifyAuth(private val activity: Activity) {
    private companion object {
        const val CLIENT_ID = "f087a62b4bc9468a9aab80ae0dc92c3c"
        const val REDIRECT_URI = "http://127.0.0.1:8765/callback"
        const val KEY_ALIAS = "pipes.spotify.refresh"
        const val TOKEN_FILE = "spotify_refresh_token"
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        const val AUTH_URL = "https://accounts.spotify.com/authorize"
    }

    suspend fun getAccessToken(): String {
        val refreshToken = readRefreshToken()
        if (refreshToken != null) {
            try {
                return refresh(refreshToken)
            } catch (failure: Exception) {
                if (failure.message?.contains("invalid_grant", ignoreCase = true) != true) throw failure
                clearRefreshToken()
            }
        }
        return authorize()
    }

    private suspend fun authorize(): String = coroutineScope {
        val verifier = randomText(64)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val state = randomText(32)
        val server = withContext(Dispatchers.IO) {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8765))
                soTimeout = 180_000
            }
        }
        val callback = async(Dispatchers.IO) { receiveCallback(server, state) }
        try {
            val authUri = Uri.parse(AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative")
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .build()
            activity.startActivity(Intent(Intent.ACTION_VIEW, authUri))
            val code = withTimeout(180_000) { callback.await() }
            exchangeCode(code, verifier)
        } finally {
            server.close()
            callback.cancel()
        }
    }

    private fun receiveCallback(server: ServerSocket, expectedState: String): String {
        server.use { listener ->
            listener.accept().use { socket ->
                socket.soTimeout = 15_000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine().orEmpty()
                while (reader.readLine()?.isNotEmpty() == true) Unit
                val path = requestLine.split(' ').getOrNull(1).orEmpty()
                val query = Uri.parse("http://127.0.0.1$path").queryParameterNames
                    .associateWith { Uri.parse("http://127.0.0.1$path").getQueryParameter(it).orEmpty() }
                val html = "<html><body>You can return to Pipes now.</body></html>"
                val body = html.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    write(body)
                    flush()
                }
                require(path.substringBefore('?') == "/callback") { "Spotify returned to an unexpected address." }
                require(query["state"] == expectedState) { "Spotify sign-in state did not match. Please try again." }
                query["error"]?.let { throw IllegalStateException("Spotify sign-in was cancelled: $it") }
                return query["code"]?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Spotify did not return a sign-in code.")
            }
        }
    }

    private suspend fun exchangeCode(code: String, verifier: String): String = withContext(Dispatchers.IO) {
        val response = postToken(mapOf(
            "client_id" to CLIENT_ID,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to REDIRECT_URI,
            "code_verifier" to verifier,
        ))
        response.optString("refresh_token").takeIf { it.isNotBlank() }?.let(::saveRefreshToken)
        response.optString("access_token").takeIf { it.isNotBlank() }
            ?: error("Spotify sign-in did not return an access token.")
    }

    private suspend fun refresh(refreshToken: String): String = withContext(Dispatchers.IO) {
        val response = postToken(mapOf(
            "client_id" to CLIENT_ID,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        ))
        response.optString("refresh_token").takeIf { it.isNotBlank() }?.let(::saveRefreshToken)
        response.optString("access_token").takeIf { it.isNotBlank() }
            ?: error("Spotify did not refresh the access token.")
    }

    private fun postToken(parameters: Map<String, String>): JSONObject {
        val connection = (URL(TOKEN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        val form = parameters.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        return try {
            connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(code in 200..299) {
                val message = runCatching { JSONObject(body).optString("error_description").ifBlank { JSONObject(body).optString("error") } }.getOrNull()
                "Spotify sign-in failed${message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}. Check the app redirect URI and try again."
            }
            JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun randomText(length: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val bytes = ByteArray(length).also(SecureRandom()::nextBytes)
        return bytes.map { alphabet[(it.toInt() and 0xff) % alphabet.length] }.joinToString("")
    }

    private fun readRefreshToken(): String? = runCatching {
        val encoded = File(activity.noBackupFilesDir, TOKEN_FILE).readText()
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    private fun saveRefreshToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val bytes = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        File(activity.noBackupFilesDir, TOKEN_FILE).writeText(Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    private fun clearRefreshToken() {
        File(activity.noBackupFilesDir, TOKEN_FILE).delete()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }
}
