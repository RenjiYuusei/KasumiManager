package com.aliucord.manager.manager

import android.util.Log
import dev.shiggy.manager.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLDecoder

data class ThemeInfo(
    val file: File,
    val name: String,
    val author: String? = null,
    val version: String? = null,
    val description: String? = null,
    val sizeBytes: Long = 0,
    val lastModified: Long = 0,
)

class ThemeManager(
    private val pathManager: PathManager,
    private val http: HttpClient,
) {
    companion object {
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    /**
     * Get all installed themes from Shiggy/themes directory
     */
    fun getInstalledThemes(): List<ThemeInfo> {
        val themesDir = pathManager.themesDir
        if (!themesDir.exists() || !themesDir.isDirectory) {
            themesDir.mkdirs()
            return emptyList()
        }

        val files = themesDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".css", ignoreCase = true) || file.name.endsWith(".json", ignoreCase = true))
        } ?: return emptyList()

        return files.map { file -> parseThemeFile(file) }
            .sortedByDescending { it.lastModified }
    }

    /**
     * Delete an installed theme
     */
    fun deleteTheme(theme: ThemeInfo): Boolean {
        return try {
            if (theme.file.exists()) {
                theme.file.delete()
            } else {
                false
            }
        } catch (t: Throwable) {
            Log.e(BuildConfig.TAG, "Failed to delete theme: ${theme.file.name}", t)
            false
        }
    }

    /**
     * Normalize various URL inputs (BetterDiscord ID, BetterDiscord theme page, GitHub blob, etc.)
     */
    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()

        // Pure numeric ID -> BetterDiscord download URL
        if (trimmed.all { it.isDigit() }) {
            return "https://betterdiscord.app/download?id=$trimmed"
        }

        // BetterDiscord theme page -> download endpoint
        // e.g. https://betterdiscord.app/theme?id=174 -> https://betterdiscord.app/download?id=174
        if (trimmed.contains("betterdiscord.app/theme", ignoreCase = true)) {
            val idMatch = Regex("[?&]id=(\\d+)").find(trimmed)
            if (idMatch != null) {
                return "https://betterdiscord.app/download?id=${idMatch.groupValues[1]}"
            }
        }

        // GitHub blob URL -> raw URL
        // e.g. https://github.com/user/repo/blob/branch/theme.css -> https://raw.githubusercontent.com/user/repo/branch/theme.css
        if (trimmed.contains("github.com", ignoreCase = true) && trimmed.contains("/blob/", ignoreCase = true)) {
            return trimmed.replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/")
        }

        return trimmed
    }

    /**
     * Download theme from URL, parse its content, and save to Shiggy/themes
     */
    suspend fun downloadTheme(inputUrl: String): Result<ThemeInfo> = withContext(Dispatchers.IO) {
        val targetUrl = normalizeUrl(inputUrl)
        if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid URL: $inputUrl"))
        }

        try {
            val response: HttpResponse = http.get(targetUrl) {
                // Use a standard browser User-Agent to prevent Cloudflare 403 blocks
                header(HttpHeaders.UserAgent, BROWSER_USER_AGENT)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml,text/css,*/*;q=0.8")
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
                header(HttpHeaders.CacheControl, "no-cache")
            }

            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    IllegalStateException("HTTP ${response.status.value}: ${response.status.description}")
                )
            }

            val bodyText = response.bodyAsText()
            if (bodyText.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Downloaded theme content is empty"))
            }

            // Extract metadata
            val metadata = extractMetadata(bodyText, response, targetUrl)
            val isJson = isJsonContent(bodyText)

            val safeName = sanitizeFileName(metadata.name)
            val fileName = when {
                safeName.endsWith(".theme.css", ignoreCase = true) -> safeName
                safeName.endsWith(".css", ignoreCase = true) -> safeName.replace(Regex("\\.css$", RegexOption.IGNORE_CASE), ".theme.css")
                safeName.endsWith(".json", ignoreCase = true) -> safeName
                isJson -> "$safeName.json"
                else -> "$safeName.theme.css"
            }

            val themesDir = pathManager.themesDir
            themesDir.mkdirs()

            val targetFile = themesDir.resolve(fileName)
            targetFile.writeText(bodyText)

            val themeInfo = ThemeInfo(
                file = targetFile,
                name = metadata.name,
                author = metadata.author,
                version = metadata.version,
                description = metadata.description,
                sizeBytes = targetFile.length(),
                lastModified = targetFile.lastModified(),
            )

            Result.success(themeInfo)
        } catch (t: Throwable) {
            Log.e(BuildConfig.TAG, "Failed to download theme from $targetUrl", t)
            Result.failure(t)
        }
    }

    private fun isJsonContent(text: String): Boolean {
        val trimmed = text.trim()
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
    }

    private data class ParsedMetadata(
        val name: String,
        val author: String? = null,
        val version: String? = null,
        val description: String? = null,
    )

    private fun extractMetadata(content: String, response: HttpResponse, url: String): ParsedMetadata {
        // Try parsing BetterDiscord header metadata
        // /**
        //  * @name Dark Matter
        //  * @author Tropical#8908
        //  * @version 3.0.0
        //  * @description A cold theme
        //  */
        val nameRegex = Regex("@name\\s+([^\r\n*]+)", RegexOption.IGNORE_CASE)
        val authorRegex = Regex("@author\\s+([^\r\n*]+)", RegexOption.IGNORE_CASE)
        val versionRegex = Regex("@version\\s+([^\r\n*]+)", RegexOption.IGNORE_CASE)
        val descRegex = Regex("@description\\s+([^\r\n*]+)", RegexOption.IGNORE_CASE)

        val nameMatch = nameRegex.find(content)?.groupValues?.get(1)?.trim()
        val authorMatch = authorRegex.find(content)?.groupValues?.get(1)?.trim()
        val versionMatch = versionRegex.find(content)?.groupValues?.get(1)?.trim()
        val descMatch = descRegex.find(content)?.groupValues?.get(1)?.trim()

        if (!nameMatch.isNullOrEmpty()) {
            return ParsedMetadata(
                name = nameMatch,
                author = authorMatch,
                version = versionMatch,
                description = descMatch,
            )
        }

        // Try JSON parsing
        if (isJsonContent(content)) {
            try {
                val json = JSONObject(content)
                val jsonName = json.optString("name").takeIf { it.isNotEmpty() }
                val jsonAuthor = json.optString("author").takeIf { it.isNotEmpty() }
                val jsonVersion = json.optString("version").takeIf { it.isNotEmpty() }
                val jsonDesc = json.optString("description").takeIf { it.isNotEmpty() }

                if (jsonName != null) {
                    return ParsedMetadata(
                        name = jsonName,
                        author = jsonAuthor,
                        version = jsonVersion,
                        description = jsonDesc,
                    )
                }
            } catch (_: Throwable) {}
        }

        // Try Content-Disposition header
        val contentDisposition = response.headers[HttpHeaders.ContentDisposition]
        if (!contentDisposition.isNullOrEmpty()) {
            val filenameMatch = Regex("filename[*]?=['\"]?(?:UTF-8'')?([^'\";\\r\\n]+)['\"]?", RegexOption.IGNORE_CASE)
                .find(contentDisposition)
            if (filenameMatch != null) {
                val filename = URLDecoder.decode(filenameMatch.groupValues[1].trim(), "UTF-8")
                val cleanName = filename.replace(Regex("\\.(theme\\.css|css|json)$", RegexOption.IGNORE_CASE), "")
                return ParsedMetadata(name = cleanName)
            }
        }

        // Fallback to URL path / ID
        val fallbackName = try {
            val uri = URI(url)
            val path = uri.path
            val query = uri.query
            if (query != null && query.contains("id=")) {
                val idMatch = Regex("id=(\\d+)").find(query)
                if (idMatch != null) {
                    "Theme_${idMatch.groupValues[1]}"
                } else {
                    path.substringAfterLast('/').takeIf { it.isNotEmpty() } ?: "CustomTheme"
                }
            } else {
                path.substringAfterLast('/').takeIf { it.isNotEmpty() } ?: "CustomTheme"
            }
        } catch (_: Throwable) {
            "CustomTheme_${System.currentTimeMillis()}"
        }

        val cleanFallback = fallbackName.replace(Regex("\\.(theme\\.css|css|json)$", RegexOption.IGNORE_CASE), "")
        return ParsedMetadata(name = cleanFallback)
    }

    private fun parseThemeFile(file: File): ThemeInfo {
        return try {
            val headerChunk = file.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                val read = reader.read(buffer)
                if (read > 0) String(buffer, 0, read) else ""
            }

            val isJson = file.name.endsWith(".json", ignoreCase = true) || isJsonContent(headerChunk)

            var name = file.nameWithoutExtension.replace(".theme", "")
            var author: String? = null
            var version: String? = null
            var description: String? = null

            if (isJson) {
                try {
                    val json = JSONObject(file.readText())
                    name = json.optString("name", name)
                    author = json.optString("author").takeIf { it.isNotEmpty() }
                    version = json.optString("version").takeIf { it.isNotEmpty() }
                    description = json.optString("description").takeIf { it.isNotEmpty() }
                } catch (_: Throwable) {}
            } else {
                val nameRegex = Regex("@name\\s+([^\r\n*]+)", RegexOption.IGNORE_CASE)
                val authorRegex = Regex("@author\\s+([^\r\n*]+)", RegexOption.IGNORE_CASE)
                val versionRegex = Regex("@version\\s+([^\r\n*]+)", RegexOption.IGNORE_CASE)
                val descRegex = Regex("@description\\s+([^\r\n*]+)", RegexOption.IGNORE_CASE)

                name = nameRegex.find(headerChunk)?.groupValues?.get(1)?.trim() ?: name
                author = authorRegex.find(headerChunk)?.groupValues?.get(1)?.trim()
                version = versionRegex.find(headerChunk)?.groupValues?.get(1)?.trim()
                description = descRegex.find(headerChunk)?.groupValues?.get(1)?.trim()
            }

            ThemeInfo(
                file = file,
                name = name,
                author = author,
                version = version,
                description = description,
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
            )
        } catch (t: Throwable) {
            Log.e(BuildConfig.TAG, "Error parsing theme file ${file.name}", t)
            ThemeInfo(
                file = file,
                name = file.name,
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
            )
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }
}
