@file:OptIn(ExperimentalMaterial3Api::class)

package com.thiago.listamercado.ui.offers

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiago.listamercado.core.Money
import com.thiago.listamercado.data.Promotion
import com.thiago.listamercado.data.PromotionRow
import com.thiago.listamercado.data.Repository
import com.thiago.listamercado.data.today
import com.thiago.listamercado.ui.components.ConfirmDialog
import com.thiago.listamercado.ui.components.DateField
import com.thiago.listamercado.ui.components.EmptyState
import com.thiago.listamercado.ui.components.LocalRepository
import com.thiago.listamercado.ui.components.MoneyField
import com.thiago.listamercado.ui.components.PriceText
import com.thiago.listamercado.ui.components.ProductSuggestions
import com.thiago.listamercado.ui.components.StorePicker
import com.thiago.listamercado.ui.components.formatDayShort
import com.thiago.listamercado.ui.components.suggestProducts
import com.thiago.listamercado.ui.components.toState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class OffersViewModel(private val repository: Repository) : ViewModel() {
    val promotions = repository.promotionRows.toState(this, emptyList())
    val stores = repository.stores.toState(this, emptyList())
    val products = repository.products.toState(this, emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun save(id: Long, productName: String, storeId: Long, priceCents: Long, regularCents: Long?, validUntil: Long?) {
        viewModelScope.launch {
            val productId = repository.findOrCreateProduct(productName) ?: return@launch
            val old = if (id > 0) repository.getPromotion(id) else null
            repository.savePromotion(
                Promotion(
                    id = id,
                    productId = productId,
                    storeId = storeId,
                    priceCents = priceCents,
                    regularPriceCents = regularCents?.takeIf { it > priceCents },
                    validUntil = validUntil,
                    source = old?.source ?: Promotion.SOURCE_MANUAL,
                    createdAt = old?.createdAt ?: System.currentTimeMillis(),
                ),
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deletePromotion(id) }
    }

    fun createStore(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { repository.addStore(name)?.let(onCreated) }
    }

    fun deleteExpired() {
        viewModelScope.launch {
            val removed = repository.deleteExpiredPromotions()
            _messages.tryEmit(if (removed == 0) "Nenhuma promoção vencida" else "$removed promoções vencidas removidas")
        }
    }
}

@Composable
fun OffersScreen(onImport: () -> Unit, onOpenProduct: (Long) -> Unit) {
    val repository = LocalRepository.current
    val viewModel: OffersViewModel = viewModel { OffersViewModel(repository) }
    val promotions by viewModel.promotions.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()

    var storeFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var onlyMine by rememberSaveable { mutableStateOf(false) }
    var showExpired by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PromotionRow?>(null) }
    var creating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val todayDay = today()
    val visible = promotions.filter { promo ->
        (storeFilter == null || promo.storeId == storeFilter) &&
            (!onlyMine || promo.favorite) &&
            (showExpired || promo.validUntil == null || promo.validUntil >= todayDay)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Promoções e preços") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Apagar promoções vencidas") },
                            onClick = {
                                menuOpen = false
                                viewModel.deleteExpired()
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Adicionar promoção")
                }
                Spacer(Modifier.height(12.dp))
                ExtendedFloatingActionButton(
                    onClick = onImport,
                    icon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                    text = { Text("Importar encarte") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = onlyMine,
                        onClick = { onlyMine = !onlyMine },
                        label = { Text("⭐ Sempre compro") },
                    )
                }
                item {
                    FilterChip(
                        selected = showExpired,
                        onClick = { showExpired = !showExpired },
                        label = { Text("Mostrar vencidas") },
                    )
                }
                item {
                    FilterChip(selected = storeFilter == null, onClick = { storeFilter = null }, label = { Text("Todos os mercados") })
                }
                items(stores, key = { it.id }) { store ->
                    FilterChip(
                        selected = storeFilter == store.id,
                        onClick = { storeFilter = if (storeFilter == store.id) null else store.id },
                        label = { Text(store.name) },
                    )
                }
            }
            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.LocalOffer,
                    title = if (promotions.isEmpty()) "Nenhuma promoção cadastrada" else "Nada com esses filtros",
                    message = "Toque em \"Importar encarte\" para ler um PDF ou foto de ofertas do mercado — " +
                        "ou compartilhe o PDF direto do WhatsApp para o Lista Mercado. " +
                        "Também dá para adicionar promoções e preços à mão no botão +.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.id }) { promo ->
                        PromotionCard(promo, todayDay, onClick = { editing = promo }, onOpenProduct = { onOpenProduct(promo.productId) })
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        PromotionDialog(
            initial = editing,
            viewModel = viewModel,
            defaultStoreId = storeFilter,
            onOpenProduct = onOpenProduct,
            onDismiss = {
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun PromotionCard(promo: PromotionRow, todayDay: Long, onClick: () -> Unit, onOpenProduct: () -> Unit) {
    val expired = promo.validUntil != null && promo.validUntil < todayDay
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (promo.favorite) {
                        Icon(Icons.Filled.Star, contentDescription = "Sempre compro", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(promo.productName, style = MaterialTheme.typography.titleMedium)
                }
                Text(promo.storeName, style = MaterialTheme.typography.bodyMedium)
                val validity = when {
                    promo.validUntil == null -> "Preço informado (sem validade)"
                    expired -> "Vencida em ${formatDayShort(promo.validUntil)}"
                    promo.validUntil == todayDay -> "Válida só hoje"
                    else -> "Válida até ${formatDayShort(promo.validUntil)}"
                }
                Text(
                    validity,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenProduct, contentPadding = PaddingValues(0.dp)) { Text("Comparar preços") }
            }
            PriceText(promo.priceCents, regularCents = promo.regularPriceCents, promo = promo.validUntil != null && !expired, big = true)
        }
    }
}

