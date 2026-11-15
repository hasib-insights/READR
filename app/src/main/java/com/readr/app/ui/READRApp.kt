package com.readr.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.readr.app.ReadrApplication
import com.readr.app.di.AppContainer
import com.readr.app.domain.model.AppPrefs
import com.readr.app.domain.model.AppThemeMode
import com.readr.app.ui.comfort.EyeComfortScreen
import com.readr.app.ui.details.DetailsScreen
import com.readr.app.ui.library.LibraryScreen
import com.readr.app.ui.paper.PaperStudioScreen
import com.readr.app.ui.reader.ReaderScreen
import com.readr.app.ui.settings.SettingsScreen
import com.readr.app.ui.theme.READRTheme

object Routes {
    const val LIBRARY = "library"
    const val PAPER_STUDIO = "paper_studio"
    const val EYE_COMFORT = "eye_comfort"
    const val SETTINGS = "settings"
    const val DETAILS = "details/{id}"
    const val READER = "reader/{id}"

    fun details(id: Long) = "details/$id"
    fun reader(id: Long) = "reader/$id"
}

private class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.LIBRARY, "Library", Icons.Filled.AutoStories),
    Tab(Routes.PAPER_STUDIO, "Paper Studio", Icons.Filled.Palette),
    Tab(Routes.EYE_COMFORT, "Eye Comfort", Icons.Filled.Contrast),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

/**
 * Root of the app: theme by user preference, bottom navigation between the
 * four main sections, and a chrome-free Reading Room for documents.
 */
@Composable
fun READRApp() {
    val container = (LocalContext.current.applicationContext as ReadrApplication).container
    val appPrefs by container.preferencesRepository.appPrefs
        .collectAsStateWithLifecycle(initialValue = AppPrefs())

    val darkTheme = when (appPrefs.appThemeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    CompositionLocalProvider(AppContainer.Local provides container) {
        READRTheme(darkTheme = darkTheme) {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route

            Scaffold(
                bottomBar = {
                    if (currentRoute in tabs.map { it.route }) {
                        ReadrBottomBar(navController, currentRoute)
                    }
                }
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.LIBRARY,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            onOpenDocument = { id -> navController.navigate(Routes.reader(id)) },
                            onOpenDetails = { id -> navController.navigate(Routes.details(id)) },
                        )
                    }
                    composable(Routes.PAPER_STUDIO) { PaperStudioScreen() }
                    composable(Routes.EYE_COMFORT) { EyeComfortScreen() }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onOpenPaperStudio = {
                                navController.navigate(Routes.PAPER_STUDIO) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable(
                        Routes.DETAILS,
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: 0L
                        DetailsScreen(
                            documentId = id,
                            onOpen = { navController.navigate(Routes.reader(id)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        Routes.READER,
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: 0L
                        ReaderScreen(documentId = id, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadrBottomBar(navController: androidx.navigation.NavController, currentRoute: String?) {
    NavigationBar {
        for (tab in tabs) {
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
