# Compose UI 自动化测试落地说明

## 当前 UI 树概览

### 顶层入口
- `MainActivity`
  - 先检查 `oobeSeenVersion`
  - 未完成引导时跳转 `OobeActivity`
  - 完成后渲染 `HomeComposeScreen`

### 首页主树
- `HomeComposeScreen`
  - 个人中心入口
  - 频道列表
    - 普通 RSS -> `FeedActivity`
    - B 站 -> `BiliEntryActivity`
    - 抖音 -> `DouyinEntryActivity`
  - `RSS推荐`
  - `添加RSS`
  - `备案信息`

### 当前自动化覆盖页面
- `OobeScreen`
- `HomeComposeScreen`
- `ProfileScreen`
- `AddRssScreen`
- `SettingsScreen`
- `AboutScreen`
- `ContactDeveloperScreen`
- `CollaboratorsScreen`
- `ProjectInfoScreen`
- `BeianScreen`
- `JoinGroupScreen`
- `LogUploadPrivacyScreen`
- `ActionDialogScreen`
- `ProfileActivity`
- `AddRssActivity`
- `SettingsActivity`
- `StaticCommonActivityTest` 覆盖的通用静态页面
- `StaticCommonActivitySmokeTest` 覆盖的静态 Activity 启动链路
- `BiliActivitySmokeTest`
- `DouyinActivitySmokeTest`
- `CommonActivitySmokeTest`

当前基线已经从“首批 Compose 页面”扩展到“Compose screen test + Activity smoke + 平台假仓库注入”的整套真机自动化。

## testTag 设计

### 已落地规则
- tag 常量集中在 `app/src/main/java/com/lightningstudio/watchrss/ui/testing/WatchRssTestTags.kt`
- 优先给可操作的业务节点打 tag，而不是给装饰性子节点打 tag
- 动态列表项使用业务 ID，例如 `home/channel_card/<channelId>`
- 文本和 `contentDescription` 继续保留给 UI 和无障碍，不再作为自动化主选择器

### 新页面接入方式
1. 先在 `WatchRssTestTags.kt` 新增页面级 tag 常量，避免测试直接写字符串。
2. 在 Compose 根节点、核心 CTA、关键状态位上增加 `Modifier.testTag(...)`。
3. 对列表、卡片、stepper 这类会重复出现的节点，优先给容器打 tag，再视需要给内部动作按钮补充 tag。
4. 写 `ScreenTest` 时优先使用 `createComposeRule()` 做纯 Compose 验证，只在需要验证 Activity 装配时才上 `createAndroidComposeRule()`。
5. 遇到 `MergedSemantics` 导致节点找不到时，先尝试 `useUnmergedTree = true`，不要回退到脆弱的文本选择器。

### 第一批已落地 tag
- `OobeTestTags`
  - 根节点、下一页、继续、协议勾选框、错误提示
- `HomeTestTags`
  - 首页根节点、列表、个人中心、空态、推荐、添加、备案
  - 动态频道卡片与左右滑动作按钮
- `ProfileTestTags`
  - 收藏、稍后再看、设置、关于、联系开发者
- `AddRssTestTags`
  - 输入框、提交、手机输入、错误、预览、已存在、二维码、回退按钮
- `SettingsTestTags`
  - 缓存 stepper、主题开关、分享开关、字体 stepper、OOBE 入口、手机互联开关、备案入口

## 测试流程表

