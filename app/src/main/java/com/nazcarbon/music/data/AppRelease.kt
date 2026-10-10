package com.nazcarbon.music.data

import org.json.JSONObject
import java.net.URI

data class AppRelease(val tag: String, val versionCode: Long, val url: String, val bytes: Long, val sha256: String) {
    fun verifyDownload(file: java.io.File) {
        require(file.length() == bytes) { "The update download is incomplete. Download it again." }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        val checksum = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        require(checksum.equals(sha256, true)) { "The update checksum doesn't match. Download it again." }
    }

    fun encode(): String = JSONObject().put("tag", tag).put("version", versionCode)
        .put("url", url).put("bytes", bytes).put("sha256", sha256).toString()

    companion object {
        private val tagPattern = Regex("v3\\.0\\.(\\d+)")
        fun parse(json: JSONObject): AppRelease? {
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
            val tag = json.optString("tag_name")
            val build = tagPattern.matchEntire(tag)?.groupValues?.get(1)?.toLongOrNull() ?: return null
            if (build !in 1..(Int.MAX_VALUE - 30000).toLong()) return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name") != "app-release.apk" || asset.optString("state") != "uploaded") continue
                if (!asset.optString("digest").startsWith("sha256:")) return null
                return validate(AppRelease(tag, 30000 + build, asset.optString("browser_download_url"),
                    asset.optLong("size"), asset.optString("digest").removePrefix("sha256:")))
            }
            return null
        }
        fun decode(value: String): AppRelease? = runCatching {
            val json = JSONObject(value)
            validate(AppRelease(json.getString("tag"), json.getLong("version"), json.getString("url"),
                json.getLong("bytes"), json.getString("sha256")))
        }.getOrNull()
        private fun validate(release: AppRelease): AppRelease? {
            val build = tagPattern.matchEntire(release.tag)?.groupValues?.get(1)?.toLongOrNull() ?: return null
            val uri = runCatching { URI(release.url) }.getOrNull() ?: return null
            return release.takeIf {
                build in 1..(Int.MAX_VALUE - 30000).toLong() && it.versionCode == 30000 + build &&
                    uri.scheme == "https" && uri.host == "github.com" && uri.rawAuthority == "github.com" &&
                    uri.rawPath == "/brimzy/pipes/releases/download/${it.tag}/app-release.apk" &&
                    uri.rawQuery == null && uri.rawFragment == null && it.bytes in 1..200_000_000 &&
                    Regex("[a-fA-F0-9]{64}").matches(it.sha256)
            }
        }
    }
}
