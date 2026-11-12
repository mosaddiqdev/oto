package com.oto.stats.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Artist photos, fetched on demand for the artists a screen is actually showing.
 *
 * Source: Deezer's public search (no account, no key). Only the artist name is sent. The photo is shrunk and kept
 * in [ArtStore] under `artist:<id>`. A failed lookup is remembered for two weeks. Requests run one at a time with a
 * short gap, only when the switch in Setup is on and the network is not metered.
 */
class ArtistPhotos(
    private val context: Context,
    private val art: ArtStore,
    private val prefs: TrackingPrefs,
) {
    private val gate = Mutex()

    fun keyFor(artistId: Long) = "artist:$artistId"

    /** True if a photo is now stored for this artist (already there, or just fetched). */
    suspend fun ensure(artistId: Long, name: String): Boolean {
        val key = keyFor(artistId)
        if (art.hasFile(key)) return true
        if (!prefs.fetchArt.value || art.recentlyMissed(key) || !onUnmeteredNetwork()) return false
        return gate.withLock {
            if (art.hasFile(key)) return@withLock true
            val ok = withContext(Dispatchers.IO) { runCatching { fetch(key, name) }.getOrDefault(null) }
            when (ok) {
                true -> true
                false -> { art.markMissed(key); false } // answered, nothing usable
                null -> false // network trouble: try again next time
            }.also { delay(250) } // Deezer allows about 50 requests per 5 seconds; stay far under it
        }
    }

    /** true = saved, false = no good match, null = could not reach the service. */
    private fun fetch(key: String, name: String): Boolean? {
        val wanted = normalize(name)
        if (wanted.isEmpty()) return false
        val body = get("https://api.deezer.com/search/artist?limit=5&q=" + URLEncoder.encode(name, "UTF-8")) ?: return null
        val data = JSONObject(body).optJSONArray("data") ?: return false
        var best: JSONObject? = null
        for (i in 0 until data.length()) {
            val a = data.getJSONObject(i)
            if (normalize(a.optString("name")) != wanted) continue
            val pic = a.optString("picture_xl").ifEmpty { a.optString("picture_big") }
            if (pic.isEmpty() || "/artist//" in pic) continue // Deezer's placeholder for artists with no photo
            if (best == null || a.optInt("nb_fan") > best.optInt("nb_fan")) best = a
        }
        val url = best?.let { it.optString("picture_xl").ifEmpty { it.optString("picture_big") } } ?: return false
        val bytes = getBytes(url) ?: return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        art.saveIfMissing(key, bmp)
        return art.hasFile(key)
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Oto/1.0 (personal listening stats)")
        }

    private fun get(url: String): String? = runCatching {
        val c = open(url)
        try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
    }.getOrNull()

    private fun getBytes(url: String): ByteArray? = runCatching {
        val c = open(url)
        try { if (c.responseCode == 200) c.inputStream.readBytes() else null } finally { c.disconnect() }
    }.getOrNull()

    private fun onUnmeteredNetwork(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.activeNetwork != null && !cm.isActiveNetworkMetered
    }.getOrDefault(false)

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().filter { it.isLetterOrDigit() }
}
