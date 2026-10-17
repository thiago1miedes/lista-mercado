@file:OptIn(ExperimentalMaterial3Api::class)

package com.thiago.listamercado.ui.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiago.listamercado.core.CurrentPrice
import com.thiago.listamercado.core.Money
import com.thiago.listamercado.core.PriceKey
import com.thiago.listamercado.core.Quantity
import com.thiago.listamercado.data.PurchaseItemInput
import com.thiago.listamercado.data.Repository
import com.thiago.listamercado.data.today
import com.thiago.listamercado.ui.components.ConfirmDialog
import com.thiago.listamercado.ui.components.DateField
import com.thiago.listamercado.ui.components.MoneyField
import com.thiago.listamercado.ui.components.ProductSuggestions
import com.thiago.listamercado.ui.components.SectionHeader
import com.thiago.listamercado.ui.components.StorePicker
import com.thiago.listamercado.ui.components.LocalRepository
import com.thiago.listamercado.ui.components.suggestProducts
import com.thiago.listamercado.ui.components.toState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DraftItem(
    val key: Long,
    val productId: Long,
    val name: String,
    val quantity: String,
    val price: String,
) {
    val parsedQuantity: Double? get() = Quantity.parse(quantity)
    val parsedPrice: Long? get() = Money.parse(price)
    val lineTotal: Long? get() {
        val q = parsedQuantity ?: return null
        val p = parsedPrice ?: return null
        return Money.lineTotal(q, p)
    }
}

class PurchaseEditViewModel(
    private val repository: Repository,
    private val purchaseId: Long,
    private val fromListId: Long,
) : ViewModel() {
    val stores = repository.stores.toState(this, emptyList())
    val products = repository.products.toState(this, emptyList())

    var storeId by mutableStateOf<Long?>(null)
        private set
    var day by mutableStateOf(today())
    val items = mutableStateListOf<DraftItem>()
    var showErrors by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var uncheckListAfterSave by mutableStateOf(true)

    val isEditing: Boolean get() = purchaseId > 0
    val fromList: Boolean get() = fromListId > 0
    val total: Long get() = items.sumOf { it.lineTotal ?: 0L }

    private var nextKey = 1L
    private var prices: Map<PriceKey, CurrentPrice> = emptyMap()

    init {
        viewModelScope.launch {
            prices = repository.currentPrices.first()
            if (purchaseId > 0) {
                repository.getPurchase(purchaseId)?.let {
                    storeId = it.storeId
                    day = it.date
                }
                repository.getPurchaseItems(purchaseId).forEach {
                    items += DraftItem(nextKey++, it.productId, it.productName, Quantity.format(it.quantity), Money.formatInput(it.unitPriceCents))
                }
            } else if (fromListId > 0) {
                val listItems = repository.getListItems(fromListId)
                val chosen = listItems.filter { it.checked }.ifEmpty { listItems }
                chosen.forEach {
                    items += DraftItem(nextKey++, it.productId, it.productName, Quantity.format(it.quantity), "")
                }
            }
            fillKnownPrices()
        }
    }

    fun selectStore(id: Long) {
        storeId = id
        fillKnownPrices()
    }

    fun createStore(name: String) {
        viewModelScope.launch { repository.addStore(name)?.let { selectStore(it) } }
    }

    /** Preenche os preços vazios com o último preço conhecido no mercado escolhido. */
    private fun fillKnownPrices() {
        val store = storeId ?: return
        for (index in items.indices) {
            val item = items[index]
            if (item.price.isNotBlank()) continue
            val known = prices[PriceKey(item.productId, store)] ?: continue
            items[index] = item.copy(price = Money.formatInput(known.priceCents))
        }
    }

    fun addItem(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.findOrCreateProduct(name) ?: return@launch
            addProduct(id, name.trim())
        }
    }

    fun addProduct(productId: Long, name: String) {
        if (items.any { it.productId == productId }) return
        val known = storeId?.let { prices[PriceKey(productId, it)] }
        items += DraftItem(nextKey++, productId, name, "1", known?.let { Money.formatInput(it.priceCents) } ?: "")
    }

    fun updateQuantity(key: Long, value: String) = update(key) { it.copy(quantity = value) }

    fun updatePrice(key: Long, value: String) = update(key) { it.copy(price = value) }

    fun remove(key: Long) {
        items.removeAll { it.key == key }
    }

    private fun update(key: Long, change: (DraftItem) -> DraftItem) {
        val index = items.indexOfFirst { it.key == key }
        if (index >= 0) items[index] = change(items[index])
    }

    fun save(onSaved: () -> Unit) {
        showErrors = true
        val store = storeId
        error = when {
            store == null -> "Escolha o mercado."
            items.isEmpty() -> "Adicione pelo menos um item."
            items.any { it.parsedQuantity == null || it.parsedPrice == null } -> "Confira a quantidade e o preço dos itens em vermelho."
            else -> null
        }
        if (error != null || store == null) return
        val inputs = items.map { PurchaseItemInput(it.productId, it.parsedQuantity!!, it.parsedPrice!!) }
        viewModelScope.launch {
            repository.savePurchase(purchaseId, store, day, inputs)
            if (fromListId > 0 && uncheckListAfterSave) repository.uncheckAll(fromListId)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deletePurchase(purchaseId)
            onDeleted()
        }
    }
}

