// AndroidX Test 的 screenshot API 位于 androidx.test.runner.screenshot 包下；
// 该类在 runner 1.7.0 中标记为 @Deprecated，但仍是用户要求的 AndroidX Test
// 截图入口，且在当前设备上可稳定工作。替代方案（Espresso captureToBitmap）
// 在 Android 15+ 上同样依赖 InputManager 反射，保留此 API 更稳妥。
@file:Suppress("DEPRECATION")
package com.lightningstudio.watchrss

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.Screenshot
import com.karumi.shot.ScreenshotTest
import com.lightningstudio.watchrss.testutil.RealDataTestHelper
import com.lightningstudio.watchrss.testutil.ScreenshotTestPermissions
import com.lightningstudio.watchrss.ui.testing.HomeTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchScreenshotTest : ScreenshotTest {

    private val composeTestRule = createAndroidComposeRule<HomeFeedListActivity>()
    private val permissionRule = ScreenshotTestPermissions.grantRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(permissionRule)
        .around(composeTestRule)

    /**
     * 覆盖 Shot 默认实现，跳过 [androidx.test.espresso.Espresso.onIdle]。
     *
     * Espresso 3.6.x 在 Android 15 (API 35) 上反射调用已被移除的
     * `InputManager.getInstance()` 会抛出 [NoSuchMethodException]，
     * 导致 Shot 截图前的等待逻辑崩溃。仅使用 instrumentation 的 idle
     * 同步已足够让 Compose 完成布局与动画。
     */
    override fun waitForAnimationsToFinish() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    /**
     * 使用 AndroidX Test 的 [Screenshot] API 捕获当前 Activity 窗口。
     *
     * 选择 `capture(view)` 只截取 App 自身窗口，避免状态栏/导航栏图标
     * 在不同模拟器/不同时间产生像素漂移。
     */
    private fun captureActivityBitmap(): Bitmap {
        val activity = composeTestRule.activity
        return Screenshot.capture(activity.window.decorView).bitmap
    }

    @Before
    fun setUp() {
        // 锁定竖屏，避免截图方向不一致
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // 使用真实数据库准备数据
        RealDataTestHelper.seedPopulatedLibrary()
        composeTestRule.waitForIdle()
    }

    @Test
    fun home_populated() {
        composeTestRule.onNodeWithTag(HomeTestTags.CHANNEL_LIST).assertIsDisplayed()
        // 必须在 @Test 方法体内直接调用 compareScreenshot，
        // 这样 Shot 的 TestNameDetector 才能通过栈迹识别测试名。
        compareScreenshot(captureActivityBitmap(), "home_populated")
    }

    @Test
    fun home_empty() {
        RealDataTestHelper.seedEmptyLibrary()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(HomeTestTags.EMPTY_ENTRY).assertIsDisplayed()
        compareScreenshot(captureActivityBitmap(), "home_empty")
    }
}
