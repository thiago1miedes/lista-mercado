package com.thiago.listamercado

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.thiago.listamercado.data.AppDatabase
import com.thiago.listamercado.data.Repository
import com.thiago.listamercado.ui.AppRoot
import com.thiago.listamercado.ui.components.LocalRepository
import com.thiago.listamercado.ui.theme.ListaMercadoTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SmokeTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: Repository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = Repository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun appOpensWithExistingList() {
        runBlocking {
            val list = repository.createList("Compras da semana")
            repository.addItemToList(list, "Arroz")
            repository.addItemToList(list, "Feijão")
        }
        compose.setContent {
            ListaMercadoTheme {
                CompositionLocalProvider(LocalRepository provides repository) { AppRoot() }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Compras da semana").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/smoke_lists.png")
    }
}
