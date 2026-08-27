package com.wheelsort.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wheelsort.app.ui.backup.BackupScreen
import com.wheelsort.app.ui.duplicates.DuplicateScreen
import com.wheelsort.app.ui.grid.GridScreen
import com.wheelsort.app.ui.home.HomeScreen
import com.wheelsort.app.ui.organize.OrganizeScreen
import com.wheelsort.app.ui.settings.SettingsScreen
import com.wheelsort.app.ui.sort.SortScreen
import com.wheelsort.app.ui.stats.StatsScreen
import com.wheelsort.app.ui.trash.TrashScreen
import java.net.URLDecoder
import java.net.URLEncoder

private object Routes {
    const val HOME = "home"
    const val SORT = "sort?album={album}&newestFirst={newestFirst}"
    const val TRASH = "trash"
    const val STATS = "stats"
    const val ORGANIZE = "organize"
    const val BACKUP = "backup"
    const val GRID = "grid?album={album}"
    const val DUPLICATES = "duplicates"
    const val SETTINGS = "settings"

    fun sort(album: String?, newestFirst: Boolean): String {
        val encoded = URLEncoder.encode(album ?: "", "UTF-8")
        return "sort?album=$encoded&newestFirst=$newestFirst"
    }

    fun grid(album: String?): String {
        val encoded = URLEncoder.encode(album ?: "", "UTF-8")
        return "grid?album=$encoded"
    }
}

private const val TRANSITION_MS = 320

@Composable
fun WheelSortNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(tween(TRANSITION_MS)) { it / 4 } + fadeIn(tween(TRANSITION_MS))
        },
        exitTransition = {
            slideOutHorizontally(tween(TRANSITION_MS)) { -it / 6 } + fadeOut(tween(TRANSITION_MS))
        },
        popEnterTransition = {
            slideInHorizontally(tween(TRANSITION_MS)) { -it / 6 } + fadeIn(tween(TRANSITION_MS))
        },
        popExitTransition = {
            slideOutHorizontally(tween(TRANSITION_MS)) { it / 4 } + fadeOut(tween(TRANSITION_MS))
        }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartSorting = { album, newestFirst ->
                    navController.navigate(Routes.sort(album, newestFirst))
                },
                onOpenTrash = { navController.navigate(Routes.TRASH) },
                onOpenStats = { navController.navigate(Routes.STATS) },
                onOpenOrganize = { navController.navigate(Routes.ORGANIZE) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onOpenGrid = { album -> navController.navigate(Routes.grid(album)) },
                onOpenDuplicates = { navController.navigate(Routes.DUPLICATES) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            route = Routes.SORT,
            arguments = listOf(
                navArgument("album") { type = NavType.StringType; defaultValue = "" },
                navArgument("newestFirst") { type = NavType.BoolType; defaultValue = true }
            )
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("album").orEmpty()
            val album = if (raw.isBlank()) null else URLDecoder.decode(raw, "UTF-8")
            val newestFirst = backStackEntry.arguments?.getBoolean("newestFirst") ?: true
            SortScreen(
                albumFilter = album,
                newestFirst = newestFirst,
                onExit = { navController.popBackStack() },
                onOpenTrash = { navController.navigate(Routes.TRASH) }
            )
        }
        composable(Routes.TRASH) {
            TrashScreen(onExit = { navController.popBackStack() })
        }
        composable(Routes.STATS) {
            StatsScreen(
                onExit = { navController.popBackStack() },
                onOpenAlbum = { album -> navController.navigate(Routes.grid(album)) }
            )
        }
        composable(Routes.ORGANIZE) {
            OrganizeScreen(onExit = { navController.popBackStack() })
        }
        composable(Routes.BACKUP) {
            BackupScreen(onExit = { navController.popBackStack() })
        }
        composable(
            route = Routes.GRID,
            arguments = listOf(navArgument("album") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("album").orEmpty()
            val album = if (raw.isBlank()) null else URLDecoder.decode(raw, "UTF-8")
            GridScreen(albumFilter = album, onExit = { navController.popBackStack() })
        }
        composable(Routes.DUPLICATES) {
            DuplicateScreen(onExit = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onExit = { navController.popBackStack() })
        }
    }
}
