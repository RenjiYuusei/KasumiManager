package com.aliucord.manager.ui.screens.themes.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import dev.shiggy.manager.R

@Composable
fun ThemesAppBar(
    onRefresh: () -> Unit,
    onAddTheme: () -> Unit,
) {
    val navigator = LocalNavigator.current

    TopAppBar(
        title = {
            Text(stringResource(R.string.themes_title))
        },
        navigationIcon = {
            IconButton(onClick = { navigator?.pop() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.navigation_back),
                )
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = stringResource(R.string.action_retry),
                )
            }
            IconButton(onClick = onAddTheme) {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = stringResource(R.string.themes_action_add),
                )
            }
        }
    )
}
