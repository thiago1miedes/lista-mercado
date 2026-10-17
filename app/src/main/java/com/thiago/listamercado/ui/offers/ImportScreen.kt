@file:OptIn(ExperimentalMaterial3Api::class)

package com.thiago.listamercado.ui.offers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiago.listamercado.SharedFile
import com.thiago.listamercado.SharedFiles
import com.thiago.listamercado.core.CatalogItem
import com.thiago.listamercado.core.Money
import com.thiago.listamercado.core.OfferParser
import com.thiago.listamercado.core.ProductMatcher
import com.thiago.listamercado.core.TextNormalizer
import com.thiago.listamercado.data.ImportedOffer
import com.thiago.listamercado.data.Product
import com.thiago.listamercado.data.Promotion
import com.thiago.listamercado.data.Repository
import com.thiago.listamercado.data.today
import com.thiago.listamercado.importer.ExtractionResult
import com.thiago.listamercado.importer.OfferExtractor
import com.thiago.listamercado.ui.components.DateField
import com.thiago.listamercado.ui.components.LocalRepository
import com.thiago.listamercado.ui.components.MoneyField
import com.thiago.listamercado.ui.components.PriceText
import com.thiago.listamercado.ui.components.ProductSuggestions
import com.thiago.listamercado.ui.components.StorePicker
import com.thiago.listamercado.ui.components.suggestProducts
import com.thiago.listamercado.ui.components.toState
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Uma oferta lida do encarte, pronta para revisão. */
data class Candidate(
    val key: Int,
    val offerName: String,
    val priceCents: Long,
    val regularCents: Long?,
    val unit: String?,
    /** Nome do produto em que a promoção será salva. */
    val targetName: String,
    /** Produto já cadastrado correspondente a [targetName], ou null para criar um novo. */
    val productId: Long?,
    val favorite: Boolean,
    val selected: Boolean,
)

sealed interface ImportPhase {
    data object Choose : ImportPhase
    data class Working(val message: String) : ImportPhase
    data object Review : ImportPhase
    data class Failed(val message: String) : ImportPhase
}

