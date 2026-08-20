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

class ThemeScreenModel(
    private val themeManager: ThemeManager,
    private val application: Application,
) : ScreenModel {
    val themes = mutableStateListOf<ThemeInfo>()

    var isLoading by mutableStateOf(false)
        private set

    var isDownloading by mutableStateOf(false)
        private set

    init {
        refreshThemes()
    }

    fun refreshThemes() = screenModelScope.launchIO {
        mainThread { isLoading = true }
        val list = themeManager.getInstalledThemes()
        mainThread {
            themes.clear()
            themes.addAll(list)
            isLoading = false
        }
    }

    fun downloadTheme(
        url: String,
        onSuccess: (ThemeInfo) -> Unit = {},
        onError: (String) -> Unit = {},
    ) = screenModelScope.launchIO {
        if (url.isBlank()) {
            mainThread { onError("URL cannot be empty") }
            return@launchIO
        }

        mainThread { isDownloading = true }
        val result = themeManager.downloadAndInstallTheme(url)
        mainThread {
            isDownloading = false
            result.onSuccess { info ->
                refreshThemes()
                application.showToast(R.string.themes_status_success, info.name)
                onSuccess(info)
            }.onFailure { t ->
                val errorMsg = t.localizedMessage ?: t.message ?: "Unknown error"
                application.showToast(R.string.themes_status_error, errorMsg)
                onError(errorMsg)
            }
        }
    }

    fun deleteTheme(theme: ThemeInfo) = screenModelScope.launchIO {
        val success = themeManager.deleteTheme(theme.file)
        mainThread {
            if (success) {
                themes.remove(theme)
                application.showToast(R.string.themes_delete_success)
            }
        }
    }
}
