package com.aliucord.manager.ui.screens.themes

import android.os.Parcelable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.koin.koinScreenModel
import com.aliucord.manager.manager.ThemeInfo
import com.aliucord.manager.ui.screens.themes.components.ThemeItemCard
import com.aliucord.manager.ui.screens.themes.components.ThemesAppBar
import com.aliucord.manager.ui.screens.themes.components.ThemesEmpty
import com.aliucord.manager.ui.screens.themes.components.dialogs.AddThemeDialog
import com.aliucord.manager.ui.screens.themes.components.dialogs.DeleteThemeDialog
import com.aliucord.manager.ui.util.paddings.PaddingValuesSides
import com.aliucord.manager.ui.util.paddings.exclude
import dev.shiggy.manager.R
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class ThemeScreen : Screen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey
        get() = "ThemeScreen"

    @Composable
    override fun Content() {
        val model = koinScreenModel<ThemeScreenModel>()

        var showAddDialog by remember { mutableStateOf(false) }
        var themeToDelete by remember { mutableStateOf<ThemeInfo?>(null) }

        if (showAddDialog) {
            AddThemeDialog(
                isDownloading = model.isDownloading,
                onDownload = { url ->
                    model.downloadTheme(
                        url = url,
                        onSuccess = {
                            showAddDialog = false
                        }
                    )
                },
                onDismiss = {
                    showAddDialog = false
                }
            )
        }

        themeToDelete?.let { theme ->
            DeleteThemeDialog(
                theme = theme,
                onConfirm = {
                    model.deleteTheme(theme)
                    themeToDelete = null
                },
                onDismiss = {
                    themeToDelete = null
                }
            )
        }

        ThemeScreenContent(
            themes = model.themes,
            isLoading = model.isLoading,
            onRefresh = { model.refreshThemes() },
            onAddTheme = { showAddDialog = true },
            onDeleteTheme = { themeToDelete = it },
        )
    }
}

@Composable
fun ThemeScreenContent(
    themes: SnapshotStateList<ThemeInfo>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onAddTheme: () -> Unit,
    onDeleteTheme: (ThemeInfo) -> Unit,
) {
    Scaffold(
        topBar = {
            ThemesAppBar(
                onRefresh = onRefresh,
                onAddTheme = onAddTheme,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTheme,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = stringResource(R.string.themes_action_add),
                )
            }
        }
    ) { paddingValues ->
        if (isLoading && themes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = paddingValues.exclude(PaddingValuesSides.Horizontal + PaddingValuesSides.Top),
                modifier = Modifier
                    .padding(paddingValues.exclude(PaddingValuesSides.Bottom))
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                if (themes.isEmpty()) {
                    item(key = "EMPTY") {
                        ThemesEmpty(
                            onAddTheme = onAddTheme,
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                }

                items(
                    items = themes,
                    key = { it.file.absolutePath },
                ) { theme ->
                    ThemeItemCard(
                        theme = theme,
                        onDelete = { onDeleteTheme(theme) },
                    )
                }
            }
        }
    }
}
