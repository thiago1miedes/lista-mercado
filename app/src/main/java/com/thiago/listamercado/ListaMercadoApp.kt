package com.thiago.listamercado

import android.app.Application
import android.net.Uri
import com.thiago.listamercado.data.AppDatabase
import com.thiago.listamercado.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ListaMercadoApp : Application() {
    val repository: Repository by lazy { Repository(AppDatabase.create(this)) }
}

/** Arquivo recebido de outro app (ex.: encarte compartilhado pelo WhatsApp). */
data class SharedFile(val uri: Uri, val mimeType: String?)

object SharedFiles {
    private val pending = MutableStateFlow<SharedFile?>(null)
    val incoming: StateFlow<SharedFile?> = pending.asStateFlow()

    fun offer(file: SharedFile) {
        pending.value = file
    }

    fun consume(): SharedFile? {
        val file = pending.value
        pending.value = null
        return file
    }
}