@Composable
fun PurchaseEditScreen(purchaseId: Long, fromListId: Long, onDone: () -> Unit) {
    val repository = LocalRepository.current
    val viewModel: PurchaseEditViewModel = viewModel { PurchaseEditViewModel(repository, purchaseId, fromListId) }
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val inPurchase = viewModel.items.map { it.productId }.toSet()
    val suggestions = remember(query, products, inPurchase) { suggestProducts(query, products, inPurchase) }

    fun submit() {
        viewModel.addItem(query)
        query = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Editar compra" else "Nova compra") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (viewModel.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Excluir compra")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    viewModel.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Total", style = MaterialTheme.typography.labelMedium)
                            Text(Money.format(viewModel.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = { viewModel.save(onDone) }) { Text("Salvar compra") }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorePicker(
                        stores = stores,
                        selectedId = viewModel.storeId,
                        onSelect = viewModel::selectStore,
                        onCreate = viewModel::createStore,
                        isError = viewModel.showErrors && viewModel.storeId == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DateField(
                        label = "Data da compra",
                        epochDay = viewModel.day,
                        onChange = { viewModel.day = it ?: today() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (viewModel.fromList) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Desmarcar os itens da lista ao salvar",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = viewModel.uncheckListAfterSave,
                                onCheckedChange = { viewModel.uncheckListAfterSave = it },
                            )
                        }
                    }
                }
            }
            item(key = "items-header") { SectionHeader("Itens (${viewModel.items.size})") }
            items(viewModel.items, key = { it.key }) { item ->
                DraftItemCard(
                    item = item,
                    showErrors = viewModel.showErrors,
                    onQuantity = { viewModel.updateQuantity(item.key, it) },
                    onPrice = { viewModel.updatePrice(item.key, it) },
                    onRemove = { viewModel.remove(item.key) },
                )
            }
            item(key = "add") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Adicionar item") },
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
                            viewModel.addProduct(it.id, it.name)
                            query = ""
                        })
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Excluir compra?",
            message = "Os preços desta compra deixam de contar na comparação.",
            onConfirm = { viewModel.delete(onDone) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DraftItemCard(
    item: DraftItem,
    showErrors: Boolean,
    onQuantity: (String) -> Unit,
    onPrice: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remover item") }
            }
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.quantity,
                    onValueChange = onQuantity,
                    label = { Text("Qtd.") },
                    singleLine = true,
                    isError = showErrors && item.parsedQuantity == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.width(88.dp),
                )
                Spacer(Modifier.width(8.dp))
                MoneyField(
                    value = item.price,
                    onValueChange = onPrice,
                    label = "Preço unitário",
                    isError = showErrors && item.parsedPrice == null,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    item.lineTotal?.let { Money.format(it) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
