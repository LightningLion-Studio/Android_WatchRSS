# Engineering Design Doc — 可穿戴 Watch 圆屏信息流（B站 / 抖音 / RSS）

> 本文是工程侧的“怎么做”文档：架构、模块划分、SDK 设计、数据模型、同步/缓存、性能与测试策略。  
> 产品交互与功能细节以 `features.md` 为准（首页/内容页/详情页/我的页、手势、未读策略、去重规则等）。

---

## 1. 目标与范围

### 1.1 我们要交付什么（V1）
在 可穿戴设备（圆形屏幕）上实现一个信息流应用，支持三类 channel：

- **RSS**：支持订阅/解析/列表/详情阅读（强制阅读模式：纯文本 + 图/视频占位），支持去重、缓存、未读提醒（仅 RSS）。
- **Bilibili**：支持登录、个性化推荐列表、视频详情与播放、点赞/投币/收藏、同步稍后再看/历史记录（尽力而为）。
- **抖音**：支持 WebView 登录、个性化视频获取、竖屏播放页（上下滑切换）。

同时提供统一能力：
- **应用内收藏 / 稍后再看**：三类内容统一入口与本地沉淀；RSS 需要把正文与媒体完整离线化下载，B站/抖音只存链接。

### 1.2 非目标（V1 不做）
- 跨 channel 的全局去重（仅 channel 内去重）。
- HTML 富文本渲染（RSS 详情页不做 HTML 渲染；只提取纯文本与媒体资源）。
- 云端同步（可预留接口，但 V1 默认不启用）。
- 下载 B站/抖音视频到本地（只播放/只存链接）。

---

## 2. 技术选型与约束

### 2.1 语言优先级
- **Kotlin（主）**：应用、UI、数据层、网络层、SDK wrapper。
- **Java（辅）**：兼容第三方 Java 库或 vendor SDK；必要时做桥接。
- **Rust（可选增强）**：用于 CPU/内存敏感的解析/抽取（如 HTML→纯文本、媒体抽取、哈希/去重 key 生成），通过 JNI/NDK 集成。

> 原则：V1 能用 Kotlin/Java 完成则不引入 Rust；Rust 仅在明确有性能/内存收益且可控时引入。

### 2.2 设备与体验约束（工程侧）
- 圆屏、小尺寸、列表密度高 → **首屏快、滚动流畅**优先。
- 手表功耗敏感 → **最小化后台工作**、按需加载、可见区域优先、图片/视频懒加载。
- 网络不稳定 → **离线缓存**、失败可重试、错误提示短而明确。

---

## 3. 总体架构

采用“分层 + 可插拔 Provider”的架构：

```
UI (Activities/Fragments/Views)
  └── Presentation (ViewModel, State, Mapper)
        └── Domain (UseCases, Entities, Interfaces)
              └── Data (Repositories, SyncEngine, DB, Cache)
                    ├── RssProvider (RSS-Parser + custom mapping)
                    ├── BiliProvider (自研 Bili SDK)
                    ├── DouyinProvider (自研 Douyin SDK)
                    └── Media/Download (player, image, offline)
```

### 3.1 模块划分（Gradle modules）
- `:app`：入口、导航、页面、资源、依赖注入组装
- `:core:domain`：统一数据模型、UseCase、接口（不依赖 Android）
- `:core:data`：Repository、Room DB、SyncEngine、缓存策略（依赖 Android）
- `:sdk:rss`：RSS 接入（封装 RSS-Parser + 自定义解析补丁）
- `:sdk:bili`：Bilibili SDK（网络、鉴权、内容、动作、播放地址解析）
- `:sdk:douyin`：抖音 SDK（WebView 登录、cookies、内容、播放）
- `:core:media`：播放器页面、媒体预加载、封面/缩略图
- `:core:offline`：RSS 离线化下载（正文抽取、媒体下载、存储）
- `:rust:core`（可选）+ `:rust:jni`：Rust 解析能力与 JNI 桥接

---

## 4. 统一数据模型（Domain）

### 4.1 Channel
```kotlin
enum class ChannelType { RSS, BILIBILI, DOUYIN }

data class Channel(
  val id: Long,
  val type: ChannelType,
  val title: String,
  val description: String?,
  val iconUrl: String?,
  val sortOrder: Int,
  val config: Map<String, String> // e.g. rssUrl, biliTab, douyinFeedType ...
)
```

