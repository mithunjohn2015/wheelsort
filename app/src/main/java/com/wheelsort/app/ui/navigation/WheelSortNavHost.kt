package com.wheelsort.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wheelsort.app.ui.home.HomeScreen
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

    fun sort(album: String?, newestFirst: Boolean): String {
        val encoded = URLEncoder.encode(album ?: "", "UTF-8")
        return "sort?album=$encoded&newestFirst=$newestFirst"
    }
}

@Composable
fun WheelSortNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartSorting = { album, newestFirst -> navController.navigate(Routes.sort(album, newestFirst)) },
                onOpenTrash = { navController.navigate(Routes.TRASH) },
                onOpenStats = { navController.navigate(Routes.STATS) }
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
            StatsScreen(onExit = { navController.popBackStack() })
        }
    }
}
