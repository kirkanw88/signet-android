package com.signet.client

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.Before
import org.junit.rules.TestName
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModelProvider
import com.signet.client.data.settingsStore
import com.signet.core.AppRelease
import com.signet.core.ReleaseVersion
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule val name=TestName()
    @Before fun awaitInteractiveWindow(){
        // A composed tree can exist before WindowManager starts delivering touch events.
        compose.waitUntil(15_000){compose.activity.hasWindowFocus()}
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(3000)
    }
    @After fun saveFixtureUiState(){
        if(InstrumentationRegistry.getArguments().getString("localVpn")!="true")return
        runCatching {
            val instrumentation=InstrumentationRegistry.getInstrumentation()
            val directory=File(instrumentation.targetContext.getExternalFilesDir(null),"ui-state").apply{mkdirs()}
            UiDevice.getInstance(instrumentation).takeScreenshot(File(directory,"${name.methodName}.png"))
            val tree=runCatching{compose.onRoot(useUnmergedTree=true).printToString(10)}.getOrElse{"Semantics unavailable: ${it.javaClass.simpleName}"}
            File(directory,"${name.methodName}.txt").writeText(tree)
        }
    }
    @Test fun emptyStateAndNavigation(){
        runBlocking {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val key=booleanPreferencesKey("hwid");val before=context.settingsStore.data.first()[key]
            try {
                context.settingsStore.edit{it[key]=false}
                org.junit.Assert.assertTrue("Old opt-out must migrate to always-on",com.signet.client.data.SettingsStore(context).load().hwid)
            }finally {context.settingsStore.edit{if(before==null)it.remove(key)else it[key]=before}}
        }
        compose.onNodeWithText("Не подключено").assertExists()
        compose.onNodeWithText("Подписки",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Добавить",substring=false).assertExists()
        compose.onNodeWithText("Настройки",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Подключаться при открытии").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Подключаться при открытии").assertExists()
    }
    @Test fun importDialogCanBeCancelled(){
        compose.activityRule.scenario.onActivity{ViewModelProvider(it)[MainViewModel::class.java].repository.notify("UI fixture message")}
        compose.waitUntil(5_000){compose.onAllNodesWithText("UI fixture message").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Добавить подписку").assertIsDisplayed().performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithText("Ссылка или конфигурация").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Ссылка или конфигурация").assertExists()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Добавить",substring=false).assertExists()
    }
    @Test fun manualUpdateControlIsReachable(){
        compose.onNodeWithText("Настройки",useUnmergedTree=true).performClick()
        compose.waitUntil(25_000){compose.onAllNodes(hasText("Проверить обновление") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Проверить обновление").performScrollTo().assertIsEnabled()
    }
    @Test fun availableUpdateBannerCanBeDismissed()=runBlocking {
        val store=InstrumentationRegistry.getInstrumentation().targetContext.settingsStore
        val cache=stringPreferencesKey("update_release");val dismissed=stringPreferencesKey("update_dismissed");val last=longPreferencesKey("update_last_attempt")
        val before=store.data.first()
        var model:MainViewModel?=null
        compose.activityRule.scenario.onActivity{model=ViewModelProvider(it)[MainViewModel::class.java]}
        try {
            val fixture=AppRelease(ReleaseVersion(99,0,0),"https://github.com/${AppRelease.REPOSITORY}/releases/tag/v99.0.0",null,null)
            store.edit{it[cache]=fixture.json();it[dismissed]="";it[last]=System.currentTimeMillis()}
            model!!.updates.restore()
            compose.waitUntil(10_000){compose.onAllNodesWithText("Доступна новая версия 99.0.0").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Доступна новая версия 99.0.0").assertIsDisplayed()
            compose.onNodeWithText("Позже").performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithText("Доступна новая версия 99.0.0").fetchSemanticsNodes().isEmpty()}
        }finally {
            store.edit{p->
                for(key in listOf(cache,dismissed)){val old=before[key];if(old==null)p.remove(key)else p[key]=old}
                val old=before[last];if(old==null)p.remove(last)else p[last]=old
            }
            model!!.updates.restore()
        }
    }
}
