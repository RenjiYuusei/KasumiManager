package com.aliucord.manager.ui.screens.themes

import android.os.Parcelable
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.koin.koinScreenModel
import com.aliucord.manager.manager.ThemeInfo
import com.aliucord.manager.ui.components.BackButton
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
        val model = koinScreenModel<ThemeModel>()
        val clipboardManager = LocalClipboardManager.current
        var themeToDelete by remember { mutableStateOf<ThemeInfo?>(null) }

        if (themeToDelete != null) {
            val theme = themeToDelete!!
            AlertDialog(
                onDismissRequest = { themeToDelete = null },
                title = { Text(stringResource(R.string.theme_delete_confirm_title)) },
                text = { Text(stringResource(R.string.theme_delete_confirm_msg, theme.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            model.deleteTheme(theme)
                            themeToDelete = null
                        }
                    ) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { themeToDelete = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.themes_title)) },
                    navigationIcon = { BackButton() },
                    actions = {
                        IconButton(onClick = model::refreshThemes) {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = stringResource(R.string.action_retry),
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = paddingValues.exclude(PaddingValuesSides.Horizontal + PaddingValuesSides.Top),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues.exclude(PaddingValuesSides.Bottom))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Download Card
                item(key = "DOWNLOAD_SECTION") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_download),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.theme_download_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            OutlinedTextField(
                                value = model.urlInput,
                                onValueChange = { model.urlInput = it },
                                label = { Text(stringResource(R.string.theme_download_url_hint)) },
                                singleLine = true,
                                trailingIcon = {
                                    if (model.urlInput.isNotEmpty()) {
                                        IconButton(onClick = { model.urlInput = "" }) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_close),
                                                contentDescription = stringResource(R.string.action_clear),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                val clip = clipboardManager.getText()?.text
                                                if (!clip.isNullOrBlank()) {
                                                    model.urlInput = clip
                                                }
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_copy),
                                                contentDescription = stringResource(R.string.theme_download_paste),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            AnimatedVisibility(visible = model.downloadError != null) {
                                Text(
                                    text = model.downloadError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }

                            Button(
                                onClick = { model.downloadTheme() },
                                enabled = model.urlInput.isNotBlank() && !model.isDownloading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                if (model.isDownloading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(end = 8.dp)
                                    )
                                    Text(stringResource(R.string.theme_download_downloading))
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_download),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(18.dp)
                                    )
                                    Text(stringResource(R.string.theme_download_button))
                                }
                            }
                        }
                    }
                }

                // Installed Themes Header
                item(key = "INSTALLED_HEADER") {
                    Text(
                        text = "${stringResource(R.string.themes_installed_header)} (${model.installedThemes.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }

                // Empty State
                if (model.installedThemes.isEmpty()) {
                    item(key = "NO_THEMES") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                                .alpha(0.7f)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_palette),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.themes_no_themes),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                // Installed Themes List
                items(
                    items = model.installedThemes,
                    key = { it.file.absolutePath }
                ) { theme ->
                    ThemeItemCard(
                        theme = theme,
                        onDelete = { themeToDelete = theme },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeItemCard(
    theme: ThemeInfo,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formattedSize = remember(theme.sizeBytes) {
        Formatter.formatFileSize(context, theme.sizeBytes)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_brush),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subInfo = buildString {
                    if (!theme.author.isNullOrBlank()) {
                        append("by ${theme.author}")
                    }
                    if (!theme.version.isNullOrBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append("v${theme.version}")
                    }
                }

                if (subInfo.isNotEmpty()) {
                    Text(
                        text = subInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!theme.description.isNullOrBlank()) {
                    Text(
                        text = theme.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${theme.file.name} • $formattedSize",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_forever),
                    contentDescription = stringResource(R.string.action_uninstall),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