### 4.2 Item（统一条目）
```kotlin
enum class ItemType { RSS_ARTICLE, VIDEO }

data class FeedItem(
  val id: Long,
  val channelId: Long,
  val type: ItemType,
  val title: String,
  val summary: String?,
  val thumbUrl: String?,
  val link: String?,          // 用于“浏览器打开/分享”
  val publishedAt: Long?,     // epoch millis
  val dedupKey: String,       // guid/link/title 规则生成
  val isRead: Boolean?,       // 仅 RSS 使用，其它为 null
  val extra: Map<String, String> // 平台扩展字段（aid/bvid、douyin awemeId 等）
)
```

### 4.3 收藏/稍后再看（统一能力）
```kotlin
enum class SaveType { FAVORITE, WATCH_LATER }

data class SavedEntry(
  val id: Long,
  val itemId: Long,
  val saveType: SaveType,
  val createdAt: Long
)
```

### 4.4 RSS 离线内容模型（仅对 RSS 的 SavedEntry）
```kotlin
data class OfflineRssContent(
  val itemId: Long,
  val plainText: String,
  val media: List<OfflineMediaRef> // image/video cover
)

data class OfflineMediaRef(
  val type: String,           // "image" | "video"
  val originUrl: String,
  val localPath: String?,     // 下载成功后写入
  val posterUrl: String?      // 视频封面（若可提取）
)
```

---

## 5. 去重/未读策略（与需求一致）

### 5.1 去重 Key 生成（每个 channel 内）
- 优先：`guid`
- 其次：`link`
- fallback：`title`

```kotlin
fun computeDedupKey(guid: String?, link: String?, title: String): String =
  when {
    !guid.isNullOrBlank() -> "guid:$guid"
    !link.isNullOrBlank() -> "link:$link"
    else -> "title:$title"
  }
```

### 5.2 未读策略（仅 RSS）
- 首次拉取：全部标记为已读
- 之后每次刷新：新增条目 `isRead=false`
- 进入详情页：标记为已读（可后续加“停留超过 N 秒”）

---

## 6. RSS 接入（SDK 选型 + 解析 + 渲染）

### 6.1 RSS SDK 选型结论（V1）
选用 **RSS-Parser（com.prof18.rssparser:rssparser）** 作为 RSS/Atom 解析库。  
原因：
- Kotlin 优先，Android 直接可用，协程 API 与现有代码风格一致。
- 支持 RSS/Atom/RDF，且条目字段覆盖 content/image/audio/video 等常见需求。
- Maven Central 发布，依赖管理简单。

> 备选：Rome（com.rometools:rome + rome-modules），若后续遇到特殊扩展解析需求可切换。  

### 6.2 RSS 拉取与解析流程
1. URL 校验（http/https、空值、长度）
2. RssParser 拉取（内部 OkHttp，可注入超时/重试/charset）
3. 解析为 `RssChannel` / `RssItem`，映射到 `Channel` / `FeedItem`
4. 生成 dedupKey、入库（Room）
5. UI 先渲染缓存；并行触发刷新合并

### 6.3 RSS 模块解析与字段映射
- `RssChannel.title/description/link/image` → Channel 基础字段
- `RssItem.title/description/link/pubDate/guid` → Item 基础字段
- `RssItem.content` → 详情页正文抽取（优先）
- `RssItem.image/audio/video` → 缩略图、音视频入口

> 兼容策略：针对不同 feed 的“脏数据/缺字段”，采用“最小可用”映射（title 必有；summary 可空；thumb 可空）。

### 6.4 详情页“强制阅读模式”（不渲染 HTML）
输入：`description` + `content`（优先）  
输出：纯文本段落 + 图片列表 + 视频占位卡片

实现方案（V1）：
- Kotlin/Java：用轻量 HTML 解析（如基于 jsoup 的子集策略）抽取：
  - 文本：保留段落、标题、列表
  - 图片：`<img src>` + `srcset` 取最合适
  - 视频：`<video>`、`<iframe>`、`<a>` 中的视频链接 → 生成“封面 + 播放”占位
- Rust（可选）：若发现 jsoup 内存压力或卡顿，可用 Rust `html2text + scraper` 实现抽取，并在 Kotlin 侧调用 JNI。

---

## 7. Bilibili SDK（自研 wrapper）

> 前提：B站 API 已存在（不在此文列出具体 endpoint），我们实现“可维护、可替换”的 SDK 层，屏蔽鉴权/签名/限流/重试/解析差异。

### 7.1 模块职责
- `BiliAuth`：
  - 登录（二维码 + 密码）
  - cookie/token 持久化、续期、过期检测
- `BiliFeed`：
  - 个性化推荐列表
  - 分页/刷新参数管理
- `BiliAction`：
  - 点赞/投币/收藏
  - 稍后再看/历史（同步：尽量同步，失败不阻塞本地）
- `BiliPlay`：
  - 获取播放信息（dash/hls 等）
  - 适配播放器输入（url + headers + cookie）

