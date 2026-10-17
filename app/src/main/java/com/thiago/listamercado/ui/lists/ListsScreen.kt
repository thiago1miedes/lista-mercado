@file:OptIn(ExperimentalMaterial3Api::class)

package com.thiago.listamercado.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiago.listamercado.data.ListSummary
import com.thiago.listamercado.data.Repository
import com.thiago.listamercado.ui.components.EmptyState
import com.thiago.listamercado.ui.components.LocalRepository
import com.thiago.listamercado.ui.components.TextInputDialog
import com.thiago.listamercado.ui.components.toState
import kotlinx.coroutines.launch

class ListsViewModel(private val repository: Repository) : ViewModel() {
    val lists = repository.listSummaries.toState(this, emptyList())

    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.createList(name)) }
    }
}

@Composable
fun ListsScreen(onOpenList: (Long) -> Unit) {
    val repository = LocalRepository.current
    val viewModel: ListsViewModel = viewModel { ListsViewModel(repository) }
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Listas de compras") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova lista") },
            )
        },
    ) { padding ->
        if (lists.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Checklist,
                title = "Nenhuma lista ainda",
                message = "Crie uma lista, adicione os itens e vá marcando enquanto compra. " +
                    "O app mostra em qual mercado sua lista sai mais barata.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(lists, key = { it.id }) { list -> ListCard(list, onClick = { onOpenList(list.id) }) }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "Nova lista",
            label = "Nome da lista",
            initial = "Compras da semana",
            confirmText = "Criar",
            onConfirm = { name -> viewModel.create(name, onOpenList) },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun ListCard(list: ListSummary, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(list.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${list.checkedCount}/${list.itemCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (list.itemCount > 0) {
                LinearProgressIndicator(
                    progress = { list.checkedCount.toFloat() / list.itemCount },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "Lista vazia",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
