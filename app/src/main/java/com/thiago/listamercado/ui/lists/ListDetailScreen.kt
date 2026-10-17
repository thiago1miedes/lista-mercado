@file:OptIn(ExperimentalMaterial3Api::class)

package com.thiago.listamercado.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiago.listamercado.core.BasketComparison
import com.thiago.listamercado.core.BasketItem
import com.thiago.listamercado.core.ItemComparison
import com.thiago.listamercado.core.Money
import com.thiago.listamercado.core.PriceComparator
import com.thiago.listamercado.core.Quantity
import com.thiago.listamercado.data.ListItemRow
import com.thiago.listamercado.data.Repository
import com.thiago.listamercado.ui.components.ConfirmDialog
import com.thiago.listamercado.ui.components.EmptyState
import com.thiago.listamercado.ui.components.LocalRepository
import com.thiago.listamercado.ui.components.ProductSuggestions
import com.thiago.listamercado.ui.components.SectionHeader
import com.thiago.listamercado.ui.components.TextInputDialog
import com.thiago.listamercado.ui.components.suggestProducts
import com.thiago.listamercado.ui.components.toState
import com.thiago.listamercado.ui.theme.PromoColor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ListDetailViewModel(private val repository: Repository, private val listId: Long) : ViewModel() {
    val list = repository.observeList(listId).toState(this, null)
    val items = repository.observeListItems(listId).toState(this, emptyList())
    val products = repository.products.toState(this, emptyList())
    val stores = repository.stores.toState(this, emptyList())
    val comparison = combine(
        repository.observeListItems(listId),
        repository.stores,
        repository.currentPrices,
    ) { items, stores, prices ->
        if (items.isEmpty() || stores.isEmpty()) null
        else PriceComparator.compare(items.map { BasketItem(it.productId, it.quantity) }, stores.map { it.id }, prices)
    }.toState(this, null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun addItem(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addItemToList(listId, name) }
    }

    fun addProduct(productId: Long) {
        viewModelScope.launch { repository.addProductToList(listId, productId) }
    }

    fun toggle(item: ListItemRow) {
        viewModelScope.launch { repository.setItemChecked(item.id, !item.checked) }
    }

    fun setQuantity(item: ListItemRow, quantity: Double) {
        viewModelScope.launch { repository.setItemQuantity(item.id, quantity) }
    }

    fun delete(item: ListItemRow) {
        viewModelScope.launch { repository.deleteListItem(item.id) }
    }

    fun addFavorites() {
        viewModelScope.launch {
            val added = repository.addFavoritesToList(listId)
            _messages.tryEmit(
                when (added) {
                    0 -> "Nenhum item novo. Marque ⭐ \"sempre compro\" nos produtos em Cadastros."
                    1 -> "1 item adicionado"
                    else -> "$added itens adicionados"
                },
            )
        }
    }

    fun uncheckAll() {
        viewModelScope.launch { repository.uncheckAll(listId) }
    }

    fun removeChecked() {
        viewModelScope.launch { repository.removeCheckedItems(listId) }
    }

    fun rename(name: String) {
        viewModelScope.launch { repository.renameList(listId, name) }
    }

    fun deleteList(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteList(listId)
            onDeleted()
        }
    }
}