### 7.2 鉴权与 cookie 存储
- 登录成功 → 统一写入 `AccountStore(BILIBILI)`：
  - cookies（全量）
  - 用户标识（若有）
  - 过期时间（若可推断）
- 存储介质：
  - `EncryptedSharedPreferences`/Keystore（优先）
  - fallback：加密后的 Room 表（仅在必要时）

### 7.3 错误与重试策略
- 401/403：触发“会话过期”→ 引导重新登录
- 429：限流 → 指数退避
- 网络错误：短提示 + “重试”按钮（不自动无限重试）

---

## 8. 抖音 SDK（自研 wrapper）

### 8.1 登录（WebView）
- 首次进入抖音 channel：进入 WebView 登录页
- 登录成功：从 CookieManager 导出 cookies → 存储到 `AccountStore(DOUYIN)`
- 之后请求：通过 OkHttp CookieJar 注入 cookies

> 注意：WebView 与 OkHttp Cookie 同步需要明确实现“导入/导出”与域名过滤，避免存入无关域 cookie。

### 8.2 内容获取与播放
- `DouyinFeed`：拉取推荐视频列表（分页）
- `DouyinPlay`：
  - 获取可播放链接（可能为重定向/临时链接）
  - 支持上下滑切换：预取下一条视频的播放信息与封面

---

## 9. 数据层：DB、Repository、SyncEngine

### 9.1 Room 表（建议）
- `channels`
- `items`
- `read_state`（或 items 中 isRead，仅 RSS 写入）
- `saved_entries`
- `offline_rss_content`
- `account_store`
- `sync_state`（etag/lastModified/lastCursor）

### 9.2 Repository 统一接口
```kotlin
interface ChannelRepository {
  suspend fun listChannels(): List<Channel>
  suspend fun reorderChannels(idsInOrder: List<Long>)
  suspend fun syncChannel(channelId: Long, reason: SyncReason)
  fun observeItems(channelId: Long): Flow<List<FeedItem>>
  suspend fun loadMore(channelId: Long)
}
```

### 9.3 SyncEngine
- 触发来源：
  - 手动刷新（UI）
  - 添加 RSS 成功后立即拉取
  - 定时任务（可选，默认关闭或低频）
- 策略：
  - **先本地，后网络**
  - 只拉取增量（RSS 可用 etag/lastModified；平台用 cursor）
  - 合并去重后入库

---

## 10. 缓存、预加载与离线

### 10.1 列表预加载
- 仅加载“可视区域 + 下方 3 条”的数据与首图（满足需求里的功耗优化原则）
- 图片：
  - 缩略图低分辨率优先
  - 磁盘缓存 + 内存缓存（限制上限）
- 视频：
  - 仅预取 metadata（时长/封面/播放链接），不提前下载内容

### 10.2 RSS 离线化下载（收藏/稍后再看）
触发：用户对 RSS 条目执行“收藏/稍后再看”  
执行：
1. 抓取条目全文（优先 `content:encoded`，否则 fallback）
2. 抽取纯文本与媒体 URL
3. 下载所有图片与视频封面（视频本体不下载）
4. 写入 `offline_rss_content`，并更新媒体 localPath
5. 在“我的-收藏/稍后再看”中优先展示离线内容

执行载体：WorkManager（网络可用、充电/电量阈值可选）

---

## 11. 播放器与媒体页

### 11.1 B站播放页
- 详情页展示封面，点击进入播放页
- 播放失败：提示 + 重试 + 若有 link 则“浏览器打开”

### 11.2 抖音播放页（竖屏上下滑）
- 单独 Activity/Fragment
- 使用 vertical pager（自研或 ViewPager2 竖向）：
  - 当前播放
  - 预加载下一条
  - 上一条可回看（可选）

---

## 12. UI 实现要点（圆屏）

### 12.1 页面与导航
- 首页：channel 列表（B站/抖音/RSS）+ 底部“添加 RSS”
- 内容页：条目列表（Image Background Card）
- 详情页：RSS 纯文本阅读 / B站详情 / 抖音播放
- 我的页：账号、收藏、稍后再看、设置、关于

### 12.2 手势与菜单
- 上下滑：滚动
- 左滑 item：快速操作（收藏/稍后再看；首页还有置顶/已读等）
- 右滑：返回
- 长按（仅首页列表）：复杂操作菜单（置顶/已读/删除等）

---

## 13. 安全、隐私与合规

- Cookie / token：加密存储；仅用于用户主动使用的平台能力
- 网络：全程 https；禁止明文落盘敏感响应
- 日志：默认不记录敏感字段；Debug 构建可开启 verbose
- 第三方平台：遵守其 API/条款（尤其是鉴权、频率、展示/分享规则）

