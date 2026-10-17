@file:OptIn(ExperimentalMaterial3Api::class)

package com.thiago.listamercado.ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiago.listamercado.core.CurrentPrice
import com.thiago.listamercado.core.Money
import com.thiago.listamercado.core.PriceKey
import com.thiago.listamercado.core.Quantity
import com.thiago.listamercado.data.Product
import com.thiago.listamercado.data.Repository
import com.thiago.listamercado.data.ShoppingList
import com.thiago.listamercado.data.today
import com.thiago.listamercado.ui.components.ConfirmDialog
import com.thiago.listamercado.ui.components.LocalRepository
import com.thiago.listamercado.ui.components.PriceText
import com.thiago.listamercado.ui.components.SectionHeader
import com.thiago.listamercado.ui.components.TextInputDialog
import com.thiago.listamercado.ui.components.describeDay
import com.thiago.listamercado.ui.components.formatDayShort
import com.thiago.listamercado.ui.components.toState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class StorePrice(val storeName: String, val price: CurrentPrice)

class ProductDetailViewModel(private val repository: Repository, private val productId: Long) : ViewModel() {
    val product = repository.observeProduct(productId).toState(this, null)
    val history = repository.observeProductHistory(productId).toState(this, emptyList())
    val promotions = repository.observeProductPromotions(productId).toState(this, emptyList())
    val lists = repository.lists.toState(this, emptyList())
    val prices = combine(repository.stores, repository.currentPrices) { stores, prices ->
        stores.mapNotNull { store ->
            prices[PriceKey(productId, store.id)]?.let { StorePrice(store.name, it) }
        }.sortedBy { it.price.priceCents }
    }.toState(this, emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun toggleFavorite() {
        val current = product.value ?: return
        viewModelScope.launch { repository.setFavorite(current.id, !current.favorite) }
    }

    fun rename(name: String) {
        val current = product.value ?: return
        viewModelScope.launch {
            if (!repository.renameProduct(current, name)) _messages.tryEmit("Já existe um produto com esse nome.")
        }
    }

    fun addToList(list: ShoppingList) {
        viewModelScope.launch {
            repository.addProductToList(list.id, productId)
            _messages.tryEmit("Adicionado em \"${list.name}\"")
        }
    }

    fun delete(product: Product, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            onDeleted()
        }
    }
}

@Composable
fun ProductDetailScreen(productId: Long, onBack: () -> Unit) {
    val repository = LocalRepository.current
    val viewModel: ProductDetailViewModel = viewModel { ProductDetailViewModel(repository, productId) }
    val product by viewModel.product.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val promotions by viewModel.promotions.collectAsStateWithLifecycle()
    val prices by viewModel.prices.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    var menuOpen by remember { mutableStateOf(false) }
    var listMenuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val todayDay = today()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(product?.name ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        if (product?.favorite == true) {
                            Icon(Icons.Filled.Star, contentDescription = "Sempre compro", tint = MaterialTheme.colorScheme.secondary)
                        } else {
                            Icon(Icons.Filled.StarBorder, contentDescription = "Marcar como sempre compro")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (lists.isNotEmpty()) {
                            DropdownMenuItem(text = { Text("Adicionar a uma lista") }, onClick = {
                                menuOpen = false
                                listMenuOpen = true
                            })
                        }
                        DropdownMenuItem(text = { Text("Renomear") }, onClick = {
                            menuOpen = false
                            renaming = true
                        })
                        DropdownMenuItem(text = { Text("Excluir produto") }, onClick = {
                            menuOpen = false
                            confirmDelete = true
                        })
                    }
                    DropdownMenu(expanded = listMenuOpen, onDismissRequest = { listMenuOpen = false }) {
                        lists.forEach { list ->
                            DropdownMenuItem(text = { Text(list.name) }, onClick = {
                                listMenuOpen = false
                                viewModel.addToList(list)
                            })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "prices-header") { SectionHeader("Preço atual por mercado") }
            if (prices.isEmpty()) {
                item(key = "no-prices") {
                    Text(
                        "Nenhum preço ainda. Registre uma compra ou uma promoção desse produto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(prices) { entry ->
                val best = entry == prices.first()
                Card(
                    colors = if (best && prices.size > 1) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.storeName, style = MaterialTheme.typography.titleMedium, fontWeight = if (best) FontWeight.Bold else FontWeight.Normal)
                            val until = entry.price.validUntilDay
                            Text(
                                when {
                                    entry.price.isPromotion && until != null -> "Promoção até ${formatDayShort(until)}"
                                    entry.price.isPromotion -> "Promoção"
                                    else -> "Preço de ${describeDay(entry.price.day)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        PriceText(entry.price.priceCents, regularCents = entry.price.regularPriceCents, promo = entry.price.isPromotion)
                    }
                }
            }

            if (history.isNotEmpty()) {
                item(key = "stats") {
                    val cheapest = history.minBy { it.unitPriceCents }
                    val average = history.map { it.unitPriceCents }.average()
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Menor preço pago: ${Money.format(cheapest.unitPriceCents)} em ${cheapest.storeName ?: "?"} (${describeDay(cheapest.date)})",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text("Preço médio pago: ${Money.format(Math.round(average))}", style = MaterialTheme.typography.bodyMedium)
                            Text("Comprado ${history.size} ${if (history.size == 1) "vez" else "vezes"}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (promotions.isNotEmpty()) {
                item(key = "promos-header") { SectionHeader("Promoções") }
                items(promotions, key = { "promo-${it.id}" }) { promo ->
                    val expired = promo.validUntil != null && promo.validUntil < todayDay
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(promo.storeName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    promo.validUntil == null -> "Sem validade"
                                    expired -> "Vencida em ${formatDayShort(promo.validUntil)}"
                                    else -> "Até ${formatDayShort(promo.validUntil)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PriceText(promo.priceCents, regularCents = promo.regularPriceCents, promo = !expired)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }

            if (history.isNotEmpty()) {
                item(key = "history-header") { SectionHeader("Histórico de compras") }
                items(history) { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(row.storeName ?: "Mercado removido", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${describeDay(row.date)} · ${Quantity.format(row.quantity)} × ${Money.format(row.unitPriceCents)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Money.format(row.unitPriceCents), style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "Renomear produto",
            label = "Nome",
            initial = product?.name ?: "",
            onConfirm = viewModel::rename,
            onDismiss = { renaming = false },
        )
    }
    val current = product
    if (confirmDelete && current != null) {
        ConfirmDialog(
            title = "Excluir ${current.name}?",
            message = "O produto sai das listas, das promoções e do histórico de compras.",
            onConfirm = { viewModel.delete(current, onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}
