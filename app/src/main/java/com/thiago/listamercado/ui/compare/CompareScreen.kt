@file:OptIn(ExperimentalMaterial3Api::class)

package com.thiago.listamercado.ui.compare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiago.listamercado.core.BasketComparison
import com.thiago.listamercado.core.BasketItem
import com.thiago.listamercado.core.ItemComparison
import com.thiago.listamercado.core.Money
import com.thiago.listamercado.core.PriceComparator
import com.thiago.listamercado.core.Quantity
import com.thiago.listamercado.core.StoreTotal
import com.thiago.listamercado.data.Product
import com.thiago.listamercado.data.Repository
import com.thiago.listamercado.data.Store
import com.thiago.listamercado.ui.components.EmptyState
import com.thiago.listamercado.ui.components.LocalRepository
import com.thiago.listamercado.ui.components.SectionHeader
import com.thiago.listamercado.ui.components.toState
import com.thiago.listamercado.ui.theme.PromoColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

data class CompareState(
    val basketSize: Int,
    val stores: List<Store>,
    val products: Map<Long, Product>,
    val comparison: BasketComparison?,
)

class CompareViewModel(repository: Repository) : ViewModel() {
    val lists = repository.lists.toState(this, emptyList())

    /** null = produtos marcados como "sempre compro"; senão, o id de uma lista. */
    private val selection = MutableStateFlow<Long?>(null)
    val selected: StateFlow<Long?> = selection.asStateFlow()

    val state = combine(
        selection,
        repository.products,
        repository.allListItems,
        repository.stores,
        repository.currentPrices,
    ) { selected, products, listItems, stores, prices ->
        val basket = if (selected == null) {
            products.filter { it.favorite }.map { BasketItem(it.id, 1.0) }
        } else {
            listItems.filter { it.listId == selected }.map { BasketItem(it.productId, it.quantity) }
        }
        CompareState(
            basketSize = basket.size,
            stores = stores,
            products = products.associateBy { it.id },
            comparison = if (basket.isEmpty() || stores.isEmpty()) null
            else PriceComparator.compare(basket, stores.map { it.id }, prices),
        )
    }.toState(this, null)

    fun select(listId: Long?) {
        selection.value = listId
    }
}

@Composable
fun CompareScreen(onOpenProduct: (Long) -> Unit) {
    val repository = LocalRepository.current
    val viewModel: CompareViewModel = viewModel { CompareViewModel(repository) }
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Onde comprar mais barato") }) }) { padding ->
        Column(Modifier.padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = selected == null,
                        onClick = { viewModel.select(null) },
                        label = { Text("⭐ Sempre compro") },
                    )
                }
                items(lists, key = { it.id }) { list ->
                    FilterChip(
                        selected = selected == list.id,
                        onClick = { viewModel.select(list.id) },
                        label = { Text(list.name) },
                    )
                }
            }
            val current = state
            when {
                current == null -> Unit
                current.stores.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Store,
                    title = "Nenhum mercado cadastrado",
                    message = "Cadastre os mercados onde você compra (em Cadastros) e registre compras ou promoções.",
                )
                current.basketSize == 0 -> EmptyState(
                    icon = Icons.Filled.Star,
                    title = if (selected == null) "Nenhum produto marcado" else "Lista vazia",
                    message = if (selected == null) {
                        "Marque com ⭐ os produtos que você sempre compra (em Cadastros > Produtos). " +
                            "Eles formam a sua \"cesta\" para comparar os mercados."
                    } else {
                        "Adicione itens nessa lista para comparar."
                    },
                )
                current.comparison == null || current.comparison.comparableItems == 0 -> EmptyState(
                    icon = Icons.Filled.Savings,
                    title = "Ainda sem preços",
                    message = "Registre compras ou importe encartes de promoções. " +
                        "Cada preço novo atualiza esta comparação.",
                )
                else -> ComparisonContent(current, current.comparison, onOpenProduct)
            }
        }
    }
}

@Composable
private fun ComparisonContent(state: CompareState, comparison: BasketComparison, onOpenProduct: (Long) -> Unit) {
    val storeNames = state.stores.associate { it.id to it.name }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "ranking-header") { SectionHeader("Ranking dos mercados (${comparison.comparableItems} itens com preço)") }
        val ranked = comparison.ranking.filter { it.pricedItems > 0 }
        items(ranked, key = { "store-${it.storeId}" }) { total ->
            val position = ranked.indexOf(total)
            StoreCard(
                position = position,
                total = total,
                name = storeNames[total.storeId] ?: "?",
                comparable = comparison.comparableItems,
                difference = total.estimatedTotalCents - (ranked.firstOrNull()?.estimatedTotalCents ?: 0),
            )
        }
        if (comparison.splitSavingsCents > 0) {
            item(key = "split") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Comprando cada item no mercado mais barato", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${Money.format(comparison.splitTotalCents)} — economia de ${Money.format(comparison.splitSavingsCents)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item(key = "items-header") { SectionHeader("Preço por produto") }
        val sortedItems = comparison.items.sortedBy { state.products[it.productId]?.name?.lowercase() ?: "" }
        items(sortedItems, key = { "item-${it.productId}" }) { item ->
            ItemPrices(item, state.products[item.productId]?.name ?: "?", storeNames, onClick = { onOpenProduct(item.productId) })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun StoreCard(position: Int, total: StoreTotal, name: String, comparable: Int, difference: Long) {
    val first = position == 0
    Card(
        colors = if (first) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (first) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = "Mais barato", tint = MaterialTheme.colorScheme.secondary)
            } else {
                Text("${position + 1}º", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append("${total.pricedItems} de $comparable itens com preço")
                        if (total.missingItems > 0) append(" · demais pela média")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (total.promoItems > 0) {
                    Text(
                        buildString {
                            append("${total.promoItems} em promoção")
                            if (total.promoSavingsCents > 0) append(" · economiza ${Money.format(total.promoSavingsCents)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = PromoColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(total.estimatedTotalCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!first && difference > 0) {
                    Text("+${Money.format(difference)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ItemPrices(item: ItemComparison, name: String, storeNames: Map<Long, String>, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val quantity = if (item.quantity != 1.0) "${Quantity.format(item.quantity)}× " else ""
            Text(quantity + name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            val best = item.bestPriceCents
            if (best != null) {
                Text(Money.format(best), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }
        if (item.prices.isEmpty()) {
            Text("Sem preço em nenhum mercado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val sorted = item.prices.entries.sortedBy { it.value.priceCents }
            Text(
                sorted.joinToString("  ·  ") { (store, price) ->
                    (storeNames[store] ?: "?") + " " + Money.format(price.priceCents) + if (price.isPromotion) " 🔥" else ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
