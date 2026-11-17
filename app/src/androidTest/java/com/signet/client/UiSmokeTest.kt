package com.signet.client

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun emptyStateAndNavigation(){
        compose.onNodeWithText("Не подключено").assertExists()
        compose.onNodeWithText("Подписки",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Добавить",substring=false).assertExists()
        compose.onNodeWithText("Настройки",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Подключаться при открытии").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Подключаться при открытии").assertExists()
    }
    @Test fun importDialogCanBeCancelled(){
        compose.onNodeWithText("Добавить подписку").assertIsDisplayed().performClick()
        compose.waitUntil(5_000){compose.onAllNodesWithText("Ссылка или конфигурация").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Ссылка или конфигурация").assertExists()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Добавить",substring=false).assertExists()
    }
}
