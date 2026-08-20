package com.aliucord.manager.manager

import android.util.Log
import com.aliucord.manager.network.services.HttpService
import dev.shiggy.manager.BuildConfig
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

data class ThemeInfo(
    val file: File,
    val name: String,
    val author: String,
    val version: String,
    val description: String,
    val backgroundUrl: String? = null,
    val primaryColor: String? = null,
)

class ThemeManager(
    private val pathManager: PathManager,
    private val httpService: HttpService,
) {
    val themesDir: File
        get() = pathManager.shiggyDir.resolve("themes").apply { mkdirs() }

    suspend fun getInstalledThemes(): List<ThemeInfo> = withContext(Dispatchers.IO) {
        val dir = themesDir
        if (!dir.exists()) return@withContext emptyList()

        val jsonFiles = dir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) } ?: emptyArray()

        jsonFiles.map { file ->
            parseThemeFile(file)
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun deleteTheme(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) file.delete() else true
        } catch (t: Throwable) {
            Log.e(BuildConfig.TAG, "Failed to delete theme file: ${file.name}", t)
            false
        }
    }

    suspend fun downloadAndInstallTheme(rawUrl: String): Result<ThemeInfo> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeThemeUrl(rawUrl.trim())
            Log.d(BuildConfig.TAG, "Downloading theme from: $normalizedUrl")

            val response = httpService.request<String> {
                url(normalizedUrl)
                header(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                )
            }

            val body = when (response) {
                is com.aliucord.manager.network.utils.ApiResponse.Success -> response.data
                is com.aliucord.manager.network.utils.ApiResponse.Error -> {
                    return@withContext Result.failure(Exception("HTTP Error: ${response.error.status}"))
                }
                is com.aliucord.manager.network.utils.ApiResponse.Failure -> {
                    return@withContext Result.failure(response.failure.throwable)
                }
            }

            if (body.isBlank()) {
                return@withContext Result.failure(Exception("Empty theme response"))
            }

            val (themeName, jsonContent) = if (isBetterDiscordCss(body)) {
                convertBetterDiscordCssToJson(body, normalizedUrl)
            } else {
                processJsonTheme(body, normalizedUrl)
            }

            val safeFileName = sanitizeFileName(themeName) + ".json"
            val targetFile = themesDir.resolve(safeFileName)
            targetFile.writeText(jsonContent)

            val info = parseThemeFile(targetFile)
            Result.success(info)
        } catch (t: Throwable) {
            Log.e(BuildConfig.TAG, "Failed to download and install theme", t)
            Result.failure(t)
        }
    }

    private fun normalizeThemeUrl(url: String): String {
        var cleanUrl = url
        // BetterDiscord links
        val bdDownloadMatch = Regex("""betterdiscord\.app/theme\?id=(\d+)""").find(cleanUrl)
        if (bdDownloadMatch != null) {
            val id = bdDownloadMatch.groupValues[1]
            return "https://betterdiscord.app/download?id=$id"
        }

        val bdThemePathMatch = Regex("""betterdiscord\.app/themes/(\d+)""").find(cleanUrl)
        if (bdThemePathMatch != null) {
            val id = bdThemePathMatch.groupValues[1]
            return "https://betterdiscord.app/download?id=$id"
        }

        // GitHub blob links -> raw
        if (cleanUrl.contains("github.com") && cleanUrl.contains("/blob/")) {
            cleanUrl = cleanUrl.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/")
        }

        return cleanUrl
    }

    private fun isBetterDiscordCss(content: String): Boolean {
        val trimmed = content.trimStart()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return false
        }
        return content.contains("@name") || content.contains(":root") || content.contains("--") || content.contains("{")
    }

    private fun convertBetterDiscordCssToJson(css: String, sourceUrl: String): Pair<String, String> {
        val nameRegex = Regex("""@name\s+([^\r\n*]+)""")
        val authorRegex = Regex("""@author\s+([^\r\n*]+)""")
        val versionRegex = Regex("""@version\s+([^\r\n*]+)""")
        val descriptionRegex = Regex("""@description\s+([^\r\n*]+)""")

        val name = nameRegex.find(css)?.groupValues?.get(1)?.trim()
            ?: extractFallbackNameFromUrl(sourceUrl)
            ?: "Custom_BD_Theme"
        val author = authorRegex.find(css)?.groupValues?.get(1)?.trim() ?: "BetterDiscord"
        val version = versionRegex.find(css)?.groupValues?.get(1)?.trim() ?: "1.0.0"
        val description = descriptionRegex.find(css)?.groupValues?.get(1)?.trim() ?: "Converted from BetterDiscord theme"

        // Extract background image url
        val bgImgRegex = Regex("""(?:--background-image|--app-bg|--bg-image|background-image)\s*:\s*url\(['"]?(https?://[^'")\s]+)['"]?\)""")
        val bgUrl = bgImgRegex.find(css)?.groupValues?.get(1)

        // Extract colors
        val mainColor = extractCssColor(css, arrayOf("--main-color", "--accentcolor", "--accent-color", "--brand-experiment", "--brand-color"))
        val hoverColor = extractCssColor(css, arrayOf("--hover-color", "--interactive-hover"))
        val bgPrimary = extractCssColor(css, arrayOf("--background-primary", "--bg-primary", "--background-dark"))
        val bgSecondary = extractCssColor(css, arrayOf("--background-secondary", "--bg-secondary"))
        val textNormal = extractCssColor(css, arrayOf("--text-normal", "--text-default", "--primary-text"))
        val textMuted = extractCssColor(css, arrayOf("--text-muted", "--secondary-text"))

        val jsonObject = buildJsonObject {
            putJsonObject("manifest") {
                put("name", name)
                put("author", author)
                put("version", version)
                put("description", description)
                put("source", sourceUrl)
            }

            if (!bgUrl.isNullOrBlank()) {
                putJsonObject("background") {
                    put("url", bgUrl)
                    put("alpha", 0.85)
                }
            }

            mainColor?.let {
                put("color_brand", it)
                put("color_brand_new", it)
                put("color_interactive_active", it)
            }
            hoverColor?.let {
                put("color_interactive_hover", it)
            }
            bgPrimary?.let {
                put("color_primary_dark", it)
                put("color_background_primary", it)
                put("color_surface", it)
            }
            bgSecondary?.let {
                put("color_background_secondary", it)
                put("color_background_tertiary", it)
            }
            textNormal?.let {
                put("color_text_normal", it)
            }
            textMuted?.let {
                put("color_text_muted", it)
            }
        }

        return Pair(name, jsonObject.toString())
    }

    private fun extractCssColor(css: String, varNames: Array<String>): String? {
        for (varName in varNames) {
            val regex = Regex("""$varName\s*:\s*([^;}\r\n]+)""")
            val match = regex.find(css)
            if (match != null) {
                val rawVal = match.groupValues[1].trim()
                val colorHex = parseCssColorToHex(rawVal)
                if (colorHex != null) return colorHex
            }
        }
        return null
    }

    private fun parseCssColorToHex(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("#")) {
            val hex = trimmed.substring(1).trim()
            if (hex.length == 3) {
                return "#" + hex.map { "$it$it" }.joinToString("")
            }
            if (hex.length == 6 || hex.length == 8) {
                return "#$hex"
            }
        }
        // RGB / RGBA
        val rgbaMatch = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)""").find(trimmed)
        if (rgbaMatch != null) {
            val r = rgbaMatch.groupValues[1].toIntOrNull() ?: 0
            val g = rgbaMatch.groupValues[2].toIntOrNull() ?: 0
            val b = rgbaMatch.groupValues[3].toIntOrNull() ?: 0
            val aStr = rgbaMatch.groupValues.getOrNull(4)
            if (!aStr.isNullOrBlank()) {
                val aFloat = aStr.toFloatOrNull() ?: 1f
                val aInt = (aFloat * 255).toInt().coerceIn(0, 255)
                return String.format("#%02x%02x%02x%02x", aInt, r, g, b)
            }
            return String.format("#%02x%02x%02x", r, g, b)
        }
        return null
    }

    private fun processJsonTheme(content: String, sourceUrl: String): Pair<String, String> {
        val jsonElement = Json.parseToJsonElement(content)
        val jsonObject = jsonElement.jsonObject

        val manifest = jsonObject["manifest"]?.jsonObject
        val name = manifest?.get("name")?.jsonPrimitive?.contentOrNull
            ?: jsonObject["name"]?.jsonPrimitive?.contentOrNull
            ?: extractFallbackNameFromUrl(sourceUrl)
            ?: "Installed_Theme"

        return Pair(name, content)
    }

    private fun extractFallbackNameFromUrl(url: String): String? {
        val uri = url.substringBefore("?").substringBefore("#")
        val lastSegment = uri.substringAfterLast("/").takeIf { it.isNotBlank() }
        if (lastSegment != null && lastSegment.contains(".")) {
            return lastSegment.substringBeforeLast(".")
        }
        val idMatch = Regex("""id=(\d+)""").find(url)
        if (idMatch != null) {
            return "Theme_${idMatch.groupValues[1]}"
        }
        return lastSegment
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[^a-zA-Z0-9_\- ]"""), "").trim().ifBlank { "Theme" }
    }

    private fun parseThemeFile(file: File): ThemeInfo {
        try {
            val text = file.readText()
            val json = Json.parseToJsonElement(text).jsonObject
            val manifest = json["manifest"]?.jsonObject

            val name = manifest?.get("name")?.jsonPrimitive?.contentOrNull
                ?: json["name"]?.jsonPrimitive?.contentOrNull
                ?: file.nameWithoutExtension

            val author = manifest?.get("author")?.jsonPrimitive?.contentOrNull
                ?: json["author"]?.jsonPrimitive?.contentOrNull
                ?: "Unknown"

            val version = manifest?.get("version")?.jsonPrimitive?.contentOrNull
                ?: json["version"]?.jsonPrimitive?.contentOrNull
                ?: "1.0.0"

            val description = manifest?.get("description")?.jsonPrimitive?.contentOrNull
                ?: json["description"]?.jsonPrimitive?.contentOrNull
                ?: ""

            val backgroundUrl = json["background"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

            val primaryColor = json["color_brand"]?.jsonPrimitive?.contentOrNull
                ?: json["color_primary_dark"]?.jsonPrimitive?.contentOrNull
                ?: json["color_background_primary"]?.jsonPrimitive?.contentOrNull

            return ThemeInfo(
                file = file,
                name = name,
                author = author,
                version = version,
                description = description,
                backgroundUrl = backgroundUrl,
                primaryColor = primaryColor,
            )
        } catch (t: Throwable) {
            return ThemeInfo(
                file = file,
                name = file.nameWithoutExtension,
                author = "Unknown",
                version = "1.0.0",
                description = "",
            )
        }
    }
}