class ImportViewModel(
    private val repository: Repository,
    private val extractor: OfferExtractor,
) : ViewModel() {
    val stores = repository.stores.toState(this, emptyList())
    val products = repository.products.toState(this, emptyList())

    var phase by mutableStateOf<ImportPhase>(ImportPhase.Choose)
        private set
    val candidates = mutableStateListOf<Candidate>()
    var storeId by mutableStateOf<Long?>(null)
    var validUntil by mutableStateOf<Long?>(null)
    var summary by mutableStateOf("")
        private set
    var showErrors by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set

    private var source = Promotion.SOURCE_PDF
    private var job: Job? = null

    fun handleShared(file: SharedFile) {
        if (file.mimeType?.startsWith("image/") == true) importImage(file.uri) else importPdf(file.uri, forceOcr = false)
    }

    fun importPdf(uri: Uri, forceOcr: Boolean) =
        startExtraction(Promotion.SOURCE_PDF) { progress -> extractor.fromPdf(uri, forceOcr, progress) }

    fun importImage(uri: Uri) = startExtraction(Promotion.SOURCE_IMAGE) { progress -> extractor.fromImage(uri, progress) }

    fun importText(text: String) = startExtraction(Promotion.SOURCE_TEXT) { extractor.fromText(text) }

    fun cancel() {
        job?.cancel()
        phase = ImportPhase.Choose
    }

    fun restart() {
        candidates.clear()
        phase = ImportPhase.Choose
    }

    private fun startExtraction(source: String, block: suspend (progress: (String) -> Unit) -> ExtractionResult) {
        job?.cancel()
        this.source = source
        phase = ImportPhase.Working("Abrindo o arquivo…")
        job = viewModelScope.launch {
            try {
                val result = block { message -> viewModelScope.launch { if (phase is ImportPhase.Working) phase = ImportPhase.Working(message) } }
                review(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                phase = ImportPhase.Failed(
                    "Não consegui ler esse arquivo (${e.message ?: e.javaClass.simpleName}). " +
                        "Se for um PDF protegido por senha, tente uma captura de tela do encarte.",
                )
            }
        }
    }

    private suspend fun review(result: ExtractionResult) {
        val catalog = repository.getProducts()
        val matcher = ProductMatcher(catalog.map { CatalogItem(it.id, it.name, it.favorite) })
        val list = result.offers.mapIndexed { index, offer ->
            val match = matcher.bestMatch(offer.name)?.item
            Candidate(
                key = index,
                offerName = offer.name,
                priceCents = offer.priceCents,
                regularCents = offer.regularPriceCents,
                unit = offer.unit,
                targetName = match?.name ?: offer.name,
                productId = match?.id,
                favorite = match?.favorite == true,
                selected = match != null,
            )
        }.sortedWith(
            compareByDescending<Candidate> { it.productId != null }
                .thenByDescending { it.favorite }
                .thenBy { it.offerName.lowercase() },
        )
        candidates.clear()
        candidates.addAll(list)

        validUntil = OfferParser.detectValidity(result.text, LocalDate.now())?.toEpochDay() ?: validUntil ?: (today() + 6)
        if (storeId == null) {
            val text = " " + TextNormalizer.normalize(result.text) + " "
            repository.getStores().firstOrNull { store ->
                val name = TextNormalizer.normalize(store.name)
                name.length >= 3 && text.contains(" $name ")
            }?.let { storeId = it.id }
        }
        val mine = list.count { it.productId != null }
        summary = buildString {
            append("${list.size} ofertas encontradas")
            if (result.pages > 0) append(" em ${result.pages} ${if (result.pages == 1) "página" else "páginas"}")
            if (result.usedOcr) append(" (leitura por imagem)")
            append(". $mine ${if (mine == 1) "é" else "são"} de produtos que você já tem.")
        }
        phase = if (list.isEmpty()) {
            ImportPhase.Failed(
                "Não encontrei preços nesse encarte. Se for um PDF, tente ligar \"Forçar leitura por imagem\". " +
                    "Fotos nítidas e bem iluminadas funcionam melhor.",
            )
        } else {
            ImportPhase.Review
        }
    }

    fun toggle(key: Int) = update(key) { it.copy(selected = !it.selected) }

    fun setAll(keys: Set<Int>, selected: Boolean) {
        for (index in candidates.indices) {
            if (candidates[index].key in keys) candidates[index] = candidates[index].copy(selected = selected)
        }
    }

    fun edit(key: Int, targetName: String, priceCents: Long, regularCents: Long?) {
        viewModelScope.launch {
            val normalized = TextNormalizer.normalize(targetName)
            val existing = repository.getProducts().firstOrNull { it.normalizedName == normalized }
            update(key) {
                it.copy(
                    targetName = targetName.trim(),
                    productId = existing?.id,
                    favorite = existing?.favorite == true,
                    priceCents = priceCents,
                    regularCents = regularCents,
                    selected = true,
                )
            }
        }
    }

    private fun update(key: Int, change: (Candidate) -> Candidate) {
        val index = candidates.indexOfFirst { it.key == key }
        if (index >= 0) candidates[index] = change(candidates[index])
    }

    fun createStore(name: String) {
        viewModelScope.launch { repository.addStore(name)?.let { storeId = it } }
    }

    fun save(onSaved: (Int) -> Unit) {
        showErrors = true
        val store = storeId ?: return
        val chosen = candidates.filter { it.selected }
        if (chosen.isEmpty() || saving) return
        saving = true
        viewModelScope.launch {
            try {
                val saved = repository.importPromotions(
                    storeId = store,
                    validUntil = validUntil,
                    source = source,
                    offers = chosen.map { ImportedOffer(it.productId, it.targetName, it.priceCents, it.regularCents) },
                )
                onSaved(saved)
            } finally {
                saving = false
            }
        }
    }
}

@Composable
fun ImportScreen(onDone: () -> Unit) {
    val repository = LocalRepository.current
    val context = LocalContext.current.applicationContext
    val viewModel: ImportViewModel = viewModel { ImportViewModel(repository, OfferExtractor(context)) }
    val incoming by SharedFiles.incoming.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        if (incoming != null) SharedFiles.consume()?.let(viewModel::handleShared)
    }

    var forceOcr by rememberSaveable { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var savedCount by remember { mutableStateOf<Int?>(null) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPdf(uri, forceOcr)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importImage(uri)
    }

    val phase = viewModel.phase
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar encarte") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
        bottomBar = {
            if (phase is ImportPhase.Review) {
                val count = viewModel.candidates.count { it.selected }
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        if (viewModel.showErrors && viewModel.storeId == null) {
                            Text("Escolha o mercado do encarte.", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { viewModel.save { savedCount = it } },
                            enabled = count > 0 && !viewModel.saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (count == 1) "Salvar 1 promoção" else "Salvar $count promoções")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (phase) {
                ImportPhase.Choose -> ChooseSource(
                    forceOcr = forceOcr,
                    onForceOcr = { forceOcr = it },
                    onPdf = { pickPdf.launch(arrayOf("application/pdf")) },
                    onImage = { pickImage.launch(arrayOf("image/*")) },
                    onText = { pasting = true },
                )
                is ImportPhase.Working -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(phase.message, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = viewModel::cancel) { Text("Cancelar") }
                }
                is ImportPhase.Failed -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.SearchOff, contentDescription = null, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(phase.message, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::restart) { Text("Tentar de novo") }
                }
                ImportPhase.Review -> ReviewOffers(viewModel)
            }
        }
    }

    if (pasting) {
        PasteTextDialog(
            onConfirm = viewModel::importText,
            onDismiss = { pasting = false },
        )
    }
    savedCount?.let { count ->
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Pronto!") },
            text = {
                Text(
                    when (count) {
                        0 -> "Essas promoções já estavam cadastradas."
                        1 -> "1 promoção salva. Ela já entra na comparação de preços."
                        else -> "$count promoções salvas. Elas já entram na comparação de preços."
                    },
                )
            },
            confirmButton = { TextButton(onClick = onDone) { Text("OK") } },
        )
    }
}

