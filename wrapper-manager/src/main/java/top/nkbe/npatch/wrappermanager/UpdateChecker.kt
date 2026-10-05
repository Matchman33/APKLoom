package top.nkbe.npatch.wrappermanager

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal const val PROJECT_URL = "https://github.com/Matchman33/APKLoom"
internal const val RELEASES_URL = "$PROJECT_URL/releases"
internal const val LATEST_RELEASE_API = "https://api.github.com/repos/Matchman33/APKLoom/releases/latest"

internal data class AppVersion(private val parts: List<Long>, private val preview: Boolean) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (index in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = (parts.getOrElse(index) { 0 }).compareTo(other.parts.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return other.preview.compareTo(preview)
    }

    companion object {
        fun parse(value: String): AppVersion? {
            if (value.length > 80) return null
            val match = Regex("^v?(\\d+(?:\\.\\d+){1,3})(?:-([A-Za-z0-9.-]+))?(?:\\+[A-Za-z0-9.-]+)?$")
                .matchEntire(value) ?: return null
            val parts = match.groupValues[1].split('.').map { it.toLongOrNull() ?: return null }
            return AppVersion(parts, match.groupValues[2].isNotEmpty())
        }
    }
}

internal data class ReleaseInfo(val version: String, val downloadUrl: String, val pageUrl: String)
internal enum class UpdateFailure { NETWORK, RATE_LIMIT, NO_RELEASE, SERVICE, INVALID_RELEASE }
internal class UpdateCheckException(val failure: UpdateFailure) : IOException(failure.name)

internal class UpdateChecker(private val endpoint: URL = URL(LATEST_RELEASE_API)) {
    fun latest(): ReleaseInfo {
        var connection: HttpURLConnection? = null
        try {
            connection = endpoint.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "APK-Loom/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Cache-Control", "no-cache")
            when (connection.responseCode) {
                200 -> Unit
                429 -> throw UpdateCheckException(UpdateFailure.RATE_LIMIT)
                403 -> throw UpdateCheckException(if (connection.getHeaderField("X-RateLimit-Remaining") == "0")
                    UpdateFailure.RATE_LIMIT else UpdateFailure.SERVICE)
                404 -> throw UpdateCheckException(UpdateFailure.NO_RELEASE)
                else -> throw UpdateCheckException(UpdateFailure.SERVICE)
            }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 1024 * 1024) throw UpdateCheckException(UpdateFailure.INVALID_RELEASE)
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            return parseRelease(body)
        } catch (error: UpdateCheckException) {
            throw error
        } catch (_: IOException) {
            throw UpdateCheckException(UpdateFailure.NETWORK)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        fun parseRelease(body: String): ReleaseInfo {
            try {
                val root = JsonParser.parseString(body).asJsonObject
                require(!root.get("draft").asBoolean && !root.get("prerelease").asBoolean)
                val tag = root.string("tag_name")
                require(tag.matches(Regex("v?\\d+(?:\\.\\d+){1,3}")) && AppVersion.parse(tag) != null)
                val version = tag.removePrefix("v")
                val page = "$PROJECT_URL/releases/tag/$tag"
                require(root.string("html_url") == page)
                val filename = "APK-Loom-$version.apk"
                val asset = root.getAsJsonArray("assets").firstOrNull {
                    it.isJsonObject && it.asJsonObject.string("name") == filename
                }?.asJsonObject ?: throw IllegalArgumentException("No manager APK")
                val download = "$PROJECT_URL/releases/download/$tag/$filename"
                require(asset.string("browser_download_url") == download)
                return ReleaseInfo(version, download, page)
            } catch (_: RuntimeException) {
                throw UpdateCheckException(UpdateFailure.INVALID_RELEASE)
            }
        }

        private fun JsonObject.string(name: String): String {
            val value = get(name)
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
            return value.asString
        }
    }
}