| 优先级 | 流程 | 前置条件 | 步骤 | 预期结果 | 备注 |
|---|---|---|---|---|---|
| P0 | OOBE 首启完成 | `oobeSeenVersion < CURRENT_OOBE_VERSION` | 启动 `MainActivity` -> OOBE 下一页 -> 未勾协议点继续 -> 勾选协议 -> 继续 | 未勾选出现错误；勾选后允许继续 | 当前已落地 screen-level 测试 |
| P0 | 首页基础结构 | 已完成 OOBE | 打开首页 -> 校验个人中心、空态或频道、推荐、添加 RSS、备案 | 首页主结构完整 | 当前已落地 screen-level 测试 |
| P0 | 首页固定入口点击 | 已完成 OOBE | 点击个人中心、推荐、添加 RSS、备案 | 回调触发或导航开始 | 当前已落地 screen-level 测试 |
| P0 | 首页频道点击 | 至少一个频道 | 点击频道卡片 | 进入频道或触发点击回调 | 当前已落地 screen-level 测试 |
| P0 | 我的页入口 | 无 | 打开 Profile -> 点击各入口 | 收藏、稍后再看、设置、关于、联系开发者入口可用 | 当前已落地 screen-level 测试 + Activity smoke |
| P0 | 添加 RSS 输入 | 无 | 输入 URL -> 点击提交 | URL 更新并触发提交回调 | 当前已落地 screen-level 测试 |
| P0 | 添加 RSS 预览确认 | 处于 `PREVIEW` 状态 | 点击确认添加 / 修改地址 | 对应回调触发 | 当前已落地 screen-level 测试 |
| P0 | 添加 RSS 已存在 | 处于 `EXISTING` 状态 | 点击跳转频道 | 打开已有频道 | 当前已落地 screen-level 测试 |
| P0 | 添加 RSS 手机输入二维码 | 处于 `QR_CODE` 状态 | 校验二维码 -> 点击返回 | 二维码与返回按钮可用 | 当前已落地 screen-level 测试 |
| P0 | 设置页基础结构 | 无 | 打开设置 -> 校验缓存、主题、分享、字体、调试入口、备案 | 设置页主结构完整 | 当前已落地 screen-level 测试 + Activity smoke |
| P0 | 设置页核心交互 | 无 | 点击缓存 stepper、字体 stepper | 回调触发并传出正确值 | 当前已落地 screen-level 测试 |
| P1 | 首页频道左滑快捷操作 | 普通 RSS 频道存在 | 左滑频道卡片 -> 点击移到顶/标记已读 | 动作按钮出现并生效 | tag 已落地，手势断言留待第二批 |
| P1 | Feed 列表到详情 | 频道内至少一个 item | 打开频道 -> 点击 item | 进入详情页 | 需要测试数据注入 |
| P1 | Feed item 左滑操作 | 频道内至少一个 item | 左滑 item -> 收藏 / 稍后再看 | 状态更新 | 需要更多稳定 tag |
| P2 | B 站入口结构 | B 站频道存在 | 首页进入 B 站 | 显示登录或内容流 | 依赖网络与账号 |
| P2 | 抖音入口分流 | 抖音频道存在 | 首页进入抖音 | 根据登录态进入不同内容页 | 依赖登录态与网络 |

## 已新增测试

- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/screen/OobeScreenTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/screen/home/HomeComposeScreenTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/screen/ProfileScreenTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/screen/common/StaticCommonScreenTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/screen/rss/AddRssScreenTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/screen/rss/SettingsScreenTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/activity/ProfileActivityTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/activity/AddRssActivityTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/activity/SettingsActivityTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/activity/BiliActivitySmokeTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/activity/DouyinActivitySmokeTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/activity/CommonActivitySmokeTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/activity/common/StaticCommonActivityTest.kt`
- `app/src/androidTest/java/com/lightningstudio/watchrss/ui/activity/common/StaticCommonActivitySmokeTest.kt`

当前真机通过数：75/75

## Activity 级落地方式

### 测试注入
- `WatchRssApplication` 已支持 `setContainerForTesting(...)`
- `app/src/androidTest/java/com/lightningstudio/watchrss/testutil/TestAppContainer.kt` 提供 `TestAppContainerRule`
- `app/src/androidTest/java/com/lightningstudio/watchrss/testutil/FakeRssRepository.kt` 提供可控频道、缓存与动作回调

Activity 场景推荐做法：
1. 在 `@Before` 里通过 `TestAppContainerRule` 注入假仓库和假设置仓库。
2. 用 `createAndroidComposeRule<...Activity>()` 启动真实页面。
3. 只校验 Activity 装配、首屏结构和关键导航，不在 Activity smoke test 里重复 screen-level 的全部交互断言。

### Screen 级与 Activity 级分工
- `ScreenTest` 负责稳定、细粒度的节点断言和回调断言。
- `ActivityTest` 负责应用容器接线、生命周期和首屏可见性。
- 复杂导航链路放到后续集成测试，不把所有职责堆进单个 UI 用例。

### 当前稳定性策略
- `connectedDebugAndroidTest` 已切到 `Android Test Orchestrator`
- instrumentation 运行时开启 `clearPackageData=true`
- 真实设备全量回归不再共享同一个测试进程，避免前序用例污染后续 Activity/Compose 状态

## 运行方式

- 构建主 APK 与测试 APK：
  - `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`
- 安装并启动应用：
  - `./gradlew :app:installDebug`
  - `adb shell am start -n com.lightningstudio.watchrss/.MainActivity`
- 运行真机 Compose/UI 自动化：
  - `./gradlew :app:connectedDebugAndroidTest`
- 定向跑某一组测试：
  - `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lightningstudio.watchrss.ui.screen.rss.SettingsScreenTest`

说明：
- 当前 `connectedDebugAndroidTest` 由 orchestrator 隔离执行，速度比共享进程更慢，但真机稳定性明显更高。

## 后续扩展建议

### 第二批优先页面
- `FeedScreen`
- `DetailScreen`
- `SettingsScreen`
- `BiliLoginScreen`

### 继续往 Activity / 集成测试推进前建议补齐
- 已具备 `WatchRssApplication.setContainerForTesting(...)`
- 已具备 `FakeRssRepository`
- 已具备 `TestAppContainer` / `TestAppContainerRule`
- 已具备可控 DataStore 初始化的 `SettingsRepository` 测试工厂

这样可以稳定覆盖 `MainActivity` 首启门禁、频道初始化、Feed 数据装配和后续导航链路。