@Composable
fun ListDetailScreen(
    listId: Long,
    onBack: () -> Unit,
    onRegisterPurchase: (Long) -> Unit,
    onOpenProduct: (Long) -> Unit,
) {
    val repository = LocalRepository.current
    val viewModel: ListDetailViewModel = viewModel { ListDetailViewModel(repository, listId) }
    val list by viewModel.list.collectAsStateWithLifecycle()
    val listItems by viewModel.items.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val comparison by viewModel.comparison.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ListItemRow?>(null) }

    val storeNames = remember(stores) { stores.associate { it.id to it.name } }
    val byProduct = remember(comparison) { comparison?.items?.associateBy { it.productId } ?: emptyMap() }
    val inList = remember(listItems) { listItems.map { it.productId }.toSet() }
    val suggestions = remember(query, products, inList) { suggestProducts(query, products, inList) }
    val pending = listItems.filter { !it.checked }
    val inCart = listItems.filter { it.checked }

    fun submit() {
        viewModel.addItem(query)
        query = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(list?.name ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Adicionar meus itens de sempre ⭐") },
                            onClick = {
                                menuOpen = false
                                viewModel.addFavorites()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Desmarcar todos") },
                            onClick = {
                                menuOpen = false
                                viewModel.uncheckAll()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remover itens marcados") },
                            onClick = {
                                menuOpen = false
                                viewModel.removeChecked()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Renomear lista") },
                            onClick = {
                                menuOpen = false
                                renaming = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Excluir lista") },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (listItems.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onRegisterPurchase(listId) },
                    icon = { Icon(Icons.Filled.AddShoppingCart, contentDescription = null) },
                    text = { Text("Registrar compra") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "add") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Adicionar item (ex.: arroz)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = { submit() }, enabled = query.isNotBlank()) {
                            Icon(Icons.Filled.Add, contentDescription = "Adicionar")
                        }
                    }
                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        ProductSuggestions(suggestions, onPick = {
                            viewModel.addProduct(it.id)
                            query = ""
                        })
                    }
                }
            }

            val current = comparison
            if (current != null && current.best != null) {
                item(key = "best") { BestStoreCard(current, storeNames) }
            }

            if (listItems.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.ShoppingCart,
                        title = "Lista vazia",
                        message = "Digite um item acima. Os itens que você marcar como \"sempre compro\" " +
                            "podem ser adicionados de uma vez pelo menu ⋮.",
                    )
                }
            }

            items(pending, key = { it.id }) { item ->
                ItemRow(item, byProduct[item.productId], storeNames, onToggle = { viewModel.toggle(item) }, onEdit = { editing = item })
            }
            if (inCart.isNotEmpty()) {
                item(key = "cart-header") { SectionHeader("No carrinho (${inCart.size})") }
                items(inCart, key = { it.id }) { item ->
                    ItemRow(item, byProduct[item.productId], storeNames, onToggle = { viewModel.toggle(item) }, onEdit = { editing = item })
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "Renomear lista",
            label = "Nome",
            initial = list?.name ?: "",
            onConfirm = viewModel::rename,
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Excluir lista?",
            message = "A lista \"${list?.name ?: ""}\" e seus itens serão removidos. Produtos e compras registradas continuam salvos.",
            onConfirm = { viewModel.deleteList(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
    editing?.let { item ->
        EditItemDialog(
            item = item,
            onSave = { viewModel.setQuantity(item, it) },
            onDelete = { viewModel.delete(item) },
            onOpenProduct = { onOpenProduct(item.productId) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun BestStoreCard(comparison: BasketComparison, storeNames: Map<Long, String>) {
    val best = comparison.best ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Mercado mais barato para esta lista", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Store, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    storeNames[best.storeId] ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(Money.format(best.estimatedTotalCents), style = MaterialTheme.typography.titleLarge)
            }
            val detail = buildString {
                append("${best.pricedItems} de ${comparison.comparableItems} itens com preço")
                if (best.missingItems > 0) append(" (os outros pela média)")
                if (best.promoItems > 0) append(" · ${best.promoItems} em promoção")
            }
            Text(detail, style = MaterialTheme.typography.bodySmall)
            comparison.ranking.drop(1).filter { it.pricedItems > 0 }.take(3).forEach { other ->
                Text(
                    "${storeNames[other.storeId] ?: "?"}: ${Money.format(other.estimatedTotalCents)}" +
                        " (+${Money.format(other.estimatedTotalCents - best.estimatedTotalCents)})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (comparison.splitSavingsCents > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Comprando cada item onde é mais barato: ${Money.format(comparison.splitTotalCents)} " +
                        "(economia de ${Money.format(comparison.splitSavingsCents)})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            val withoutPrice = comparison.items.size - comparison.comparableItems
            if (withoutPrice > 0) {
                Text(
                    "$withoutPrice ${if (withoutPrice == 1) "item ainda sem preço" else "itens ainda sem preço"} em nenhum mercado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: ListItemRow,
    comparison: ItemComparison?,
    storeNames: Map<Long, String>,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val quantity = if (item.quantity != 1.0) "${Quantity.format(item.quantity)}× " else ""
                Text(
                    quantity + item.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (item.favorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Star, contentDescription = "Sempre compro", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                }
            }
            val bestStore = comparison?.bestStoreId
            val bestPrice = comparison?.bestPriceCents
            if (bestStore != null && bestPrice != null) {
                val promo = comparison?.prices?.get(bestStore)?.isPromotion == true
                Text(
                    "${Money.format(bestPrice)} em ${storeNames[bestStore] ?: "?"}" + if (promo) " · promoção" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (promo) PromoColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Sem preço registrado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Editar item")
        }
    }
}

@Composable
private fun EditItemDialog(
    item: ListItemRow,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit,
    onOpenProduct: () -> Unit,
    onDismiss: () -> Unit,
) {
    var quantity by remember(item.id) { mutableStateOf(Quantity.format(item.quantity)) }
    val parsed = Quantity.parse(quantity)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.productName) },
        text = {
            Column {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantidade") },
                    singleLine = true,
                    isError = parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) { Text("Remover da lista") }
                    TextButton(onClick = {
                        onDismiss()
                        onOpenProduct()
                    }) { Text("Ver preços") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = {
                    parsed?.let(onSave)
                    onDismiss()
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
