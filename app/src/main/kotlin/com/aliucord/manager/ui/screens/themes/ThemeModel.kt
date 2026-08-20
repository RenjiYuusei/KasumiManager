package com.aliucord.manager.ui.screens.themes

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.aliucord.manager.manager.ThemeInfo
import com.aliucord.manager.manager.ThemeManager
import com.aliucord.manager.util.launchIO
import com.aliucord.manager.util.mainThread
import com.aliucord.manager.util.showToast
import dev.shiggy.manager.R

class ThemeModel(
    private val themeManager: ThemeManager,
    private val application: Application,
) : ScreenModel {
    val installedThemes = mutableStateListOf<ThemeInfo>()

    var urlInput by mutableStateOf("")
    var isDownloading by mutableStateOf(false)
    var downloadError by mutableStateOf<String?>(null)

    init {
        refreshThemes()
    }

    fun refreshThemes() = screenModelScope.launchIO {
        val list = themeManager.getInstalledThemes()
        mainThread {
            installedThemes.clear()
            installedThemes.addAll(list)
        }
    }

    fun downloadTheme(inputUrl: String? = null) {
        val targetUrl = (inputUrl ?: urlInput).trim()
        if (targetUrl.isBlank()) return

        isDownloading = true
        downloadError = null

        screenModelScope.launchIO {
            val result = themeManager.downloadTheme(targetUrl)
            mainThread {
                isDownloading = false
                result.fold(
                    onSuccess = { theme ->
                        urlInput = ""
                        refreshThemes()
                        application.showToast(R.string.theme_download_success, theme.name)
                    },
                    onFailure = { t ->
                        val err = t.message ?: t.toString()
                        downloadError = err
                        application.showToast(R.string.theme_download_failed, err)
                    }
                )
            }
        }
    }

    fun deleteTheme(theme: ThemeInfo) = screenModelScope.launchIO {
        val success = themeManager.deleteTheme(theme)
        mainThread {
            if (success) {
                installedThemes.remove(theme)
                application.showToast(R.string.theme_deleted)
            }
        }
    }
}
