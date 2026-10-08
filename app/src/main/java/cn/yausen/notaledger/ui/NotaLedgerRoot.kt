package cn.yausen.notaledger.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.yausen.notaledger.data.LedgerRepository
import cn.yausen.notaledger.data.UserPreferences
import cn.yausen.notaledger.ui.screens.AddEditScreen
import cn.yausen.notaledger.ui.screens.CategoriesScreen
import cn.yausen.notaledger.ui.screens.HomeScreen
import cn.yausen.notaledger.ui.screens.StatsScreen

/** Bottom-bar destinations. */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Ledger("ledger", "账本", Icons.AutoMirrored.Filled.ReceiptLong),
    Stats("stats", "统计", Icons.Filled.BarChart),
    Categories("categories", "分类", Icons.Filled.Category),
}

object Routes {
    const val ADD = "add?editId={editId}"
    const val HOME = "ledger"
    const val STATS = "stats"
    const val CATEGORIES = "categories"

    fun add(editId: Long? = null): String = if (editId == null) "add" else "add?editId=$editId"
}

@Composable
fun NotaLedgerRoot(
    repository: LedgerRepository,
    preferences: UserPreferences,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(Tab.Ledger.route, Tab.Stats.route, Tab.Categories.route)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    // Keep a single copy of each tab and remember its scroll state.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Ledger.route,
            modifier = Modifier
                // Consuming the outer insets is what stops the nested Scaffolds in the
                // screens from adding the status-bar height a second time — without it
                // their top app bars are pushed down by a full status bar.
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Tab.Ledger.route) {
                val vm: LedgerViewModel = viewModel(
                    factory = LedgerViewModelFactory(repository, preferences),
                )
                HomeScreen(
                    viewModel = vm,
                    onAddTransaction = { navController.navigate(Routes.add()) },
                    onEditTransaction = { id -> navController.navigate(Routes.add(id)) },
                )
            }

            composable(Tab.Stats.route) {
                val vm: LedgerViewModel = viewModel(
                    factory = LedgerViewModelFactory(repository, preferences),
                )
                StatsScreen(viewModel = vm)
            }

            composable(Tab.Categories.route) {
                val vm: CategoryViewModel = viewModel(
                    factory = CategoryViewModelFactory(repository),
                )
                CategoriesScreen(viewModel = vm)
            }

            composable(
                route = Routes.ADD,
                arguments = listOf(
                    navArgument("editId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val editId = entry.arguments?.getLong("editId")?.takeIf { it >= 0L }
                val vm: AddEditViewModel = viewModel(
                    factory = AddEditViewModelFactory(repository, editId),
                )
                AddEditScreen(
                    viewModel = vm,
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}