---

## 14. 测试策略

### 14.1 单元测试
- dedupKey 规则、合并逻辑
- RSS 映射（多种 feed 样例）
- HTML → 纯文本抽取（黄金样例）
- AccountStore 加解密与过期处理

### 14.2 集成测试
- MockWebServer：模拟 RSS/B站/抖音响应、超时、401、429
- DB migration 测试（版本升级）

### 14.3 性能测试（手表真机）
- 冷启动到首页首屏（目标：尽可能 < 1.5s，按实际设备调整）
- 列表快速滚动帧率
- 连续阅读/播放功耗对比（A/B：预加载开关）
- 性能信息获取（Debug 构建）
  - 应用内 JankStats（PerformanceMonitor）
    - 进入目标页面 → 触发滚动/交互 → 返回上一页或切后台，`onPause` 会输出统计
    - 获取日志：`adb logcat -d -s perf`（汇总）与 `adb logcat -d -s frame`（逐帧分解，仅 DetailActivity）
    - 日志字段：`screen`、`scenario`、`frames`、`jank`、`jankPct`、`avgMs`、`maxMs`
  - 系统 framestats（dumpsys gfxinfo）
    - 重置计数：`adb shell dumpsys gfxinfo com.lightningstudio.watchrss reset`
    - 滚动/复现场景后导出：`adb shell dumpsys gfxinfo com.lightningstudio.watchrss framestats > /sdcard/gfxinfo.txt`
    - 拉取分析：`adb shell cat /sdcard/gfxinfo.txt`
    - 关注指标：`Janky frames / Total frames`、`90th/95th/99th percentile`、`Number High input latency`
  - 屏幕刷新率确认（用于 16ms 阈值）
    - `adb shell dumpsys SurfaceFlinger --latency`（首行约 16666666ns 表示 60Hz）

---

## 15. 里程碑（建议）

- **M0（基础骨架）**：工程结构、DB、导航、首页列表（静态数据）
- **M1（RSS 可用）**：添加 RSS、解析、内容页、详情阅读、去重/未读
- **M2（B站登录+列表）**：二维码登录、推荐列表、详情页骨架
- **M3（B站播放+动作）**：播放页、点赞/投币/收藏、稍后再看同步
- **M4（抖音登录+播放）**：WebView 登录、推荐列表、竖向播放页与切换
- **M5（我的页+离线）**：收藏/稍后再看、RSS 离线下载、设置（主题/字号）
- **M6（稳定性&发布）**：崩溃率、网络弱场景、功耗优化、隐私合规、开源清单

---

## 16. 风险与对策

1. **平台 API 变更/风控**
   - 对策：SDK 层强隔离；接口可热修（配置化 endpoint/headers）；关键路径降级（浏览器打开）
2. **WebView 登录 cookie 不稳定**
   - 对策：明确域名白名单；导出/导入双向校验；异常引导重登
3. **RSS“脏数据”导致解析失败**
   - 对策：解析容错（try-catch + fallback）；保存原始 xml 片段用于 debug（不含敏感）
4. **圆屏 UI 点击区域太小**
   - 对策：统一最小触控区域（>= 48dp）；重要操作放到滑动菜单按钮
5. **离线下载耗电/耗流量**
   - 对策：WorkManager + 仅 Wi‑Fi（可选）+ 批量下载；限制单条大小与总缓存

---

## 17. Open Questions（需要尽早澄清）
- B站/抖音 API 的鉴权方式、限频策略、是否需要签名/设备指纹
- 视频播放 URL 是否包含 DRM / 是否支持手表侧解码能力
- 是否需要 云同步（V2）以及与手机端的互通需求
- 厂商圆屏控件能力与限制（滑动菜单、卡片样式、圆屏适配细节）

---

## 18. UI 约定补充（2026-03）

### 18.1 首页个人入口
- 首页顶部“我的”入口保留头像占位与“点击进入我的”提示。
- 不再显示“未登录”占位文案，避免在尚未接入账号体系时产生误导。

### 18.2 联系开发者 -> 日志上传
- “隐私及防诈说明”页中的“开始上传”按钮使用与其他小型胶囊按钮一致的样式。
- 该按钮保持居中展示，不再使用整行铺满的特殊外观。

### 18.3 设置页的调试入口
- “新手引导”在设置页中作为整条 pill 的点击入口，右侧不再保留额外的小按钮。
- “新手引导”及其后的“开发者选项”区块只在 `BuildConfig.DEBUG == true` 时渲染。
- Release 构建不显示上述入口，避免把调试/回看流程暴露给正式用户。

---