@Composable
private fun ChooseSource(
    forceOcr: Boolean,
    onForceOcr: (Boolean) -> Unit,
    onPdf: () -> Unit,
    onImage: () -> Unit,
    onText: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Leia as ofertas de um encarte do mercado. O app encontra os preços, reconhece os produtos " +
                "que você já compra e você confirma o que quer salvar.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SourceButton(Icons.Filled.PictureAsPdf, "Escolher PDF", "Encarte/tabloide em PDF", onPdf)
        SourceButton(Icons.Filled.Image, "Escolher imagem", "Foto ou print do encarte", onImage)
        SourceButton(Icons.Filled.ContentPaste, "Colar texto", "Ofertas copiadas de um site ou mensagem", onText)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Forçar leitura por imagem (OCR)", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Use se o PDF for escaneado ou se o texto vier embaralhado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = forceOcr, onCheckedChange = onForceOcr)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Text(
                "Dica: recebeu o encarte no WhatsApp? Toque em compartilhar no PDF e escolha \"Lista Mercado\". " +
                    "Tudo é lido no próprio celular, sem internet.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun SourceButton(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReviewOffers(viewModel: ImportViewModel) {
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    var onlyMine by rememberSaveable { mutableStateOf(viewModel.candidates.any { it.productId != null }) }
    var editing by remember { mutableStateOf<Candidate?>(null) }
    val visible = viewModel.candidates.filter { !onlyMine || it.productId != null }
    val mineCount = viewModel.candidates.count { it.productId != null }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "header") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(viewModel.summary, style = MaterialTheme.typography.bodyMedium)
                StorePicker(
                    stores = stores,
                    selectedId = viewModel.storeId,
                    onSelect = { viewModel.storeId = it },
                    onCreate = viewModel::createStore,
                    label = "Mercado do encarte",
                    isError = viewModel.showErrors && viewModel.storeId == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = "Ofertas válidas até",
                    epochDay = viewModel.validUntil,
                    onChange = { viewModel.validUntil = it },
                    allowClear = true,
                    placeholder = "Sem validade",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = onlyMine, onClick = { onlyMine = true }, label = { Text("Meus produtos ($mineCount)") })
                    FilterChip(selected = !onlyMine, onClick = { onlyMine = false }, label = { Text("Todas (${viewModel.candidates.size})") })
                }
                Row {
                    TextButton(onClick = { viewModel.setAll(visible.map { it.key }.toSet(), true) }) { Text("Marcar todas") }
                    TextButton(onClick = { viewModel.setAll(visible.map { it.key }.toSet(), false) }) { Text("Desmarcar") }
                }
                if (onlyMine && mineCount == 0) {
                    Text(
                        "Nenhuma oferta bateu com os produtos que você já cadastrou. Veja \"Todas\" e marque as que interessam — " +
                            "elas viram produtos novos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
        items(visible, key = { it.key }) { candidate ->
            CandidateRow(candidate, onToggle = { viewModel.toggle(candidate.key) }, onEdit = { editing = candidate })
            HorizontalDivider()
        }
    }

    editing?.let { candidate ->
        val products by viewModel.products.collectAsStateWithLifecycle()
        EditCandidateDialog(
            candidate = candidate,
            products = products,
            onSave = { name, price, regular -> viewModel.edit(candidate.key, name, price, regular) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CandidateRow(candidate: Candidate, onToggle: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = candidate.selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(candidate.offerName, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (candidate.favorite) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    if (candidate.productId != null) "→ ${candidate.targetName}" else "→ novo produto: ${candidate.targetName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (candidate.productId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (candidate.productId != null) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            PriceText(candidate.priceCents, regularCents = candidate.regularCents, promo = true)
            candidate.unit?.let { Text("/$it", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun EditCandidateDialog(
    candidate: Candidate,
    products: List<Product>,
    onSave: (name: String, price: Long, regular: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(candidate.key) { mutableStateOf(candidate.targetName) }
    var price by remember(candidate.key) { mutableStateOf(Money.formatInput(candidate.priceCents)) }
    var regular by remember(candidate.key) { mutableStateOf(candidate.regularCents?.let { Money.formatInput(it) } ?: "") }
    var typing by remember { mutableStateOf(false) }
    val parsedPrice = Money.parse(price)
    val parsedRegular = if (regular.isBlank()) null else Money.parse(regular)
    val normalized = TextNormalizer.normalize(name)
    val existing = products.firstOrNull { it.normalizedName == normalized }
    val suggestions = remember(name, products, typing) {
        if (!typing) emptyList() else suggestProducts(name, products).filter { it.id != existing?.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Revisar oferta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No encarte: ${candidate.offerName}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        typing = true
                    },
                    label = { Text("Salvar no produto") },
                    supportingText = {
                        Text(if (existing != null) "Produto já cadastrado" else "Será criado um produto novo")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                ProductSuggestions(suggestions, onPick = {
                    name = it.name
                    typing = false
                })
                MoneyField(
                    value = price,
                    onValueChange = { price = it },
                    label = "Preço da oferta",
                    isError = parsedPrice == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(
                    value = regular,
                    onValueChange = { regular = it },
                    label = "Preço normal (opcional)",
                    isError = regular.isNotBlank() && parsedRegular == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && parsedPrice != null,
                onClick = {
                    if (parsedPrice != null) onSave(name, parsedPrice, parsedRegular)
                    onDismiss()
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PasteTextDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colar ofertas") },
        text = {
            Column {
                Text(
                    "Uma oferta por linha funciona melhor, ex.: \"Arroz Tio João 5kg R$ 24,90\".",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    onConfirm(text)
                    onDismiss()
                },
            ) { Text("Ler ofertas") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
