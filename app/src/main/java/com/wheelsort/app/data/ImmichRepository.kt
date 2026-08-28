package com.wheelsort.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ImmichSettings(
    val serverUrl: String,
    val apiKey: String,
    val cfAccessClientId: String = "",
    val cfAccessClientSecret: String = ""
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && apiKey.isNotBlank()
    /** True only when BOTH Cloudflare fields are filled in - a half-filled pair isn't usable. */
    val usesCloudflareAccess: Boolean get() = cfAccessClientId.isNotBlank() && cfAccessClientSecret.isNotBlank()
}

/** id -> whether that photo already exists on the Immich server. */
typealias BackupStatusMap = Map<String, Boolean>

/**
 * Talks to a user-provided, self-hosted Immich server to check which photos are already backed
 * up - never uploads anything, only asks "do you already have this?" via SHA1 checksums, which
 * is exactly what Immich's own clients use before uploading to avoid duplicates.
 */
class ImmichRepository(context: Context) {

    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "immich_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun loadSettings(): ImmichSettings = ImmichSettings(
        serverUrl = prefs.getString(KEY_URL, "") ?: "",
        apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        cfAccessClientId = prefs.getString(KEY_CF_CLIENT_ID, "") ?: "",
        cfAccessClientSecret = prefs.getString(KEY_CF_CLIENT_SECRET, "") ?: ""
    )

    fun saveSettings(serverUrl: String, apiKey: String, cfAccessClientId: String = "", cfAccessClientSecret: String = "") {
        prefs.edit()
            .putString(KEY_URL, serverUrl.trim().trimEnd('/'))
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_CF_CLIENT_ID, cfAccessClientId.trim())
            .putString(KEY_CF_CLIENT_SECRET, cfAccessClientSecret.trim())
            .apply()
    }

    /** Cloudflare Access checks these BEFORE the request ever reaches the origin server - if
     *  present, they need to go on every single request, same as the Immich API key does. */
    private fun Request.Builder.addCloudflareAccessHeaders(settings: ImmichSettings): Request.Builder {
        if (settings.usesCloudflareAccess) {
            addHeader("CF-Access-Client-Id", settings.cfAccessClientId)
            addHeader("CF-Access-Client-Secret", settings.cfAccessClientSecret)
        }
        return this
    }

    private fun baseUrl(settings: ImmichSettings) = settings.serverUrl.trimEnd('/')

    /** Simple auth+reachability check via a well-established, stable Immich endpoint. */
    fun testConnection(settings: ImmichSettings): Result<Unit> {
        return try {
            val request = Request.Builder()
                .url("${baseUrl(settings)}/api/users/me")
                .addHeader("x-api-key", settings.apiKey)
                .addHeader("Accept", "application/json")
                .addCloudflareAccessHeaders(settings)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Server responded ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * [items] is (localId, sha1HexChecksum) pairs. Immich's bulk-upload-check endpoint - the same
     * one its own apps use before uploading - tells us per-item whether it already exists on the
     * server ("reject" + an assetId means duplicate = already backed up).
     */
    fun checkBackupStatus(settings: ImmichSettings, items: List<Pair<String, String>>): Result<BackupStatusMap> {
        if (items.isEmpty()) return Result.success(emptyMap())
        return try {
            val assetsArray = JSONArray()
            for ((id, checksum) in items) {
                assetsArray.put(JSONObject().apply {
                    put("id", id)
                    put("checksum", checksum)
                })
            }
            val body = JSONObject().apply { put("assets", assetsArray) }
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${baseUrl(settings)}/api/assets/bulk-upload-check")
                .addHeader("x-api-key", settings.apiKey)
                .addHeader("Accept", "application/json")
                .addCloudflareAccessHeaders(settings)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Server responded ${response.code}"))
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val results = json.optJSONArray("results") ?: JSONArray()
                val map = mutableMapOf<String, Boolean>()
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val id = item.optString("id")
                    val action = item.optString("action")
                    map[id] = action == "reject" // rejected as duplicate = already on the server
                }
                Result.success(map)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val KEY_URL = "server_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_CF_CLIENT_ID = "cf_access_client_id"
        const val KEY_CF_CLIENT_SECRET = "cf_access_client_secret"
    }
}

object ChecksumUtils {
    /** Streaming SHA1 hex digest of a MediaStore file - never loads the whole photo into memory at once. */
    fun sha1Hex(context: Context, uri: android.net.Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
