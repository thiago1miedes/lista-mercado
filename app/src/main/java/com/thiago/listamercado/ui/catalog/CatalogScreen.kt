@file:OptIn(ExperimentalMaterial3Api::class)

package com.thiago.listamercado.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiago.listamercado.core.TextNormalizer
import com.thiago.listamercado.data.Product
import com.thiago.listamercado.data.Repository
import com.thiago.listamercado.data.Store
import com.thiago.listamercado.ui.components.ConfirmDialog
import com.thiago.listamercado.ui.components.EmptyState
import com.thiago.listamercado.ui.components.LocalRepository
import com.thiago.listamercado.ui.components.TextInputDialog
import com.thiago.listamercado.ui.components.toState
import kotlinx.coroutines.launch

class CatalogViewModel(private val repository: Repository) : ViewModel() {
    val products = repository.products.toState(this, emptyList())
    val stores = repository.stores.toState(this, emptyList())
    val purchaseCounts = repository.purchaseCounts.toState(this, emptyMap())

    fun addProduct(name: String) {
        viewModelScope.launch { repository.findOrCreateProduct(name, favorite = true) }
    }

    fun toggleFavorite(product: Product) {
        viewModelScope.launch { repository.setFavorite(product.id, !product.favorite) }
    }

    fun addStore(name: String) {
        viewModelScope.launch { repository.addStore(name) }
    }

    fun renameStore(store: Store, name: String) {
        viewModelScope.launch { repository.renameStore(store, name) }
    }

    fun deleteStore(store: Store) {
        viewModelScope.launch { repository.deleteStore(store) }
    }
}

@Composable
fun CatalogScreen(onOpenProduct: (Long) -> Unit) {
    val repository = LocalRepository.current
    val viewModel: CatalogViewModel = viewModel { CatalogViewModel(repository) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Cadastros") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (tab == 0) "Produto" else "Mercado") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Produtos") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mercados") })
            }
            if (tab == 0) ProductsTab(viewModel, onOpenProduct) else StoresTab(viewModel)
        }
    }

    if (adding) {
        TextInputDialog(
            title = if (tab == 0) "Novo produto" else "Novo mercado",
            label = "Nome",
            confirmText = "Adicionar",
            onConfirm = { if (tab == 0) viewModel.addProduct(it) else viewModel.addStore(it) },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun ProductsTab(viewModel: CatalogViewModel, onOpenProduct: (Long) -> Unit) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val counts by viewModel.purchaseCounts.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val normalized = TextNormalizer.normalize(query)
    val visible = products
        .filter { normalized.isEmpty() || it.normalizedName.contains(normalized) }
        .sortedWith(compareByDescending<Product> { it.favorite }.thenBy { it.name.lowercase() })

    if (products.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Inventory2,
            title = "Nenhum produto ainda",
            message = "Os produtos são criados quando você adiciona itens nas listas, compras ou promoções. " +
                "Marque ⭐ os que você sempre compra para acompanhar onde estão mais baratos.",
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar produto") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        item(key = "hint") {
            Text(
                "⭐ = sempre compro. Esses produtos formam sua cesta na aba Comparar e são destacados nos encartes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        items(visible, key = { it.id }) { product ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenProduct(product.id) }.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    Text(product.name, style = MaterialTheme.typography.bodyLarge)
                    val times = counts[product.id] ?: 0
                    if (times > 0) {
                        Text(
                            if (times == 1) "Comprado 1 vez" else "Comprado $times vezes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { viewModel.toggleFavorite(product) }) {
                    if (product.favorite) {
                        Icon(Icons.Filled.Star, contentDescription = "Remover de sempre compro", tint = MaterialTheme.colorScheme.secondary)
                    } else {
                        Icon(Icons.Filled.StarBorder, contentDescription = "Marcar como sempre compro")
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun StoresTab(viewModel: CatalogViewModel) {
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Store?>(null) }
    var deleting by remember { mutableStateOf<Store?>(null) }

    if (stores.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Store,
            title = "Nenhum mercado cadastrado",
            message = "Adicione os mercados onde você costuma comprar para comparar os preços entre eles.",
        )
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(stores, key = { it.id }) { store ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = store }.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(store.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    editing?.let { store ->
        StoreDialog(
            store = store,
            onRename = { viewModel.renameStore(store, it) },
            onDelete = { deleting = store },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { store ->
        ConfirmDialog(
            title = "Excluir ${store.name}?",
            message = "As promoções desse mercado serão apagadas. As compras continuam no histórico, sem o mercado.",
            onConfirm = { viewModel.deleteStore(store) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun StoreDialog(store: Store, onRename: (String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember(store.id) { mutableStateOf(store.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mercado") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    onDismiss()
                    onDelete()
                }) { Text("Excluir mercado") }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onRename(name)
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
