package com.andrespar2.parflix.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.andrespar2.parflix.domain.MediaType
import com.andrespar2.parflix.ui.screens.about.AboutScreen
import com.andrespar2.parflix.ui.screens.catalog.CatalogScreen
import com.andrespar2.parflix.ui.screens.details.DetailsScreen
import com.andrespar2.parflix.ui.screens.home.HomeScreen
import com.andrespar2.parflix.ui.screens.library.LibraryScreen
import com.andrespar2.parflix.ui.screens.player.PlayerScreen
import com.andrespar2.parflix.ui.screens.search.SearchScreen
import com.andrespar2.parflix.ui.screens.settings.SettingsScreen

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val mediaType: MediaType? = null,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.HOME, "Inicio", Icons.Filled.Home),
    TopLevelDestination(Routes.catalog(MediaType.MOVIE.name), "Películas", Icons.Filled.Movie, MediaType.MOVIE),
    TopLevelDestination(Routes.catalog(MediaType.SERIES.name), "Series", Icons.Filled.Tv, MediaType.SERIES),
    TopLevelDestination(Routes.catalog(MediaType.ANIME.name), "Anime", Icons.Filled.AutoAwesome, MediaType.ANIME),
    TopLevelDestination(Routes.SEARCH, "Buscador", Icons.Filled.Search),
)

@Composable
fun ParFlixNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTypeArg = backStackEntry?.arguments?.getString(Routes.ARG_MEDIA_TYPE)

    val showBottomBar = currentRoute == Routes.HOME ||
        currentRoute == Routes.SEARCH ||
        currentRoute == Routes.catalogPattern()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    topLevelDestinations.forEach { destination ->
                        val selected = when {
                            destination.mediaType != null ->
                                currentRoute == Routes.catalogPattern() &&
                                    currentTypeArg == destination.mediaType.name
                            else -> currentRoute == destination.route
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenDetails = { type, tmdbId ->
                        navController.navigate(Routes.details(type.name, tmdbId))
                    },
                    onOpenCatalog = { type -> navController.navigateTopLevel(Routes.catalog(type.name)) },
                    onOpenSearch = { navController.navigateTopLevel(Routes.SEARCH) },
                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                )
            }

            composable(
                route = Routes.catalogPattern(),
                arguments = listOf(navArgument(Routes.ARG_MEDIA_TYPE) { type = NavType.StringType }),
            ) { entry ->
                val mediaType = entry.arguments?.getString(Routes.ARG_MEDIA_TYPE)
                    ?.let { MediaType.fromString(it) } ?: MediaType.MOVIE
                CatalogScreen(
                    mediaType = mediaType,
                    onBack = { navController.popBackStack() },
                    onOpenDetails = { type, tmdbId ->
                        navController.navigate(Routes.details(type.name, tmdbId))
                    },
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetails = { type, tmdbId ->
                        navController.navigate(Routes.details(type.name, tmdbId))
                    },
                )
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetails = { type, tmdbId ->
                        navController.navigate(Routes.details(type.name, tmdbId))
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.detailsPattern(),
                arguments = listOf(
                    navArgument(Routes.ARG_MEDIA_TYPE) { type = NavType.StringType },
                    navArgument(Routes.ARG_TMDB_ID) { type = NavType.IntType },
                ),
            ) { entry ->
                val mediaType = entry.arguments?.getString(Routes.ARG_MEDIA_TYPE)
                    ?.let { MediaType.fromString(it) } ?: MediaType.MOVIE
                val tmdbId = entry.arguments?.getInt(Routes.ARG_TMDB_ID) ?: 0
                DetailsScreen(
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    onBack = { navController.popBackStack() },
                    onPlay = { type, id, season, episode ->
                        navController.navigate(Routes.player(type.name, id, season, episode))
                    },
                )
            }

            composable(
                route = Routes.playerPattern(),
                arguments = listOf(
                    navArgument(Routes.ARG_MEDIA_TYPE) { type = NavType.StringType },
                    navArgument(Routes.ARG_TMDB_ID) { type = NavType.IntType },
                    navArgument(Routes.ARG_SEASON) { type = NavType.IntType },
                    navArgument(Routes.ARG_EPISODE) { type = NavType.IntType },
                ),
            ) { entry ->
                val mediaType = entry.arguments?.getString(Routes.ARG_MEDIA_TYPE)
                    ?.let { MediaType.fromString(it) } ?: MediaType.MOVIE
                val tmdbId = entry.arguments?.getInt(Routes.ARG_TMDB_ID) ?: 0
                val season = entry.arguments?.getInt(Routes.ARG_SEASON) ?: -1
                val episode = entry.arguments?.getInt(Routes.ARG_EPISODE) ?: -1
                PlayerScreen(
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = season,
                    episode = episode,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
