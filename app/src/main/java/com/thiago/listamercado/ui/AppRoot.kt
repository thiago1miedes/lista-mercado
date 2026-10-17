@file:OptIn(ExperimentalLayoutApi::class)

package com.thiago.listamercado.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thiago.listamercado.SharedFiles
import com.thiago.listamercado.ui.catalog.CatalogScreen
import com.thiago.listamercado.ui.catalog.ProductDetailScreen
import com.thiago.listamercado.ui.compare.CompareScreen
import com.thiago.listamercado.ui.lists.ListDetailScreen
import com.thiago.listamercado.ui.lists.ListsScreen
import com.thiago.listamercado.ui.offers.ImportScreen
import com.thiago.listamercado.ui.offers.OffersScreen
import com.thiago.listamercado.ui.purchases.PurchaseEditScreen
import com.thiago.listamercado.ui.purchases.PurchasesScreen

private object Routes {
    const val LISTS = "lists"
    const val LIST = "list/{listId}"
    const val PURCHASES = "purchases"
    const val PURCHASE = "purchase/{purchaseId}?fromList={fromList}"
    const val OFFERS = "offers"
    const val IMPORT = "import"
    const val COMPARE = "compare"
    const val CATALOG = "catalog"
    const val PRODUCT = "product/{productId}"

    fun list(id: Long) = "list/$id"
    fun purchase(id: Long, fromList: Long = 0) = "purchase/$id?fromList=$fromList"
    fun product(id: Long) = "product/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.LISTS, "Listas", Icons.Filled.Checklist),
    Tab(Routes.PURCHASES, "Compras", Icons.Filled.Receipt),
    Tab(Routes.OFFERS, "Promoções", Icons.Filled.LocalOffer),
    Tab(Routes.COMPARE, "Comparar", Icons.Filled.Savings),
    Tab(Routes.CATALOG, "Cadastros", Icons.Filled.Inventory2),
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val incoming by SharedFiles.incoming.collectAsStateWithLifecycle()

    LaunchedEffect(incoming) {
        if (incoming != null && navController.currentDestination?.route != Routes.IMPORT) {
            navController.navigate(Routes.IMPORT) { launchSingleTop = true }
        }
    }

    val showBottomBar = tabs.any { it.route == currentDestination?.route }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LISTS,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.LISTS) {
                ListsScreen(onOpenList = { navController.navigate(Routes.list(it)) })
            }
            composable(
                Routes.LIST,
                arguments = listOf(navArgument("listId") { type = NavType.LongType }),
            ) { entry ->
                ListDetailScreen(
                    listId = entry.arguments?.getLong("listId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onRegisterPurchase = { listId -> navController.navigate(Routes.purchase(0, listId)) },
                    onOpenProduct = { navController.navigate(Routes.product(it)) },
                )
            }
            composable(Routes.PURCHASES) {
                PurchasesScreen(
                    onOpenPurchase = { navController.navigate(Routes.purchase(it)) },
                    onNewPurchase = { navController.navigate(Routes.purchase(0)) },
                )
            }
            composable(
                Routes.PURCHASE,
                arguments = listOf(
                    navArgument("purchaseId") { type = NavType.LongType },
                    navArgument("fromList") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                PurchaseEditScreen(
                    purchaseId = entry.arguments?.getLong("purchaseId") ?: 0L,
                    fromListId = entry.arguments?.getLong("fromList") ?: 0L,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.OFFERS) {
                OffersScreen(
                    onImport = { navController.navigate(Routes.IMPORT) },
                    onOpenProduct = { navController.navigate(Routes.product(it)) },
                )
            }
            composable(Routes.IMPORT) {
                ImportScreen(onDone = { navController.popBackStack() })
            }
            composable(Routes.COMPARE) {
                CompareScreen(onOpenProduct = { navController.navigate(Routes.product(it)) })
            }
            composable(Routes.CATALOG) {
                CatalogScreen(onOpenProduct = { navController.navigate(Routes.product(it)) })
            }
            composable(
                Routes.PRODUCT,
                arguments = listOf(navArgument("productId") { type = NavType.LongType }),
            ) { entry ->
                ProductDetailScreen(
                    productId = entry.arguments?.getLong("productId") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
