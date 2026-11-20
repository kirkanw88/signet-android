package com.signet.client

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.signet.client.data.Repository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import java.util.UUID

/** Acceptance tests for Universal only. Production subscriptions and servers are never used. */
@RunWith(AndroidJUnit4::class)
class UniversalUiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun settingsBackAndManualImport() {
        Assume.assumeTrue(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".external"))
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Соединение").performClick()
        compose.onNodeWithText("Подключаться при открытии").assertExists()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Маршрутизация").assertExists()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Добавить подписку").performClick()
        compose.onNodeWithText("Добавить вручную").performClick()
        compose.onNodeWithText("Ссылка или конфигурация").assertExists()
        compose.onNodeWithText("Разрешить OAuth-доступ XDrive из этой подписки").assertDoesNotExist()
        compose.onNodeWithText("Ключ AGE · если требуется").assertDoesNotExist()
        compose.onNodeWithText("Дополнительные параметры").performClick()
        compose.onNodeWithText("Разрешить OAuth-доступ XDrive из этой подписки").assertExists()
        compose.onNodeWithText("Ключ AGE · если требуется").assertExists()
        compose.onNodeWithText("Скрыть дополнительные параметры").performClick()
        compose.onNodeWithText("Ключ AGE · если требуется").assertDoesNotExist()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithContentDescription("Добавить подписку").assertExists()
    }

    @Test fun independentProviderGroupsAndSelection() = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".external"))
        val repository = get<Repository>(Repository::class.java)
        repository.awaitReady()
        val existing = repository.state.value.subscriptions.map { it.id }.toSet()
        val originalSelection = repository.state.value.selected
        val token = UUID.randomUUID().toString().take(8)
        fun fixture(name: String) = JSONObject()
            .put("remarks", name)
            .put("outbounds", JSONArray().put(
                JSONObject().put("tag", "proxy").put("protocol", "socks")
                    .put("settings", JSONObject().put("servers", JSONArray().put(
                        JSONObject().put("address", "127.0.0.1").put("port", 10888)
                    )))
            )).toString()
        try {
            repository.import(fixture("Provider-A-$token"))
            repository.import(fixture("Provider-B-$token"))
            val added = repository.state.value.subscriptions.filter { it.id !in existing }
            assertEquals("Two independent provider sources", 2, added.size)
            assertNotEquals(added[0].id, added[1].id)
            val first = added.first { it.title.contains("Provider-A") }
            val second = added.first { it.title.contains("Provider-B") }
            repository.toggleSubscription(first.id)
            assertTrue(first.id in repository.state.value.settings.collapsedSubscriptions)
            assertFalse(second.id in repository.state.value.settings.collapsedSubscriptions)
            repository.select(second.servers.single().id)
            assertEquals(second.servers.single().id, repository.state.value.selected)
            repository.favorite(second.servers.single().id)
            assertTrue(repository.state.value.servers.single { it.id==second.servers.single().id }.favorite)
            repository.favorite(second.servers.single().id)
            assertFalse(repository.state.value.servers.single { it.id==second.servers.single().id }.favorite)
            repository.toggleSubscription(first.id)
            assertFalse(first.id in repository.state.value.settings.collapsedSubscriptions)
        } finally {
            repository.state.value.subscriptions.filter { it.id !in existing }.forEach { repository.remove(it.id) }
            originalSelection?.takeIf { id -> repository.state.value.servers.any { it.id == id } }?.let { repository.select(it) }
        }
    }
}
