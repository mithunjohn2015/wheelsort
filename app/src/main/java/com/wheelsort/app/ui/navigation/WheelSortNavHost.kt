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
    const val SORT = "sort?album={album}"
    const val TRASH = "trash"
    const val STATS = "stats"

    fun sort(album: String?): String {
        val encoded = URLEncoder.encode(album ?: "", "UTF-8")
        return "sort?album=$encoded"
    }
}

@Composable
fun WheelSortNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartSorting = { album -> navController.navigate(Routes.sort(album)) },
                onOpenTrash = { navController.navigate(Routes.TRASH) },
                onOpenStats = { navController.navigate(Routes.STATS) }
            )
        }
        composable(
            route = Routes.SORT,
            arguments = listOf(navArgument("album") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("album").orEmpty()
            val album = if (raw.isBlank()) null else URLDecoder.decode(raw, "UTF-8")
            SortScreen(albumFilter = album, onExit = { navController.popBackStack() })
        }
        composable(Routes.TRASH) {
            TrashScreen(onExit = { navController.popBackStack() })
        }
        composable(Routes.STATS) {
            StatsScreen(onExit = { navController.popBackStack() })
        }
    }
}
