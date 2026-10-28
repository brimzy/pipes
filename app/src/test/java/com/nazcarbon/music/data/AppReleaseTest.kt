package com.nazcarbon.music.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppReleaseTest {
    private fun release(tag: String = "v3.0.25", url: String = "https://github.com/brimzy/pipes/releases/download/$tag/app-release.apk", digest: String = "sha256:" + "a".repeat(64)) = JSONObject()
        .put("tag_name", tag).put("draft", false).put("prerelease", false)
        .put("assets", org.json.JSONArray().put(JSONObject().put("name", "app-release.apk")
            .put("state", "uploaded").put("browser_download_url", url).put("size", 16000000).put("digest", digest)))

    @Test fun `release run number maps to installed Android version code`() {
        val update = AppRelease.parse(release())!!
        assertEquals(30025L, update.versionCode)
        assertTrue(update.versionCode > 30024)
        assertFalse(update.versionCode > 30025)
        assertEquals(update, AppRelease.decode(update.encode()))
    }
    @Test fun `version four releases map to the new version range`() {
        val update = AppRelease.parse(release(tag = "v4.0.25"))!!
        assertEquals(40025L, update.versionCode)
        assertEquals(update, AppRelease.decode(update.encode()))
        assertNull(AppRelease.decode(update.copy(versionCode = 30025).encode()))
    }
    @Test fun `version five upgrades existing installs and round trips`() {
        val old = AppRelease.parse(release(tag = "v4.0.33"))!!
        val update = AppRelease.parse(release(tag = "v5.0.34"))!!
        assertEquals(50034L, update.versionCode)
        assertTrue(update.versionCode > old.versionCode)
        assertEquals(update, AppRelease.decode(update.encode()))
        assertNull(AppRelease.decode(update.copy(versionCode = 40034).encode()))
        assertNull(AppRelease.parse(release(tag = "v6.0.34")))
    }
    @Test fun `version five point one upgrades and round trips`() {
        val update = AppRelease.parse(release(tag = "v5.1.35"))!!
        assertEquals(50135L, update.versionCode)
        assertTrue(update.versionCode > AppRelease.parse(release(tag = "v5.0.34"))!!.versionCode)
        assertEquals(update, AppRelease.decode(update.encode()))
        assertNull(AppRelease.decode(update.copy(versionCode = 50035).encode()))
    }
    @Test fun `version five rejects Android version code overflow`() {
        val maximum = Int.MAX_VALUE.toLong() - 50000
        assertEquals(Int.MAX_VALUE.toLong(), AppRelease.parse(release(tag = "v5.0.$maximum"))!!.versionCode)
        assertNull(AppRelease.parse(release(tag = "v5.0.${maximum + 1}")))
    }
    @Test fun `drafts prereleases and incomplete assets are ignored`() {
        assertNull(AppRelease.parse(release().put("draft", true)))
        assertNull(AppRelease.parse(release().put("prerelease", true)))
        assertNull(AppRelease.parse(release().put("assets", org.json.JSONArray())))
        assertNull(AppRelease.parse(release(digest = "")))
        assertNull(AppRelease.parse(release(digest = "sha256:bad")))
    }
    @Test fun `only the official repository HTTPS APK is accepted`() {
        for (url in listOf("http://github.com/brimzy/pipes/releases/download/v3.0.25/app-release.apk",
            "https://github.com.evil.test/brimzy/pipes/releases/download/v3.0.25/app-release.apk",
            "https://github.com/other/repo/releases/download/v3.0.25/app-release.apk",
            "https://github.com/brimzy/pipes/releases/download/v3.0.24/app-release.apk",
            "https://github.com/brimzy/pipes/releases/download/v3.0.25/app-release.apk?redirect=1")) {
            assertNull(url, AppRelease.parse(release(url = url)))
        }
    }
    @Test fun `malformed tags and saved data cannot become updates`() {
        assertNull(AppRelease.parse(release(tag = "v3.0.999999999999999999999")))
        assertNull(AppRelease.parse(release(tag = "latest")))
        assertNull(AppRelease.decode("invalid"))
        val update = AppRelease.parse(release())!!
        assertNull(AppRelease.decode(JSONObject(update.encode()).put("version", 99999).toString()))
    }
    @Test fun `download verification rejects truncation and same-size corruption`() {
        val file = java.io.File.createTempFile("pipes-update", ".apk")
        try {
            file.writeText("abc")
            val update = AppRelease.parse(release())!!.copy(bytes = 3,
                sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
            update.verifyDownload(file)
            file.writeText("ab")
            assertThrows(IllegalArgumentException::class.java) { update.verifyDownload(file) }
            file.writeText("abd")
            assertThrows(IllegalArgumentException::class.java) { update.verifyDownload(file) }
        } finally { file.delete() }
    }
}
