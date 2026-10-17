@file:OptIn(ExperimentalMaterial3Api::class)

package com.thiago.listamercado.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thiago.listamercado.core.Money
import com.thiago.listamercado.core.TextNormalizer
import com.thiago.listamercado.data.Product
import com.thiago.listamercado.data.Repository
import com.thiago.listamercado.data.Store
import com.thiago.listamercado.data.today
import com.thiago.listamercado.ui.theme.PromoColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

val LocalRepository = staticCompositionLocalOf<Repository> { error("Repository não configurado") }

fun <T> Flow<T>.toState(viewModel: ViewModel, initial: T): StateFlow<T> =
    stateIn(viewModel.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

private val dayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val shortDayFormatter = DateTimeFormatter.ofPattern("dd/MM")
private const val MILLIS_PER_DAY = 86_400_000L

fun formatDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayFormatter)

fun formatDayShort(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDayFormatter)

fun describeDay(epochDay: Long): String = when (epochDay - today()) {
    0L -> "hoje"
    -1L -> "ontem"
    1L -> "amanhã"
    else -> formatDay(epochDay)
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    confirmText: String = "Salvar",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    val confirm = {
        if (value.isNotBlank()) {
            onConfirm(value.trim())
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = { TextButton(onClick = confirm, enabled = value.isNotBlank()) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Excluir",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Produtos já cadastrados que combinam com o que está sendo digitado. */
fun suggestProducts(query: String, products: List<Product>, exclude: Set<Long> = emptySet(), limit: Int = 5): List<Product> {
    val normalized = TextNormalizer.normalize(query)
    if (normalized.isEmpty()) return emptyList()
    return products
        .filter { it.id !in exclude && it.normalizedName.contains(normalized) }
        .sortedWith(
            compareByDescending<Product> { it.normalizedName.startsWith(normalized) }
                .thenByDescending { it.favorite }
                .thenBy { it.name.length },
        )
        .take(limit)
}

@Composable
fun ProductSuggestions(
    suggestions: List<Product>,
    onPick: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 3.dp,
    ) {
        Column {
            suggestions.forEachIndexed { index, product ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(product) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(product.name, modifier = Modifier.weight(1f))
                    if (product.favorite) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Sempre compro",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StorePicker(
    stores: List<Store>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Mercado",
    isError: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val selected = stores.firstOrNull { it.id == selectedId }
    Box(modifier) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Escolha o mercado") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            isError = isError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable { if (stores.isEmpty()) creating = true else expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            stores.forEach { store ->
                DropdownMenuItem(
                    text = { Text(store.name) },
                    onClick = {
                        onSelect(store.id)
                        expanded = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("+ Novo mercado", fontWeight = FontWeight.Medium) },
                onClick = {
                    expanded = false
                    creating = true
                },
            )
        }
    }
    if (creating) {
        TextInputDialog(
            title = "Novo mercado",
            label = "Nome do mercado",
            onConfirm = onCreate,
            onDismiss = { creating = false },
        )
    }
}

@Composable
fun DateField(
    label: String,
    epochDay: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
    placeholder: String = "",
) {
    var picking by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            OutlinedTextField(
                value = epochDay?.let { formatDay(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.matchParentSize().clickable { picking = true })
        }
        if (allowClear && epochDay != null) {
            IconButton(onClick = { onChange(null) }) {
                Icon(Icons.Filled.Clear, contentDescription = "Limpar data")
            }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (epochDay ?: today()) * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Math.floorDiv(it, MILLIS_PER_DAY)) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Preço com o valor "de" riscado e o percentual de desconto, quando houver. */
@Composable
fun PriceText(
    priceCents: Long,
    modifier: Modifier = Modifier,
    regularCents: Long? = null,
    promo: Boolean = false,
    big: Boolean = false,
) {
    val discount = Money.discountPercent(regularCents, priceCents)
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            Money.format(priceCents),
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (promo || discount != null) PromoColor else MaterialTheme.colorScheme.onSurface,
        )
        if (regularCents != null && discount != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Money.format(regularCents),
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "-$discount%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = PromoColor,
                )
            }
        }
    }
}

/** Campo de texto para valores em reais. */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) },
        prefix = { Text("R$ ") },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier,
    )
}
