@file:OptIn(ExperimentalMaterial3Api::class)

package com.thiago.listamercado.ui.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiago.listamercado.core.Money
import com.thiago.listamercado.data.PurchaseSummary
import com.thiago.listamercado.data.Repository
import com.thiago.listamercado.ui.components.EmptyState
import com.thiago.listamercado.ui.components.LocalRepository
import com.thiago.listamercado.ui.components.describeDay
import com.thiago.listamercado.ui.components.toState
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class PurchasesViewModel(repository: Repository) : ViewModel() {
    val purchases = repository.purchaseSummaries.toState(this, emptyList())
}

@Composable
fun PurchasesScreen(onOpenPurchase: (Long) -> Unit, onNewPurchase: () -> Unit) {
    val repository = LocalRepository.current
    val viewModel: PurchasesViewModel = viewModel { PurchasesViewModel(repository) }
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Compras realizadas") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewPurchase,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova compra") },
            )
        },
    ) { padding ->
        if (purchases.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Receipt,
                title = "Nenhuma compra registrada",
                message = "Registre o que você comprou e quanto pagou. Esses preços alimentam a " +
                    "comparação entre mercados. Dica: abra uma lista e toque em \"Registrar compra\".",
                modifier = Modifier.padding(padding),
            )
        } else {
            val months = remember(purchases) { monthlyTotals(purchases) }
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "summary") { MonthSummary(months) }
                items(purchases, key = { it.id }) { purchase ->
                    PurchaseCard(purchase, onClick = { onOpenPurchase(purchase.id) })
                }
            }
        }
    }
}

private data class MonthTotal(val label: String, val totalCents: Long, val count: Int)

private fun monthlyTotals(purchases: List<PurchaseSummary>): List<MonthTotal> {
    val locale = Locale("pt", "BR")
    return purchases
        .groupBy { LocalDate.ofEpochDay(it.date).withDayOfMonth(1) }
        .toSortedMap(compareByDescending { it })
        .map { (month, list) ->
            val name = month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
            MonthTotal("$name/${month.year}", list.sumOf { it.totalCents }, list.size)
        }
        .take(3)
}

@Composable
private fun MonthSummary(months: List<MonthTotal>) {
    if (months.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Gastos por mês", style = MaterialTheme.typography.labelLarge)
            months.forEachIndexed { index, month ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${month.label} · ${month.count} ${if (month.count == 1) "compra" else "compras"}",
                        style = if (index == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Money.format(month.totalCents),
                        style = if (index == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun PurchaseCard(purchase: PurchaseSummary, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(purchase.storeName ?: "Mercado removido", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${describeDay(purchase.date)} · ${purchase.itemCount} ${if (purchase.itemCount == 1) "item" else "itens"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.format(purchase.totalCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