@Composable
private fun PromotionDialog(
    initial: PromotionRow?,
    viewModel: OffersViewModel,
    defaultStoreId: Long?,
    onOpenProduct: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    var productName by rememberSaveable { mutableStateOf(initial?.productName ?: "") }
    var storeId by rememberSaveable { mutableStateOf(initial?.storeId ?: defaultStoreId) }
    var price by rememberSaveable { mutableStateOf(initial?.priceCents?.let { Money.formatInput(it) } ?: "") }
    var regular by rememberSaveable { mutableStateOf(initial?.regularPriceCents?.let { Money.formatInput(it) } ?: "") }
    var validUntil by rememberSaveable { mutableStateOf(if (initial == null) today() + 6 else initial.validUntil) }
    var showErrors by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var nameFocused by remember { mutableStateOf(initial == null) }

    val parsedPrice = Money.parse(price)
    val parsedRegular = if (regular.isBlank()) null else Money.parse(regular)
    val suggestions = remember(productName, products, nameFocused) {
        if (!nameFocused) emptyList()
        else suggestProducts(productName, products).filter { it.name != productName }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova promoção / preço" else "Editar promoção") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = productName,
                    onValueChange = {
                        productName = it
                        nameFocused = true
                    },
                    label = { Text("Produto") },
                    singleLine = true,
                    isError = showErrors && productName.isBlank(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                ProductSuggestions(suggestions, onPick = {
                    productName = it.name
                    nameFocused = false
                })
                StorePicker(
                    stores = stores,
                    selectedId = storeId,
                    onSelect = { storeId = it },
                    onCreate = { name -> viewModel.createStore(name) { storeId = it } },
                    isError = showErrors && storeId == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(
                    value = price,
                    onValueChange = { price = it },
                    label = "Preço da promoção",
                    isError = showErrors && parsedPrice == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(
                    value = regular,
                    onValueChange = { regular = it },
                    label = "Preço normal (opcional)",
                    isError = regular.isNotBlank() && parsedRegular == null,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = "Válida até",
                    epochDay = validUntil,
                    onChange = { validUntil = it },
                    allowClear = true,
                    placeholder = "Sem validade",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Sem validade, o valor é tratado como o preço atual do produto nesse mercado até ser atualizado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (initial != null) {
                    Row {
                        TextButton(onClick = { confirmDelete = true }) { Text("Excluir") }
                        TextButton(onClick = {
                            onDismiss()
                            onOpenProduct(initial.productId)
                        }) { Text("Ver produto") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showErrors = true
                val store = storeId
                if (productName.isNotBlank() && store != null && parsedPrice != null) {
                    viewModel.save(initial?.id ?: 0L, productName, store, parsedPrice, parsedRegular, validUntil)
                    onDismiss()
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (confirmDelete && initial != null) {
        ConfirmDialog(
            title = "Excluir promoção?",
            message = "${initial.productName} em ${initial.storeName}",
            onConfirm = {
                viewModel.delete(initial.id)
                onDismiss()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
