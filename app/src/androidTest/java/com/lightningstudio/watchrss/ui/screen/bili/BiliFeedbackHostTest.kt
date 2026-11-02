package com.lightningstudio.watchrss.ui.screen.bili

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BiliFeedbackHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun latestMessageReplacesOldMessage_withoutBlockingTouches() {
        val message = mutableStateOf<String?>("已点赞")
        var taps = 0
        compose.setContent {
            BiliFeedbackHost(message.value, { message.value = null }) {
                Box(Modifier.fillMaxSize().testTag("actions").clickable { taps++ }) {
                    Text("视频操作")
                }
            }
        }
        compose.onNodeWithText("已点赞").assertIsDisplayed()
        compose.runOnIdle { message.value = "已取消点赞" }
        compose.onNodeWithText("已取消点赞").assertIsDisplayed()
        compose.onNodeWithText("已点赞").assertDoesNotExist()
        compose.runOnIdle { message.value = "请求失败" }
        compose.onNodeWithText("请求失败").assertIsDisplayed()
        compose.onNodeWithText("已取消点赞").assertDoesNotExist()
        // Physical touch through the feedback itself must reach the action underneath.
        compose.onNodeWithText("请求失败").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun replacingWithSameTextRestartsExpiry() {
        val message = mutableStateOf<String?>("请求失败")
        compose.mainClock.autoAdvance = false
        compose.setContent { BiliFeedbackHost(message.value, { message.value = null }) {} }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("请求失败").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_400)
        compose.runOnIdle { message.value = "请求失败" }
        compose.mainClock.advanceTimeBy(100)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("请求失败").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithText("请求失败").assertDoesNotExist()
    }
}
