# v2.0 工单实施记录

本记录保存用户选定方案及当前实际状态。代码修改、构建通过与真机验收分别记录，未验收项不视为关闭。

开始任务时无 ADB 设备；后续已使用独立空白 Android 37 ARM64 模拟器完成安装、启动与部分原生回归，详见下方按时间追加的证据。真实圆屏设备验收仍待完成。开始任务时 DetailScreen.kt 已有用户未提交修改，予以保留。

| 工单 | 用户方案 | 内容 | 状态 |
|---|---|---|---|
| BUG-001 | A | 光标与组合输入 | 已修改；单测通过；待真机 |
| BUG-002 | 新提示前取消旧 Toast | 最新操作反馈 | 全局主线程通道先 cancel 再 show；顺序回归通过；待真机 |
| BUG-003 | A | 封面失败恢复 | RSS 与 B站信息流封面已共用恢复路径；新已验证网络重试规则有单测覆盖，真实 Wi‑Fi／蜂窝切换待设备验收 |
| BUG-004 | A | 同步预设立即更新当前样式 | 活动预设观察与资源缓存版本更新；云端覆盖字号／背景色原生回归通过；待真机 |
| BUG-005 | A | 已读红点一致 | 已统一频道与未读数查询；新增 EPUB 章节 Room 一致性原生回归通过；小说界面与圆屏动态体验待真机 |
| BUG-006 | A | 阅读背景覆盖底部 | 已补窗口／导航栏活动预设底色及随底色切换的导航图标明暗；新增 DetailActivity 原生运行测试验证预设变色时窗口与导航栏同步更新；真实圆屏截图待验收 |
| REQ-001 | B | 书籍入口继续阅读 | EPUB 与分章 TXT 目录加入继续阅读；章节打开时记录最近位置且正文进度不变；导航回归 3 项通过并已安装启动；真实双端与真机待验收 |
| REQ-002 | A | 小说隐藏 RSS 专属操作 | 已统一内容能力，隐藏原文、内部地址分享和查看全文；本轮能力矩阵 6 项测试通过；待真机 |
| REQ-003 | A | 章末上一章与下一章 | 已按书籍及稳定章节编号导航，首末边界与保存失败提示；本轮章节回归 3 项通过并安装启动；待真机 |
| REQ-004 | B | 控制面板与正文触控选项 | 已增加持久化正文点击开关，默认关闭；自动滚动与设置回归通过；待真机 |
| REQ-005 | A | 应用内 TTS 悬浮控制 | 已增加跨页面悬浮入口、拖动吸边和明确停止；悬浮边界回归通过；待真机 |
| REQ-006 | B | 字号滑块与独立主题选择 | 已增加手表字号滑块、独立主题与阅读器设置入口；预设／主题定向回归通过；待真机 |
| REQ-007 | A | 备忘录图片块与稳定引用 | 已增加图片占位、完整引用保护和光标映射；本轮编辑回归 7 项通过；待真机 |
| REQ-008 | B | 独立排序页 | 已加入首页独立排序页与保存／取消；本轮排序规则 5 项回归通过；待真机 |
| REQ-009 | A | 抖音首条就绪即静音播放 | 首条进入与静音会话状态已核对；启动／恢复定向回归通过；待真机 |
| REQ-010 | B | 暂停后保留可见控制入口 | 已加入定时收起及生命周期暂停后的可见控制入口；播放手势回归通过；待真机 |
| REQ-011 | A | 分层提供播放控制 | 已加入进度、暂停、音量及展开的跳转／画面控制；本轮 21 项控制／音量回归、安装启动通过；待真机 |
| REQ-012 | A | 长按临时倍速松手恢复 | 按下／松手／取消／生命周期清理已核对；定向回归和安装启动通过；待真机 |
| REQ-013 | B | B站专门播放控制面板 | 已加入大进度、角度选择、平移回中及播放反馈；缩放／播放边界回归通过；待真机 |
| REQ-014 | B | 完整详情页与明确操作 | 已补完整标题、操作文字和分享去向；11 项 B 站原生冒烟通过；待真机 |
| REQ-015 | B | 首页简洁状态与任务详情 | 已补阶段记录、完整结果和日志详情；本轮 2 项同步状态原生测试通过；待真机 |
| REQ-016 | A+B | 可跳过引导与空状态引导 | 已补首次四步引导、空内容入口与复看；新 intent 抑制边界回归及 2 项原生引导测试通过；待真机 |
| REQ-017 | A+B | 本地基础帮助与远程帮助中心 | 手机端本地帮助、版本说明、远程缓存及同步帮助已补；448 项测试与构建、2 项原生运行测试通过；真实远程内容待配置 |
| REQ-018 | A | 按操作解释权限与拒绝路径 | 已补首页同步／设置预览的按需说明与拒绝后设置路径；446 项测试及构建通过；待设备拒绝场景 |
| REQ-019 | B | 未同意协议时就地引导 | Phone 已补提示与滚动定位；Watch OOBE 点击继续时显示就地错误、保持未勾选且不前进；原生回归通过；待真机 |
| REQ-020 | B | 多账号独立数据空间 | 两端已接入独立 Room／文件／云状态和同步账号边界；Phone 455 项单测、9 项原生回归通过；Watch 586 项单测、4 项原生回归及安装通过；真实配对验收待完成 |
| REQ-021 | 查看 B | 登录付费页协议链接与关于页汇总 | 已补登录／授权页公开链接及关于页付费协议；446 项测试及构建通过；待真机 |
| REQ-022 | B | 高频页样式试点后渐进迁移 | Phone 同步卡片及 Watch 首页、个人中心、关于／联系页、B站频道信息、RSS／B站搜索及结果、RSS／B站信息流、AI 总结、Token 用量、文章阅读、收藏列表、RSS／B站频道设置、TTS 设置、连接手机页、相关设置／帮助页和通用入口首轮迁移已完成；二维码、删除确认、图片预览及设置控件主题对比已修正；其他页面待渐进迁移 |
| REQ-023 | A+B | B站扫码文案与帮助面板 | 已修改；全量单测与构建通过；待真机 |
| REQ-024 | B | 可见卡片优先缓存复用 | RSS 与 B站可见卡片已复用缓存、优先加载；快速滚动待真机验收 |
| REQ-025 | A | B站会话单条循环开关 | 已补当前会话循环设置；571 项测试与构建通过；待真机 |
| REQ-026 | A | 长按删除单条搜索历史 | 已修改；全量单测与构建通过 |
| REQ-027 | A | 标题切换收藏夹独立位置 | 已补标题选择、多夹缓存、独立分页与位置；575 项测试及构建通过；待真机 |
| REQ-028 | A | 按浏览器处理能力展示入口 | 下载手机端弹窗已修改；全量单测与构建通过；待真机 |
| REQ-029 | A | 同播放会话沉浸模式 | 同会话禁用切片与自动跳片已核对；播放分页回归通过；待真机 |
| RSCH-001 | B | 区分统计指标名称和口径 | 已按代码来源修正首次引导事件报表与说明；页面断言及真实渲染函数检查通过 |
| RSCH-002 | A | 候选支付渠道保留旧权益 | 5 项微信支付回归通过，已覆盖旧迅虎权益保留；商户主体／资质和生产联调待确认 |
| RSCH-003 | A+B | 蓝牙增量补传与云增量同步 | 两端已有账号隔离与云增量协议；已接入本地自动补传、合并／有限重试、会话互斥和异常暂停恢复；Phone 463 项单测＋11 项原生、Watch 588 项单测＋5 项原生通过；两端发布版入口、用户控制、只读恢复和取消反馈已补齐并验证；阅读预设／资源双向云桥接已补齐并验证；备忘录正文／目录／图片增量云桥接已接入本地验证；历史恢复备忘录已补齐本地验证；自动同步冲突汇总与 Android 11 云密钥能力已本地验证；真实双设备／云账号待验收 |
| RSCH-004 | B；火山方舟大模型朗读 | 在线朗读与缓存保留兜底 | 后端已支持方舟 Agent Plan HTTP TTS 与普通语音 V3 SSE 双模式、配置隔离及有界缓存；方舟授权音色联调待完成 |
| RSCH-005 | A | 小程序已有能力与最小范围 | 已完成代码能力盘点与最小范围文档；103 项本地测试通过；真实微信联调待验收 |
| RSCH-006 | B | B站单独音频路径 | 已补独立 DASH 音频选择与媒体会话服务、详情入口和停止控制；578 项测试及构建通过，API 30／37 模拟器原生音频生命周期测试通过；真实 B站来源、OEM 中断和功耗待设备 |
| RSCH-007 | A | 扫码等待保持亮屏 | B站扫码页已修改，API 30 原生亮屏／页面清理测试通过；设备矩阵待真机 |

## 已实施 comment

- BUG-001：移除正文与标题 onValueChange 持续回写旧选区的过滤；保留一次性焦点恢复，接受实际选区与组合输入。补光标移动、组合输入与撤销回归。
- BUG-002：所有手表端 Toast 汇入主线程公共通道，新提示先取消旧提示，使用 applicationContext 避免持有 Activity。

- Comment REQ-022（联系开发者页主题迁移）：联系开发者入口从固定纯黑容器改用统一 `WatchSurface`，标题和操作继续使用主题前景色；加群／上传日志回调及按钮触控规格保持不变。`assembleDebug`、Android 37 模拟器 `installDebug` 均成功，MainActivity 启动且 logcat 未见 AndroidRuntime／SQLite 异常；页面视觉仍待圆屏验收。
- Comment REQ-022（日志上传等待／错误状态主题迁移）：日志上传页外层改用统一 `WatchSurface`，加载指示、加载文案和错误文本继续继承 Material 主题色；WebView 内容与日志加载逻辑保持原样。`assembleDebug`、Android 37 模拟器 `installDebug` 均成功，MainActivity 启动且 logcat 未见 AndroidRuntime／SQLite 异常；WebView 页面仍待设备验收。
- Comment REQ-022（项目说明正文主题迁移）：信息页正文标题和段落从固定白字改用 Material `onSurface`，随当前主题保持可读对比；正文拆分、字号、备案链接和阅读背景行为不变。`assembleDebug`、Android 37 模拟器 `installDebug` 均成功，MainActivity 启动且 logcat 未见 AndroidRuntime／SQLite 异常；信息页视觉仍待圆屏验收。
- Comment REQ-022（日志上传隐私说明主题迁移）：上传前说明页从强制纯黑容器改用统一 `WatchSurface`，文本、按钮沿用当前 Material 配色；隐私说明、用户主动确认与上传回调不变。`assembleDebug`、Android 37 模拟器 `installDebug` 均成功，MainActivity 启动且 logcat 未见 AndroidRuntime／SQLite 异常；页面视觉仍待圆屏验收。
- Comment REQ-022（加群二维码页主题迁移）：加群二维码页面从固定纯黑容器改用统一 `WatchSurface`，二维码、群号和无障碍说明保持原样；二维码生成仍在后台执行。`assembleDebug`、Android 37 模拟器 `installDebug` 均成功，MainActivity 启动且 logcat 未见 AndroidRuntime／SQLite 异常；设备扫码识别与主题视觉仍待圆屏验收。
- Comment REQ-022（抖音频道信息页主题迁移）：抖音频道信息页从固定纯黑容器改用统一 `WatchSurface`，频道标题、登录状态、更新时间和操作继续使用 Material 语义色；刷新及跳转回调不变。`assembleDebug`、Android 37 模拟器 `installDebug` 均成功，MainActivity 启动且 logcat 未见 AndroidRuntime／SQLite 异常；页面视觉仍待圆屏验收。
- Comment REQ-022（备案二维码页主题迁移）：备案页面改用统一 `WatchSurface`，二维码标题、网址、二维码本体及页面无障碍说明保持不变；移除外层固定黑底以适配应用主题。`assembleDebug`、Android 37 模拟器 `installDebug` 均成功，MainActivity 启动且 logcat 未见 AndroidRuntime／SQLite 异常；二维码实机识别和圆屏视觉仍待设备验收。
- Comment REQ-022（隐私政策确认页跟随阅读主题）：确认页正文文字和底部操作栏改用当前阅读预设解析出的背景／内容色，浅色预设不再出现白字黑底与正文背景不协调；阅读链接、同意与拒绝动作保持不变。Android 37 `installDebug` 成功，显式启动 MainActivity 后应用进程存活；按应用 PID 筛选未见崩溃或 SQLite 异常。确认页主题／同意路径的页面级回归与圆屏视觉仍待验收。
- Comment REQ-022（抖音登录页外层背景跟随主题）：登录页容器从固定黑色改为 Material 背景，避免登录成功视图使用 `onBackground` 前景色却叠在黑底上导致浅色主题对比错误；二维码扫描区域保留黑底和白色提示以维持扫码对比，错误视图继续使用主题色。页面行为不变。
- Comment REQ-022（阅读器 AI 总结错误对比）：总结失败提示按当前阅读器正文色亮度选择深红／浅红，浅色和深色阅读预设下都保留错误辨识度，避免固定浅粉色落在浅色阅读背景上对比不足；重试入口、成功与生成中提示不变。
- Comment RSCH-004（部署配置透传）：生产与测试 Docker Compose 均显式透传 TTS provider、上游地址、模型、授权音色及服务端 API Key；生产默认 `volcengine_ark`（Agent Plan），测试默认 `volcengine`（V3 SSE），只有不经部署 Compose 且未设 provider 时，`Config::from_env` 才回退 MiniMax。两套环境的渲染校验通过，切换服务不需改镜像，但需在部署密钥环境配置已授权资源；未写入真实凭据。
- Comment RSCH-004（接受官方 SSE 完成事件）：火山方舟 V3 SSE 官方示例在 `code=20000000` 的成功完成事件后结束，不带 `[DONE]`；解码器现以成功完成事件和非空音频作为完成条件，不再误判官方响应为截断。若上游发送 `[DONE]` 仍校验其只能出现在成功完成事件之后，完成后额外数据仍拒绝。官方协议：[V3 SSE 响应示例与成功码](https://docs.volcengine.com/docs/doubao-voice/http-chunked-sse-unidirectional-streaming-v3?lang=en)。尚未用已授权音色联调。
- Comment REQ-022（协作者页面主题背景）：协作者页面外层改用 Material `background`，圆形内容区改用主题 `surface` 到 `background` 的渐变；名称、角色和卡片已使用的主题前景色保持一致，滚动和圆形遮罩逻辑不变。Android 37 `installDebug` 成功，MainActivity 启动后按应用 PID 检查未见崩溃／SQLite 异常；协作者页实际主题渲染仍待页面级与圆屏验收。
- Comment REQ-022（分享二维码页主题背景）：分享页不再用固定黑底覆盖 `WatchSurface`，外层背景改随 Material `background`；二维码面板、扫码错误回调、点击关闭和无障碍说明保持原样。页面视觉和不同屏幕扫码效果仍待设备验收。
- Comment REQ-022（抖音登录网络状态页主题背景）：登录检查与网络不可用两种状态从固定黑底改用 Material `background`，与已有主题文字／按钮色保持一致；状态检测文案、返回动作及可用性判断不变。Android 37 `installDebug` 成功，MainActivity 启动后按应用 PID 检查未见崩溃／SQLite 异常；两个状态页仍需页面级与圆屏视觉验收。
- Comment REQ-022（抖音播放历史列表主题背景）：历史列表外层背景改用 Material `background`，与既有主题标题、卡片文字及按钮保持一致；清空历史确认与打开条目行为不变。Android 37 `installDebug` 成功，MainActivity 启动后按应用 PID 检查未见崩溃／SQLite 异常；历史页视觉仍待页面级与圆屏验收。
- Comment REQ-022（B站收藏／稍后再看列表主题背景）：列表与下拉刷新容器从固定黑底改用 Material `background`，收藏夹选择、分页、刷新和条目操作保持不变。Android 37 `installDebug` 成功，MainActivity 启动后按应用 PID 检查未见崩溃／SQLite 异常；收藏页视觉仍待页面级与圆屏验收。
- Comment REQ-022（B站信息流页面背景）：信息流和侧滑菜单共用的页面底色从固定黑色改用 Material `background`；卡片媒体占位继续保留黑底，分页、拉动刷新与菜单手势不变。Android 37 `installDebug` 成功，MainActivity 启动后按应用 PID 检查未见崩溃／SQLite 异常；信息流视觉仍待页面级与圆屏验收。
- BUG-003（RSS 与 B站信息流）：封面明确加载与失败状态，失败支持点击重试，默认网络恢复 validated 时重试未成功项；已有封面不重新加载。
- BUG-004：预设运行时观察字体与背景记录，分块资源完整校验并落盘后通知阅读器更新；背景图片缓存包含资源版本。
- BUG-005：频道与未读数改由一个 Room 聚合查询返回，避免两个数据流合并产生暂态不一致。小说整书与章节流程仍待验收。
- BUG-006：阅读器窗口与导航栏底色随当前预设更新，覆盖正文以外暴露的窗口区域；截图所指具体分界仍需真机复现。
- Comment BUG-006（窗口与底部背景运行时一致）：新增 `ReaderWindowBackgroundTest` 实际启动 DetailActivity，应用两种不同活动预设颜色，分别断言窗口 decor 背景与 navigation bar 更新到同一颜色；测试通过 Android 11 API 30 模拟器原生运行。安装后 MainActivity 启动成功、AndroidRuntime 无异常，`git diff --check` 通过。此测试验证窗口颜色同步，不替代原截图所在实体圆屏的完整视觉复现。
- REQ-023：二维码附近标明使用 B站手机客户端，增加按需展开的扫码帮助，保留现有刷新与状态提示。
- REQ-026：搜索历史支持长按确认删除单条，保留整批清空；历史写入在 DataStore edit 内完成，避免读改写丢更新。
- REQ-024：RSS 与 B站信息流共用封面组件，可见卡片优先请求，命中内存缓存立即呈现，去掉滚动时延迟展示新封面的路径。
- REQ-028：下载手机端弹窗按实际 https 链接处理能力显示浏览器按钮；补包可见性声明，打开失败提示扫码，不再静默忽略异常。
- RSCH-007（B站范围）：有二维码且未成功登录时 keepScreenOn，页面离开或状态改变后恢复原值；未宣称设备矩阵验证完成。

## 验证

- 第一批：NotesActivityTest 与 assembleDebug 通过。
- 第二批：NotesActivityTest、ReaderPreset 相关单测与 assembleDebug 通过。
- 最终：`./gradlew :app:testDebugUnitTest assembleDebug` 通过，548 条 JVM 测试，零失败；`git diff --check` 通过。

EXT-001～003 属于待确认的 iOS 项目，未修改。

## 阅读闭环续修

- REQ-002：按内容链接类型统一原文与分享能力。TXT、EPUB 和内部导入内容不显示网页原文、无意义地址分享及正文“查看全文”操作；普通网页文章保留。增加内容能力矩阵测试。
- Comment REQ-002（小说隐藏网页专属操作）：阅读器以内容链接统一计算能力，TXT／EPUB／内部导入内容不显示网页原文与分享入口；普通网页仍保留。复跑 `ImportedContentIdsTest`，6 项通过，覆盖小说、本地内容及普通网页边界。手表界面显隐仍待真机验收。
- REQ-004：在阅读控制面板与自动滚动设置页增加持久化“点击正文控制滚动”开关，默认关闭；正文双击仍可打开面板，开始／暂停在面板操作。到底时停止并解释原因，不自动回顶；设置入口改为设置图标，不改 TTS 状态。控制页支持触摸及表冠滚动，工具按钮保持独立事件目标。增加与自动开始开关独立的状态测试。
- Comment REQ-004（自动滚动控制与正文触控分离）：正文单击仅在用户开启持久化“点击正文控制滚动”后才切换播放；默认关闭时由控制面板开始／暂停，双击正文仍打开面板，TTS 独立。到底时不自动回顶并给出提示。`DetailViewModelTest` 与 `SettingsRepositoryTest` 定向测试通过，覆盖播放会话覆盖偏好及开关／速度持久化。控制面板真实表冠与触控仍待圆屏验收。
- REQ-006：手表预设页增加 10–64 sp 正文字号滑块，松手保存到当前预设；安全默认预设创建可持久化副本。主题继续独立选择，不删除现有预设；阅读器菜单增加“字号与主题”入口，返回后由现有响应式预设更新生效。
- Comment REQ-006（手表字号与主题分开设置）：阅读器菜单可进入预设页，10–64 sp 滑块松手后保存字号；若当前为安全回退预设则创建并激活持久副本；浅色／深色／系统主题选择保持独立，未删除原预设。`ReaderPresetModelsTest` 与 `WatchReaderThemeScheduleTest` 定向回归通过。滑块圆屏可操作性及返回后即时阅读效果待真机验收。
- 当前 ADB 仍无设备，圆屏布局、手势与阅读位置需真机验收。最终 `./gradlew :app:testDebugUnitTest assembleDebug` 通过，550 条测试零失败；`git diff --check` 通过。

## 小说章节续修

- REQ-001：EPUB 与分章 TXT 的目录标题下增加“继续阅读”入口。使用完整章节元数据中最近的 readingPositionChangedAt 定位，而非仅在已加载的列表页寻找；首次阅读打开首章，重读前面的章节后按实际最近位置恢复。不加载整书正文。
- Comment REQ-001（从书籍入口继续上次阅读）：目录直接展示最近章节入口，按最近一次阅读位置而非当前滚动页计算；尚无阅读记录时回到首章。已复跑 `NovelNavigationTest`，3 项测试覆盖最近阅读恢复及章节导航／书籍隔离。真实 EPUB/TXT 导入与圆屏入口仍待设备验收。
- Comment REQ-001（打开章节即更新续读目标）：EPUB／分章 TXT 章节详情首次加载时更新该章的阅读位置时间，并沿用 `readingProgress` 同步事件；即使本次停在顶部、进度值未变化，“继续阅读”也会指向最近打开的章节，正文进度字节与百分比不变。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，日志未见 AndroidRuntime／FATAL／SQLiteException；真实跨设备续读仍待验收。
- Comment REQ-022（配对二维码渐变页文字对比）：背景渐变期间标题、说明、同步状态与操作文字现在按所在背景的亮度切换黑／白前景；移除反向连续灰度混色，避免中间帧文字与背景接近而难以阅读，同时保留二维码背景渐变和按钮自身的明暗过渡。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；渐变视觉仍待圆屏验收。
- Comment REQ-022（删除确认按钮图标主题对比）：删除按钮图标前景从默认 `onSurface` 改为与错误色按钮背景配对的 `onError`，避免浅／深主题中图标颜色与危险操作底色失配；确认流程和取消按钮不变。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；弹窗视觉仍待圆屏验收。
- Comment REQ-022（图片预览黑底文字对比）：图片加载失败提示与本地图片缺失时的“手机同步”入口现在显式使用白色前景，匹配始终为黑色的媒体画布，避免继承较暗的 `onSurfaceVariant` 前景而降低黑底对比；加载状态、图片手势和外层主题不变。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；失败态圆屏视觉仍待验收。
- Comment REQ-022（设置步进按钮跟随主题）：字号与图标步进控件的两套圆形按钮背景从固定深灰资源改为 `surfaceVariant`，使按钮底色随 Material 主题与 `onSurface` 前景一致配对；深色主题仍保留次级表面层级，点击、按压态和数值更新逻辑不变。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；浅色设置页视觉仍待设备验收。
- Comment REQ-022（共享设置步进按钮跟随主题）：共享字号／图标步进按钮的按下和静止背景由固定 pill 资源改为 Material `surfaceVariant` 混色，文本与图标继续使用 `onSurface`；主题配对、48dp 命中区与数值操作保持一致。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；实际控件视觉仍待圆屏验收。
- Comment REQ-022（共享设置行跟随主题）：通用设置 pill 行从固定深灰背景改用 Material `surfaceVariant`，与其 `onSurface` 标签和图标匹配；行高、点击语义、内容槽与调用方间距不变。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；实际设置页视觉仍待圆屏验收。
- Comment REQ-022（个人中心入口跟随主题）：个人中心功能 pill 的固定深灰背景改为 Material `surfaceVariant`，与主题 `onSurface` 图标和文字配对；卡片顺序、标签、点击目标与 48dp 触控规格不变。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；实际主题外观仍待圆屏验收。
- Comment REQ-022（关于页入口跟随主题）：关于页自介、隐私、协议、许可和贡献者入口统一使用 `surfaceVariant`，替换固定深灰背景并匹配主题前景；入口文本、辅助说明及跳转回调保持原样。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；实际主题外观仍待圆屏验收。
- Comment REQ-022（联系开发者入口跟随主题）：加群和上传日志 pill 改用 Material `surfaceVariant`，与主题图标／文字前景配对；原有按钮语义、尺寸、回调和二维码页流程不变。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；实际主题外观仍待圆屏验收。
- Comment REQ-022（B站设置卡片跟随主题）：常规设置与退出／删除危险操作卡片的固定深灰背景改为 Material `surfaceVariant`，标签、错误色文字和图标继续使用各自语义前景；设置开关、危险操作状态、48dp 命中区与原回调不变。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；设置页实际主题视觉仍待圆屏验收。
- Comment REQ-022（RSS 频道设置卡片跟随主题）：常规设置与清空／删除危险操作卡片的固定深灰背景改为 Material `surfaceVariant`，普通文字与危险文字继续分别使用 `onSurface`、`error`；开关、危险操作可用状态、48dp 点击范围与原回调不变。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；频道设置页实际主题视觉仍待圆屏验收。
- Comment REQ-022（抖音频道信息动作跟随主题）：登录、设置、历史和标记已读操作按钮从固定深灰底改为 Material `surfaceVariant`，与 `onSurface` 文本和禁用态透明度相配；按钮尺寸、语义及原导航／动作回调不变。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；频道信息页主题外观仍待圆屏验收。
- Comment REQ-022（RSS 频道信息动作跟随主题）：设置、刷新、分享与标记已读操作从固定深灰底改为 Material `surfaceVariant`，文字仍按 `onSurface` 和可用状态显示；按钮语义、禁用条件、尺寸与原有回调保持不变。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；频道详情页圆屏外观仍待验收。
- Comment REQ-022（B站频道信息操作跟随主题）：搜索、稍后再看、历史、收藏夹和登录操作按钮的固定深灰底改为 Material `surfaceVariant`，文本继续按 `onSurface` 与启用状态呈现；尺寸、禁用条件、无障碍语义与导航回调不变。Android `:app:installDebug` 成功，模拟器已启动 MainActivity，最近日志未见 AndroidRuntime／FATAL／SQLiteException，`git diff --check` 通过；频道信息页圆屏主题外观仍待验收。
- Comment REQ-022（收藏列表撤回按钮跟随主题）：撤回浮动按钮背景从固定深灰改为 Material `surfaceVariant`，与 `onSurface` 撤回图标配对；48dp 触控下限、浮动位置、撤回状态与点击逻辑不变。 Android `:app:installDebug` 成功，Pixel 10 Pro（Android 17）已启动 MainActivity，最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。浅色／深色主题下撤回按钮的实机视觉仍待圆屏验收。
- REQ-003：章末提供上一章／下一章，按书籍来源过滤并按数字章节编号排序，跨万章时也不使用字符串顺序；首末章显示边界并禁用无效动作。切换前保存当前位置，失败不跳转并可重试；退出当前章节 Activity，避免堆叠阅读页面。不默认自动跨章。
- Comment REQ-003（上一章／下一章）：章节尾部提供明确导航，按当前书籍和数字章节顺序确定相邻章；首末边界按钮禁用并显示状态。切换前强制保存阅读位置，保存失败留在原章并提示重试；成功后停止自动滚动并关闭旧阅读 Activity，不自动跨章。`NovelNavigationTest` 3 项通过，`installDebug` 成功并启动 MainActivity；logcat 无崩溃或 SQLite 异常。实际章末圆屏布局与触控待真机验收。
- REQ-002 补充：补齐手机端既有 import-txt-novel 分章 TXT 地址识别，其原文与内部地址分享入口也隐藏。
- 验证：增加章节排序、书籍隔离、首末边界、最近阅读恢复、分章 TXT 地址识别的测试。ADB 无设备；`./gradlew :app:testDebugUnitTest assembleDebug` 通过，553 条测试零失败，`git diff --check` 通过。

## TTS 悬浮控制续修

- REQ-005：公共主题层接入单一应用内悬浮入口，直接观察应用级 ReadAloudController；页面切换不创建第二个播放会话，停止后入口随真实状态消失。专门的朗读播放页保留原有完整控制。
- Comment REQ-005（朗读停止入口）：应用内控制浮钮持续绑定唯一应用级朗读控制器，页面切换不新建会话；展开后可暂停／继续／停止，收起仅关闭面板。圆屏安全范围、吸边位置及极小视口边界测试通过。该验证为布局计算单测，真实圆屏拖动与音频中止行为待设备验收。
- 支持拖动并吸附左右边缘，圆屏停靠按圆形安全范围计算，停靠侧及高度在应用内跨页面保留；拖动不调用任何播放动作。展开面板提供实际播放／暂停状态、失败重试及明确“停止朗读”；读取和合成阶段仍可停止。
- 展开面板适配整屏并可触摸／表冠滚动。返回或“收起，保留朗读”只关闭面板，不停止音频；没有申请系统悬浮窗权限。应用后台音频规则沿用已有控制器，未新增后台保活机制。
- 浮层位于阅读内容的过度滚动容器外，避免随正文回弹移动；公共宿主通过 CompositionLocal 防止主题嵌套时重复显示。
- 验证：新增圆屏按钮四角安全范围、左右吸边及小视口边界测试。首轮 555 条 JVM 单测和 Debug 构建通过；面板滚动适配后 `./gradlew :app:testDebugUnitTest assembleDebug` 再次通过（555 条测试零失败），`git diff --check` 通过。ADB 仍无设备，跨页触控与实际音频停止待真机验收。

## 备忘录图片编辑续修

- REQ-007：Markdown 图片引用与 HTML img 在编辑区显示为简短的“〔图片 n〕”占位，实际保存、同步与预览继续使用原始图片引用及既有稳定资源路径；不迁移数据、不把展示序号当作图片 ID。
- 选区在图片边界停靠，表冠将图片作为一个对象跳过。输入法退格或选区替换若触及部分引用，按完整图片引用处理，避免产生半截资源路径；撤销／重做可恢复原引用。
- 保留普通正文的中文组合输入状态；围栏代码块与行内代码中的图片语法仍按普通文本编辑。自绘光标、可见区域定位及按视觉行移动使用同一偏移映射，避免占位缩短后光标错位。
- 验证：新增展示、映射单调性与边界、代码块、原子删除与撤销、中文组合输入、表冠跳过图片、跨多图替换测试。`./gradlew :app:testDebugUnitTest assembleDebug` 通过，562 条测试零失败，`git diff --check` 通过。ADB 无设备，真实输入法与圆屏编辑交互待验收。
- Comment REQ-007（图片引用以图片块呈现）：编辑区将 Markdown／HTML 图片路径显示为紧凑编号块，底层原文及稳定资源路径不变；删除或选区替换按完整引用处理，表冠跨过图片对象，普通中文组合输入和代码内语法保持原样，撤销／重做恢复原引用。`NoteImageEditingTest` 7 项定向回归通过。实际输入法与圆屏编辑体验待设备验收。

## 首页排序续修

- REQ-008：首页长按频道或备忘录进入操作菜单后可打开独立“首页排序”页，包含全部可移动频道及备忘录入口；通过拖动把手或上下箭头调整草稿，正常首页滚动及打开不承担排序手势。
- 保存前明确说明：按草稿顺序展示并取消现有置顶。取消或返回不保存；保存期间禁用重复提交及返回。对频道集合做完整性与去重校验，编辑期间新增／删除频道时拒绝覆盖，提示重新打开排序页。
- 频道位置、RSS 源同步状态和同步变更日志在同一 Room 事务内写入；排名值大于既有排序版本，沿用现有按源更新合并规则。备忘录位置使用已有本地位置存储，内置入口及备忘录位置不承诺跨端同步，页面有明确说明。
- 验证：新增头中尾移动、取消草稿不变、边界、重复项、缺失项与并发新增频道校验测试。最终 `./gradlew :app:testDebugUnitTest assembleDebug` 通过，565 条测试零失败，`git diff --check` 通过。ADB 仍无设备，圆屏拖动、持久化重开及跨端顺序验收待真机。
- Comment REQ-008（独立首页排序）：频道和备忘录先进入可取消的独立草稿排序页，通过拖动把手或上下箭头排序；保存要求频道集合完整且无重复，期间发生增删会拒绝覆盖。保存采用 Room 事务写频道顺序与同步状态／日志，关闭置顶；备忘录与内置项明确保存在本机。`HomeOrderDraftTest` 与 `HomeEntryOrderingTest` 共 5 项回归通过，覆盖头中尾移动、边界、完整集合校验和首页展示顺序。圆屏拖动、取消／重开及跨端排序仍待设备验收。

## 抖音播放会话续修

- REQ-009：普通沉浸入口在完成已有说明确认、登录及内容到达后自动进入首条；复用既有首条立即播放路径。播放器池初始静音，新会话／回到标题页重置静音；前台恢复和预热结束按最新会话状态恢复音量，避免旧协程把静音改回有声。静音时保留明确“开启声音”，不因静音开播主动改变系统音量；用户开启声音才执行既有可选音量保护。
- Comment REQ-009（首条静音自动播放）：首个视频就绪后沿用现有立即播放路径，播放器池与会话初始音量保持 0；开启声音是显式操作。返回标题页重置静音，新会话按静音状态播放；前台恢复／预热使用最新会话静音值，不让旧协程恢复声音。`DouyinImmersiveScreenPagingTest` 与 `DouyinEntryResumeSupportTest` 定向回归通过。实际首帧发声时序与设备音量体验待真机验收。
- REQ-010：保留单击显示／隐藏控制、双击播放／暂停的现有手势，增加明确暂停／继续按钮；菜单四秒收起，暂停时保留“已暂停 · 控制”入口。长按期间不触发菜单定时收起，以免打断临时倍速。
- Comment REQ-010（暂停时保留控制入口）：保留单击显隐与双击播放／暂停；控制栏 4 秒收起后，暂停状态仍显示“已暂停 · 控制”入口，静音仍保留“开启声音”。长按临时倍速期间不启动自动收起计时，释放后恢复计时。`DouyinImmersiveScreenPagingTest` 定向回归通过；真实暂停提示可见性和触控仍待真机。
- REQ-012：长按画面临时 2 倍速，保存实际按下前速度；松手、手势取消、离开页面或生命周期暂停均恢复原速度，重复开始不会把临时速度误当作原速度。原长按更多操作迁移到可见“更多”按钮。
- Comment REQ-012（长按期间临时 2 倍速）：按下只保存一次实际播放速度并设为 2×；松手、手势取消、页面失活、生命周期暂停／停止或释放组件都会恢复该速度。重复 begin/end 不覆盖原速度或多次误恢复；更多操作已有独立可见入口。`TemporaryPlaybackSpeedTest` 与 Douyin 手势回归通过，`installDebug` 成功并启动 MainActivity，logcat 无崩溃／SQLite 异常。真实长按触控及播放引擎速率恢复待设备验收。
- REQ-029：在同一播放器／Pager 会话切换“单条沉浸”，关闭上下滑切片，不重建媒体、进度或音量；显式退出或返回先退出模式。暂停失败自动跳片与丢弃当前条目的路径，避免单条模式被错误恢复流程拉到下一条。
- Comment REQ-029（同会话单条沉浸）：切换仅关闭原 Pager 的用户滑动，不重建播放器／媒体或更改进度、音量；返回键先退出单条模式。单条模式下失败隔离不丢弃条目，自动跳片任务被抑制，避免意外跳到下一条。`DouyinImmersiveScreenPagingTest` 定向回归通过；真实播放连续性、手势与退出位置仍待真机验收。
- 验证：新增临时速度恢复、重复触发、取消幂等和无活动手势测试；最终 `./gradlew :app:testDebugUnitTest assembleDebug` 通过，568 条测试零失败，`git diff --check` 通过。ADB 无设备，真实静音、首条播放、长按取消和模式切换待验收。

## 播放控制面板续修

- REQ-011：抖音可见控制区增加直接进度条与播放控制入口。面板第一层提供进度、暂停／继续、静音及音量；展开后提供 ±5／±15 秒、适应／填充／完整画面。未知时长或不可定位媒体禁用进度与跳转，时间标签不假设有效时长。拖动进度期间暂停控制区自动收起。
- 音量滑块复用 PlayerVolumeState 的用户音量调整路径，保留原有音量保护；不重新创建播放器或改变媒体来源。小屏控制区与面板均支持滚动。
- Comment REQ-011（抖音分层播放控制）：可见区有进度、暂停／继续、静音与播放控制入口；面板第一层提供进度、播放、音量和静音，展开层提供 ±5／±15 秒及适应／填充／完整画面。未知时长或不可定位时禁用进度和跳转，拖动时暂停控制栏自动收起；音量沿用系统音量及既有保护策略。`PlaybackControlPanelTest` 与 `PlayerVolumeOverlayPolicyTest` 共 21 项通过；`installDebug` 成功，MainActivity 已启动，logcat 无崩溃或 SQLite 异常。圆屏控件布局和实机媒体 seek 仍待设备验收。
- REQ-013：B站增加独立播放控制面板，保留既有双击播放／暂停与长按进度兼容入口；旋转入口打开面板，提供 0／90／180／270 度直接选择，平移滑块与画面回中放在展开层。双击显示播放状态文字反馈并展开控制。
- B站控制面板使用独立应用对话框，不随视频旋转；切换角度／模式先停止平移惯性并回中。关闭原生 Activity 侧滑关闭功能，返回经过现有画面平移边界判断，避免平台侧滑绕过该判断。
- Comment REQ-013（B站播放状态、进度与旋转控制）：独立对话框承载进度／音量及低频旋转和平移控制，视频角度提供 0／90／180／270 度选择；切换缩放或角度会停止平移惯性并回中，双击播放／暂停给出状态反馈。禁用系统侧滑关闭，Activity 返回经画面平移剩余边界判定。`BiliPlayerScreenScaleTest` 与 `PlaybackControlPanelTest` 定向回归通过。旋转画面触控和真实侧滑边界仍待设备验收。
- 验证：新增未知时长、跳转两端、Long 极值溢出与超过一小时时间标签测试。全量测试首轮暴露 OobeViewModelTest 未清理测试 ViewModel 协程的调度器竞态；修正为受控测试调度器并在 finally 取消等待 ViewModel 与 DataStore 作业，不跳过原测试。最终全量验证执行中，ADB 无设备。

### 播放控制最终验证状态审计

- 原记录的 `write_stdin session_id=3652` 当前已不存在，所指 `/tmp/watchrss-playback-panel-final-validation.log` 也不存在；无法从当前工作区证实那次 `testDebugUnitTest assembleDebug` 的最终结果，因此不据此宣称全量测试通过。
- 后续分别执行的 `:app:installDebug` 通过并启动 MainActivity；REQ-011、REQ-013 定向回归结果仍以各自工单 comment 中此前明确记录的结果为准。

## 小程序范围核查续修

- RSCH-005：已在 WatchRSS_WeChat/docs/current-scope.md 逐项记录账号、授权、恢复、云索引与正文、RSS、导入、支付路由、笔记透传与 BLE 的代码证据及边界，定义已有云阅读闭环的最小交付范围和本地演示顺序。
- 修正小程序 README 中“TXT/EPUB 尚不支持”与“完整双向同步”的过时／过宽表述；明确正文按需读取、笔记对象透传、BLE 固件与支付真机门禁。保留原有 app.json 未提交修改，没有重新挂载被移出的 purchase 页面。
- 小程序完整本地测试串行运行：103 项通过，零失败、零跳过。修正 cloud-library 测试 HTTP 桩缺少现有诊断接口的问题，未绕过原离线失败／重试断言。无真实短信、付款、发布或手表 BLE 操作。
- Android 最终验证 session_id=3652 仍在编译，保持原会话，不将其记为通过。小程序测试与文档盘点不替代真实微信及跨端联调。

## 统计口径续修

- RSCH-001：核对 Android 手机／手表的首次引导事件触发代码、服务端每日累计覆盖键及报表 SQL。后台原“下载量”实际聚合首次合格引导打开次数；已改称“首次引导事件报告”，按“今日／昨日／近 30 日／累计事件”展示。
- 页面区分事件、下载请求、APK 下载完成、安装成功和独立用户数；说明客户端按本地安装标志记录、清除数据或重装可能重新计数，以及服务端同日／同安装／同设备／同平台覆盖写入。功能排行改称事件次数并补齐两个首次引导事件的明确中文名。
- 保留现有事件 ID、数据库查询与 downloads 等兼容字段；不重算历史，不宣称上报链路已排除漏报。详细定义位于 WatchRSS_Backend/docs/telemetry-metric-definitions.md。
- 验证：两个现有管理页断言以 Rust 2024 标准库独立运行通过；执行当前 HTML 的三个实际渲染函数，窗口数值、次数单位、事件名、图表和空状态通过；差异检查通过。未跑全量 Cargo 集成或查询生产数据，后端已有其他未提交修改均保留。
- Android 最终验证 session_id=3652 再次轮询仍存活且日志仍在 compileDebugKotlin；未重启、未记为通过。ADB 仍无设备。

## 在线朗读与协议引导续修（验证中）

- RSCH-004：Backend 增加 provider=volcengine 的 V3 SSE 合成分支，保留原默认 MiniMax。服务端配置授权资源、音色与 API Key，客户端应用默认语音协议保持兼容。完整成功音频才返回／缓存，拒绝截断、上游错误及非法编码。32 条／8 MiB／10 分钟缓存，认证与成功调用次数配额继续生效。配置与边界见 Backend/docs/volcengine-tts.md。
- 真实模块的独立 Rust 测试通过：协议 3 项，缓存 2 项。新增本地 HTTP 集成测试，尚未运行。`cargo check --locked --lib` 正等待其他 Cargo 构建目录锁，session_id=40705，日志 /tmp/watchrss-volcengine-cargo-check.log。未部署或调用真实计费合成，未确认音质与费用。
- REQ-019：手机当前首次引导和扩展引导的继续按钮在未勾选时给就地原因并滚动定位到复选框，不代勾选、不触发后续动作；勾选后清除提示。手表端原有逻辑已具备未勾选提示和阻止前进。Phone 构建及单测 session_id=3944，日志 /tmp/watchrss-phone-consent-validation.log，尚未返回结果，不作完成 comment。
- Android 播放器最终验证仍使用 session_id=3652，日志 /tmp/watchrss-playback-panel-final-validation.log，无终态；ADB 无设备。

- REQ-021：手机 MainActivity 授权门禁与 AccountActivity 根页加入 PublicLegalLinks，未登录与未购买均可查看三类协议及关于／备案；AboutScreen 汇总增加付费协议。使用现有 LegalDocumentActivity 和 AboutActivity，不新增备案或主体信息。Phone session_id=3944 尚在 Gradle 启动阶段，包含本次修改，未报告通过。

- REQ-021 路径复核：PhoneCompanionApplication 的三层首次使用／授权生命周期守卫原先没有一致放行关于和法律文档；已仅为 AboutActivity、LegalDocumentActivity 补公开信息例外，避免未付费打开后立即被重定向。授权业务门禁保留。
- RSCH-004 HTTP 集成测试排队 session_id=72873，日志 /tmp/watchrss-volcengine-http-tests.log，等待 artifact 目录锁；5 项独立模块测试仍是当前已完成验证。

## B站详情与循环续修

- 原验证句柄 3652／3944／40705／72873 均已不存在，进程列表无对应 Gradle／Cargo 任务，旧日志无成功终态。重新验证没有停止其他任务。
- 首次恢复手表全量验证暴露新增循环复选框未走 WatchCheckbox 封装；修正后再次执行 `./gradlew :app:testDebugUnitTest assembleDebug`，571 项测试零失败，BUILD SUCCESSFUL。日志 /tmp/watchrss-bili-details-loop-validation.log。ADB 仍无设备。
- Comment REQ-011：分层播放控制已通过全量单测及 APK 构建，未知时长与跳转边界单测保留，真机触摸和圆屏布局待验收。
- Comment REQ-013：独立控制面板与旋转／平移设置通过上述验证，实际手势和返回边界仍待真机。
- Comment REQ-014：复用独立 BiliDetailActivity 完整展示标题，为点赞／投币／收藏／分享增加文字标签，系统分享和扫码分享分开表达并说明链接去向。播放器保留播放控制与详情两个文字入口，低频画面控制归入面板；进入详情先暂停，返回保留原播放会话而不承诺手机续播。
- 验证：本轮 `BiliActivitySmokeTest` 在 API 30 模拟器 11 项通过，包含详情 Activity 启动路径；调试 APK 安装后 MainActivity 启动，logcat 无崩溃或 SQLite 异常。冒烟测试不证明超长标题及所有分享目标的完整像素布局，圆屏适配仍待设备验收。
- Comment REQ-025：当前 B站播放器会话内提供单条循环复选框，使用 ExoPlayer REPEAT_MODE_ONE／OFF，重建来源时保留本次设置；离开屏幕会话后恢复关闭，无额外定时循环任务。循环边界待真机。
- RSCH-004 整体验证失败：Backend 当前 SQL 类型化工作导致 43 处编译错误，涉及 checked_sql、battery_observability、tts/quota 等，HTTP 集成测试未进入执行。日志 /tmp/watchrss-volcengine-http-resumed-tests.log。保留其他现有改动，不把 5 项协议／缓存单测视为整体后端通过。
- Phone 重新验证 session_id 待工具返回，日志 /tmp/watchrss-phone-public-consent-resumed-validation.log。

## 收藏夹与手机同步详情续修

- Comment REQ-027：收藏夹标题显示当前名称，点击选择全部可用收藏夹；按 folder ID 保存各自已加载列表与下一页，Compose SaveableStateHolder 分别保存滚动位置。切换取消旧任务并用请求版本拦截迟到响应；请求失败保留已有列表与错误提示，下一次加载仍请求原失败页，不跳页。空目录与失败分别表达。
- 新增四项 ViewModel 回归，覆盖多夹分页隔离／切回缓存、重复分页事件抑制、不可取消迟到结果、失败后原页重试、目录删除后选中态清理。手表全量 575 项测试零失败、零跳过，assembleDebug 通过，日志 /tmp/watchrss-favorite-folders-validation.log。
- Comment REQ-019：Phone 首次及扩展引导支持未勾选的就地提示与滚动定位；原手表校验保留。Phone 全量 446 项测试和 assembleDebug 通过，日志 /tmp/watchrss-phone-public-consent-resumed-validation.log，未执行真机交互。
- Comment REQ-021：公开协议与关于入口、生命周期门禁例外通过同一批 446 项测试和构建；具体协议内容及备案原数据未改动，待未付费实机路径验收。
- REQ-015：首页只显示当前阶段／简洁结果，取消阶段数值冒充整体百分比；任务详情显示实际阶段历史、当前阶段传输量和速度、完整完成／失败结果及技术日志导出。账号授权、阅读资源或词元子任务失败时，保留“部分项目未完成”和各项原因，不只用短暂 Toast 承载。阶段历史在 ViewModel 保存，32 条有界，开始新任务重置。新一轮 Phone 验证正在运行，日志 /tmp/watchrss-phone-sync-details-validation.log。

## 同步详情与权限说明验证

- Comment REQ-015：Phone 首页改为阶段与简洁结果，任务详情收纳真实阶段历史、当前传输数据、完整结果、错误和日志导出；不展示阶段数冒充整体百分比。部分子任务失败原因留在结果中，首页优先显示失败摘要。本轮 `:app:connectedDebugAndroidTest` 过滤运行 `SyncStatusCardRuntimeTest`，2 项原生 Compose 测试通过，覆盖无伪百分比、阶段详情、失败原因与显式日志导出。Phone Debug APK 安装后经 launcher 启动，logcat 无崩溃／SQLite 异常；真实手表同步与部分失败场景仍待设备验收。
- REQ-018：移除 HomeActivity 首次创建时申请蓝牙权限；重用已有授权启动现有服务。同步、设置同步和手表预览在用户操作后解释 CONNECT 的已配对连接用途，以及现有发现服务涉及的 ADVERTISE 用途。拒绝或不再询问后提供系统应用设置路径和本地导入／阅读说明，不自动重试、不申请定位／相机／录音。回调改为读取实际 CONNECT 状态，不以空 result.all 当作授权成功。权限细节依据 Android 官方蓝牙权限文档 https://developer.android.com/develop/connectivity/bluetooth/bt-permissions 。验证执行中，日志 /tmp/watchrss-phone-permission-guidance-validation.log。
- 当前有效验证：Phone 权限说明命令已启动；此前的全部旧句柄均不可据其日志假定仍存活。手表最近一次全量 575 项通过，手机同步详情最近一次 446 项通过，ADB 无设备。

## 首次引导续修

- Comment REQ-018：Phone 权限说明和拒绝路径全量 446 项测试及 assembleDebug 通过，日志 /tmp/watchrss-phone-permission-guidance-validation.log。尚无设备，首拒、永久拒绝、从系统设置返回以及 OEM 分组授权差异待验收。
- REQ-016：首次正常进入首页显示可随时跳过的四步引导（导入、配对连接、同步、手表查看），可直接前往对应入口；完成或跳过后持久化已看标记。外部分享／文件打开和指定目的地导航不弹入门页抢占原动作。空阅读资料库给直接导入与查看入门；同步任务详情也可复看。跳过仅关闭功能引导，不修改协议或授权状态。新一轮 Phone 全量验证已启动，日志 /tmp/watchrss-phone-quick-start-validation.log。

- Comment REQ-016：四步引导覆盖导入、配对、同步与手表查看；可跳过并持久化已看状态，空资料库直达导入，首页及同步详情可复看。修正 HomeActivity 已存在时收到 `ACTION_SEND`／`ACTION_VIEW` 或支持目的地新 Intent，及时隐藏当前引导而不写入“已看”，避免遮住外部导入／定向导航。`QuickStartEntryPolicyTest` 2 项与 `HelpCenterRuntimeTest` 2 项原生 Compose 回归通过；Phone Debug 构建安装并经 launcher 启动，logcat 无崩溃／SQLite 异常。真实分享接收、系统蓝牙页面返回与设备同步仍待验收。
- 本轮验证终态：手表 575 项与 APK 构建通过，手机 446 项与 APK 构建通过，均无 ADB 设备。后端 HTTP 集成未运行成功，仍受现有 SQL 类型化编译错误阻断。剩余需求保持待处理，没有将全部工单标记完成。

## 帮助中心与独立音频续修

- Comment REQ-017：Phone 帮助中心提供导入、同步、权限、阅读、媒体和账号的本地基础帮助，本版本功能说明，以及从既有公告接口加载的在线更新／帮助补充。公告缓存按运行环境区分，帮助页允许查看当前／旧版本说明而不改变自动更新弹窗规则；在线失败保留缓存与本地内容。未同意协议时只展示本地内容。个人页入口在窄屏和宽屏均进入帮助中心；同步任务详情链接对应 SYNC 主题，错误 footer 提供基础帮助入口。448 项测试、assembleDebug 通过，日志 /tmp/watchrss-phone-help-center-validation.log。
- REQ-017 定向复验：`PhoneAnnouncementRepositoryTest` 3 项通过，覆盖公告解析、过期／当前版本在帮助页可读而不重新弹更新、过大内容拒绝缓存；`HelpCenterRuntimeTest` 2 项连接模拟器 Compose 测试通过，覆盖无协议时本地帮助可读、在线刷新被门禁拦截且基础内容保留、入门复看／跳过。Phone APK 安装并启动正常，logcat 无崩溃或 SQLite 异常。在线公告是否已由项目方发布仍取决于 Backend 配置，未将远端内容发布视为本地可验证项。
- 在线内容维护沿用 Backend 的 WATCHRSS_PHONE_ANNOUNCEMENT_VERSION／CHANGELOG／DOWNLOAD_URL 配置：CHANGELOG 可加入帮助补充章节；本轮不部署公告或改生产配置，不宣称已发布远程内容。
- RSCH-006：SDK 已有 DASH audio 解析。新增独立音频源选择，只从 audio 数组选择 HTTPS 主／备地址，按有效带宽优先低码率；视频轨和复用 MP4 不作音频兜底。视频详情加入只听音频入口，媒体会话服务独立持有 ExoPlayer，支持系统媒体控制与自定义停止入口、应用内暂停／停止、页面返回后保持会话。播放器处理音频焦点和耳机断开；新 B站视频、朗读会话及抖音开启声音会结束独立后台音频，B站退出登录也停止。起播复用已保存视频位置和已有音量上限；音频会话内重新进入保留现有进度，不声称新增了音频冷启动断点持久化。
- 增加三个真实音频选路测试：禁止视频／复用流冒充音频、低带宽与备地址选择、拒绝本地／明文／带凭据地址。最终手表 578 项测试及 assembleDebug 通过，日志 /tmp/watchrss-bili-audio-final-validation.log。官方生命周期参考 https://developer.android.com/media/media3/session/background-playback 。实际来源可播放性、通知布局、熄屏、中断恢复、音质、流量及功耗收益仍需设备验证，RSCH-006 不作全部验收完成。
- 启动临时空白 AVD 尝试补运行证据：使用本机已安装 Android 37 ARM64 镜像，新数据目录 /tmp/watchrss-validation-avd.dieq1ta1，不复用用户 Pixel_10_Pro 数据。端口 5580，启动日志 /tmp/watchrss-validation-emulator.log。待工具返回有效句柄。

## 临时模拟器运行验证

- 临时 AVD 已正常启动，Android 37 ARM64／端口 5580，仅使用新建数据目录。已执行手表 `:app:installDebug`、`adb shell am start -n com.lightningstudio.watchrss/.MainActivity` 和 AndroidRuntime 日志检查，启动到使用前说明，无应用崩溃。Phone `:app:installDebug` 和 MainActivity 启动也通过。均为模拟器，未替代圆屏手表验证。
- RSCH-006 首轮真实运行测试发现 ExoPlayer.stop 后 playWhenReady 仍为 true：媒体已经停下，但停止意图状态未清。修正在停止前调用 pause，再 stop 和 clearMediaItems；保留原断言验证，没有放宽停止要求。
- 新增 BiliAudioServiceTest：空会话不会自动播放且拒绝 file URL；显式参数授权的官方音频网络测试验证音频进入 READY／PLAYING 后销毁 Activity、释放全部控制器、熄屏，再连接时仍保留媒体与推进中的进度，随后暂停／继续／停止清空状态。修复后 2 项全部通过、零跳过，`connectedDebugAndroidTest` 成功，日志 /tmp/watchrss-audio-instrumented-final-validation.log。官方测试资源取自 https://github.com/android/platform-samples/blob/main/samples/media/video/src/main/java/com/example/platform/media/video/TransformerVideoComposition.kt ，无 B站登录或 Cookie。
- 这证明当前 Android 37 模拟器上的媒体服务生命周期和实际解码路径，不证明 B站 DASH CDN、OEM 通知布局、真实耳机断开、系统来电或功耗收益；RSCH-006 保持待这些验收。
- Phone HelpCenterRuntimeTest 已构建，正在同一空白模拟器验证离线帮助、协议前不联网提示、四步引导复看与跳过，并保存帮助页截图；日志 /tmp/watchrss-phone-help-runtime-validation.log。

- Phone 原生运行验证：HelpCenterRuntimeTest 两项通过、零跳过。覆盖协议确认前离线帮助可读、点击在线刷新显示先确认协议且保持本地帮助，以及四步入门的复看、下一步和跳过；日志 /tmp/watchrss-phone-help-runtime-validation.log。测试后 AGP 清理了 Phone APK 和私有外部目录，截图暂未取回，未把缺失截图当作视觉验收证据。正仅重跑截图所在用例并请求保留测试 APK，不重跑无关测试。
- 模拟器 AndroidRuntime 日志未见应用崩溃；外部 adb 直接启动非导出 Activity 被系统拒绝属于预期组件保护，原生测试通过应用自身的 ActivityScenario 进入，未放宽导出权限。

## 后端整体验证依赖修复

- 保留现有 SQLx 类型化与所有查询检查，修正九类 Rust 适配错误：ExecuteTyped 生命周期约束；电量报表漏改的 count 字段和 String／&str；云密钥信封的两个匿名 Record 分支投影；LLM/TTS 配额在查询前统一身份参数；支付查单刷新前后投影为已有命名 DTO；mode_ms 明确 i32→i64 转换。未回退为运行时动态 Row 解码，也未改业务 SQL 内容。
- 再次编译已无这些 Rust 类型错误，剩余 34 个宏错误对应 17 条 SQL 缺少真实离线元数据。为此新建完全隔离的本机 PostgreSQL 17 测试集群，目录 /tmp/watchrss-goal-pg.p4bnim0_，端口 63006，数据库 watchrss_validation；定位信息在 /tmp/watchrss-goal-pg-location.json。没有连接项目 .env 的数据库。
- tests/supabase_stubs.sql 与全部迁移已按完整文件名顺序、逐文件事务重放成功，日志 /tmp/watchrss-goal-schema.log。实际当前 0034 迁移已经修正旧文档所述语法与管理员表问题，本次未跳过迁移。
- 正在该测试库上执行项目 tools/prepare-sqlx.sh（会真实校验全部目标并生成元数据），日志 /tmp/watchrss-sqlx-metadata-repair.log。尚未宣称后端整体通过；完成后需回到离线模式跑火山 HTTP 集成与相关测试。
- 帮助页截图已在保留测试 APK 的单用例重跑后取回，路径 Android_WatchRSS_Phone/docs/verification/help-center-offline-android37.png；已逐图检查标题、主题选择、正文与版本说明边界，文字可读无截断。截图仅支持该手机帮助页布局，不能代替同步页或圆屏验证。

## 后端、支付与样式试点验证续记

- SQLx 真实测试库全目标 prepare 成功，元数据由实际查询生成；离线模式 159 项库测试通过（3 项默认忽略），checked_sql 集成 2 项与火山 HTTP 集成 2 项通过。日志 /tmp/watchrss-sqlx-metadata-repair-final.log、/tmp/watchrss-backend-typed-tts-validation.log。此前“类型化编译阻断”的记录为历史状态，当前已解除。
- RSCH-002：在既有真实数据库／模拟微信提供方测试中，加入原迅虎已付款订单和容量包；签名微信付款及重复回调后累计容量正确，完整退款及重复／迟到回调后仍保留旧渠道权益。5 项微信支付测试全部通过（显式运行 ignored 数据库测试），日志 /tmp/watchrss-payment-legacy-rights-validation.log。未操作生产库、真实收款或部署；企业主体资质、最终商户渠道与商业迁移规则仍需实际配置后联调，不据此标为完成。
- Comment REQ-022：Phone 同步卡片作为高频页样式试点，明确状态／主操作／任务详情层级；主按钮高度及详情触摸区域至少 48dp，容器上文本与忙碌按钮使用明确对比色，忙碌时显示“同步中”。2 项真实 Compose 原生测试通过，覆盖阶段而非伪整体百分比、详细结果、忙碌日志禁用与显式导出，日志 /tmp/watchrss-phone-sync-pilot-verified.log。截图 Android_WatchRSS_Phone/docs/verification/sync-card-pilot-android37.png 已检查文字及控件边界。未修改返回动画，未声称所有页面迁移完成。
- Comment REQ-022（Watch 信息流渐进迁移）：RSS／B站信息流卡片与 RSS 搜索摘要共用易读摘要样式，字号从 9sp 提升到 12sp 并移除半透明主题文字，保留现有标题截断与页面转场。`:app:installDebug` 成功，`HomeFeedListActivityTest` 2 项原生导航回归通过；冷启动截图曾短暂出现 Android “System UI isn't responding”提示，选择等待后首次引导正常呈现；保留日志未见 ANR 报告、应用崩溃或 SQLite 错误，故不将这次慢启动记为无条件通过。Feed 内容视觉仍需在真实圆屏设备复核；其余页面仍待逐步迁移。
- Comment REQ-022（文章阅读工具栏迁移）：阅读页圆形动作视觉仍保持 32dp，但可点区域扩至 48dp；文字动作按钮最小高度设为 48dp，主题和已存在的转场保持不变。新增 Compose 原生回归断言图标动作边界至少 48dp 且仍可点击；`DetailHeaderActionsTouchTargetTest` 1 项通过，测试后 `installDebug` 成功，MainActivity 启动且 logcat 无 ANR／崩溃／SQLite 错误。真实圆屏拥挤度仍待设备验收。
- Comment REQ-022（收藏列表主题与触控迁移）：收藏／稍后看列表背景改用 `MaterialTheme.colorScheme.background`，不再固定为黑色；撤回按钮最小触控区域为 48dp。新增 Compose 原生用例验证按钮边界与点击，`SavedItemsScreenStyleTest` 1 项通过；测试后 `installDebug` 成功，MainActivity 启动且 logcat 无 ANR／崩溃／SQLite 错误。切换明暗主题的真实圆屏视觉及剩余页面迁移待验收。
- Comment REQ-022（首页频道入口迁移）：首页背景跟随主题 background；个人中心入口尺寸至少 48dp（若设备资源更大则保留更大值）。首页 Compose 测试增加范围断言，并修正未读点断言使用未合并语义树以准确检查装饰节点；`HomeComposeScreenTest` 8 项全部通过。测试后 `installDebug` 成功，MainActivity 启动且 logcat 无 ANR／崩溃／SQLite 错误。真实圆屏上的明暗主题与点按验收仍待设备。
- Comment REQ-022（设置备案入口迁移）：设置页备案链接改为至少 48dp 高的整行按钮，加入明确按钮角色与“备案信息”无障碍名称，保持原备案文字和打开动作。渲染用例验证边界，交互回归验证回调；SettingsScreen 三项原生用例分别通过（渲染／停用音量选项与交互复跑）。测试后 `installDebug` 成功，MainActivity 启动且 logcat 无 ANR／崩溃／SQLite 错误。真实设备上的圆屏留白仍需验收。
- Comment REQ-022（添加 RSS 输入页可读性迁移）：地址输入框示例占位文字与 focused/unfocusedPlaceholderColor 不再在语义次级颜色上额外叠加 0.7 透明度，保留 label、输入正文与禁用态色阶；避免圆屏小尺寸下占位提示过淡。`AddRssScreenTest` 4 项通过；测试后 `installDebug` 成功，MainActivity 启动且 logcat 无 ANR／崩溃／SQLite 错误。真实圆屏输入视觉仍待验收。
- Comment REQ-022（设置步进控件触控迁移）：字体、静音开播音量上限、缓存上限和媒体缓存的增减按钮从 20dp 紧凑图标改为独立的 48dp 最小触控区域，数值仍展示在设置项标题行；边界值禁用状态、无障碍名称及原有增减回调保留。`SettingsScreenTest` 3 项通过，测试断言按钮宽高至少 48dp；测试任务安装成功，启动 MainActivity 后 logcat 无 AndroidRuntime／SQLite 错误。真实圆屏上的垂直空间与单手操作仍待设备验收。
- Comment REQ-022（自动滚动控件触控迁移）：速度加减按钮保留原 32dp 圆形视觉，点击区域扩大到 48dp；阅读中播放／暂停目标同样至少 48dp，调整速度的行高同步扩展以免压缩触控区。`AutoScrollControlActivityTest` 2 项通过并断言两组控件宽高；测试任务安装成功，启动 MainActivity 后 logcat 无 AndroidRuntime／SQLite 错误。真实圆屏上相邻控件的误触率仍待设备验收。
- Comment REQ-022（朗读页触控与滚动迁移）：上一条、下一条、播放／暂停、停止、自动下一篇、语速、当前文章入口触控目标均至少 48dp，圆形图标视觉尺寸保留；增加纵向触摸滚动，窄高圆屏可访问完整控制面板。`ReadAloudPlaybackScreenTest` 通过，覆盖 8 个入口的最小尺寸与当前文章点击回调；测试任务安装成功，启动 MainActivity 后 logcat 无 AndroidRuntime／SQLite 错误。真实圆屏上的滚动及控件排列仍待设备验收。
- Comment REQ-022（个人中心备案入口迁移）：备案链接从只包住小号文字的点击区域扩展为整行至少 48dp 高的按钮，补“备案信息”无障碍名称与按钮角色，保留现有备案文字及打开回调。`ProfileScreenTest` 4 项通过，新增宽高断言；测试任务安装成功，启动 MainActivity 后 logcat 无 AndroidRuntime／SQLite 错误。真实圆屏边缘布局仍待验收。
- Comment REQ-022（关于页操作入口迁移）：项目自介、隐私政策、用户协议、开源许可与贡献者入口统一显式为按钮语义，最小高度 48dp，保留现有标签、圆角和点击路径。About 专项原生测试新增高度断言并通过，点击回调仍按顺序触发。首次全类测试发现备案二维码和加群页断言搜索了未渲染文案；已在下一 comment 校正断言并复跑全类通过。真实圆屏视觉待验收。
- Comment REQ-022（二维码页面回归断言对齐）：备案与加群页面已经给二维码标题／副标题设置了专用无障碍描述；旧测试却搜索未渲染的文案。断言现使用实际可见标题的未合并语义节点、实际群号文案和完整二维码描述，不改产品页面内容。`StaticCommonScreenTest` 7 项全通过；测试任务安装成功，启动 MainActivity 后 logcat 无 AndroidRuntime／SQLite 错误。
- Comment REQ-022（联系开发者入口触控语义迁移）：加群、上传日志按钮保留图标和 pill 样式，入口最小高度至少 48dp，并通过 `clickable(role = Role.Button)` 暴露按钮角色；原有文字标签与回调不变。`StaticCommonScreenTest` 7 项全通过，两个入口均新增宽高断言并验证原回调；测试任务安装成功，启动 MainActivity 后 logcat 无 AndroidRuntime／SQLite 错误。真实圆屏上的边缘触控仍待验收。
- Comment REQ-022（OOBE 主按钮触控迁移）：欢迎、协议、后续引导共用的主按钮最小高度设为 48dp，并显式使用按钮角色；原有颜色、圆角和文字布局保留。`OobeScreenTest` 6 项全通过，首次页和协议页的继续按钮均增加高度断言。测试任务安装成功，启动 MainActivity 后 logcat 无 AndroidRuntime／SQLite 错误。
- Comment REQ-022（B站搜索历史触控与对比度）：搜索历史关键词胶囊最小高度提升至 48dp，点击与长按删除仍作用于同一完整触控区域；补充搜索动作标签，并改用主题 `onSurfaceVariant` 文本色，避免颜色跟随主题时对比度不明。`assembleDebug` 与 `:app:installDebug` 通过，Android 11 模拟器启动 MainActivity 后无 AndroidRuntime 异常；日志中有一条 SQLite 双引号字符串警告。圆屏布局和实际长按仍待设备验收。
- Comment REQ-022（抖音信息流登录入口迁移）：未登录时的“登录”入口改为主题主色的按钮语义触控目标，最小高度 48dp，文字居中；原登录回调和信息流已登录内容不变。`assembleDebug`、`:app:installDebug` 通过，Android 11 模拟器启动 MainActivity 后无 AndroidRuntime 异常；抖音登录页实体圆屏触达仍待设备验收。
- Comment REQ-022（连接手机页主题背景迁移）：连接手机页从强制纯黑切换到统一 `WatchSurface` 主题背景，正文继续使用主题前景／次级文字颜色，页面内容和下载手机端操作不变。`PhoneConnectionScreenTest` 在 Android 11 模拟器通过（1 项），随后 `installDebug` 与 MainActivity 启动成功，AndroidRuntime 无异常；发布主题及实体圆屏视觉仍待设备验收。
- Comment REQ-022（频道设置危险操作迁移）：清空／删除频道整行保留原 52dp 点击区域，增加按钮角色和动作标签；危险文字及图标颜色改用 `MaterialTheme.colorScheme.error`，跟随深浅主题的语义色。`assembleDebug`、`:app:installDebug` 通过，Android 11 模拟器启动 MainActivity 后无 AndroidRuntime 异常；频道设置页主题截图与实体圆屏仍待验收。
- Comment REQ-022（B站频道设置主题与危险操作迁移）：B站设置页改用统一 `WatchSurface`，退出登录／删除频道保留整行 52dp 点击范围，增加按钮角色与操作标签；危险颜色改用 `MaterialTheme.colorScheme.error`。新增 `BiliSettingsScreenTest` 验证按钮角色、启用状态与两个原回调。Android 11 模拟器原生测试通过（1 项），测试后安装并启动 MainActivity，无 AndroidRuntime 异常；深浅主题实机视觉待验收。
- Comment REQ-022（TTS 设置主题与状态色迁移）：TTS 语音源设置页从固定纯黑背景迁移到统一 `WatchSurface`；未登录、未配置密钥及连接失败状态使用主题 error 色，随深浅主题保持语义一致。`assembleDebug`、`:app:installDebug` 通过，Android 11 模拟器启动 MainActivity 后无 AndroidRuntime 异常，`git diff --check` 通过；TTS 设置页实机深浅主题视觉待验收。
- Comment REQ-022（个人中心主题背景迁移）：个人中心根背景改为统一 `WatchSurface`，首页渐变／背景规则与账号、收藏、设置和备案入口内容保持一致；原列表项触控与备案操作不变。`ProfileScreenTest` 4 项全部通过并断言既有入口回调，测试后 `installDebug` 成功，MainActivity 启动后无 AndroidRuntime 异常；实体圆屏视觉待验收。
- Comment REQ-022（B站频道信息页主题迁移）：频道信息页从强制纯黑切换到统一 `WatchSurface`，标题、订阅信息、平台范围卡片和既有操作继续使用主题语义色与原导航回调。`BiliActivitySmokeTest` 11 项通过；测试后 `installDebug` 成功，Android 11 模拟器 MainActivity 启动且 AndroidRuntime 无异常，`git diff --check` 通过。实体圆屏信息密度与主题视觉待验收。
- Comment REQ-022（B站搜索结果页主题迁移）：结果列表与搜索入口改用同一 `WatchSurface` 主题背景，保留搜索输入、空结果／错误提示、分页加载及卡片点击行为。`assembleDebug`、`:app:installDebug` 通过；Android 11 模拟器 MainActivity 启动后 AndroidRuntime 无异常，`git diff --check` 通过。此次只验证构建与应用启动，搜索结果页实际路由和圆屏视觉仍待专门验收。
- Comment REQ-022（AI 总结错误状态与主题迁移）：总结页改用统一 `WatchSurface`；错误面板以 Material error container 与 onErrorContainer 呈现，不再使用固定浅红背景和硬编码文字色，错误状态在深浅主题遵循相同语义对比。新增 `LlmSummaryScreenTest` 验证错误信息显示及重试按钮可访问、点击回调正常；Android 11 原生测试通过（1 项），测试后安装并启动 MainActivity 无 AndroidRuntime 异常，`git diff --check` 通过。圆屏实际主题视觉仍待验收。
- Comment REQ-022（Token 用量页交互语义与触控）：供应商汇总和单次记录卡移除空操作的 clickable 语义，保留信息展示；“清空记录”操作最小触控高度设为 48dp，并显式标注 Button 角色。新增 3 项原生测试验证两类信息卡无点击动作、清空操作高度／角色／回调正确；Android 11 模拟器全部通过，安装后 MainActivity 启动且 AndroidRuntime 无异常，`git diff --check` 通过。
- Comment REQ-022（B站回复卡片交互）：回复详情中的根评论和回复卡片不再注册空点击回调，因此不再伪装成可操作控件；评论列表中有实际导航动作的卡片继续保持可点击。`:app:installDebug` 成功；Android 11 模拟器启动 MainActivity 后 `AndroidRuntime` 无异常，`git diff --check` 通过。回复详情路由需登录态／真实评论数据，未做交互设备验收。
- Comment REQ-022（B站回复页刷新反馈）：下拉刷新指示状态直接绑定 Paging refresh load state，移除 `refresh()` 返回后立刻清零的本地标志，加载指示器因此覆盖实际刷新周期；下拉回调仍触发 Paging 刷新。`:app:installDebug` 成功；Android 11 模拟器启动 MainActivity 后 `AndroidRuntime` 无异常，`git diff --check` 通过。真实网络下刷新反馈仍待设备验收。
- Comment REQ-022（自动滚动页主题迁移）：设置与当前文章控制页统一使用 WatchSurface 和 Material 主题语义色；移除固定白字、灰字与固定按钮底色，保留播放／暂停、速度调整、开关及现有 48dp 触控目标。`AutoScrollControlActivityTest` 2 项通过，测试后 `installDebug` 成功；Android 11 模拟器启动 MainActivity 后 `AndroidRuntime` 无异常，`git diff --check` 通过。真实 Wear OS 深浅主题视觉仍待圆屏设备验收。
- Comment REQ-022（B站扫码页主题背景迁移）：扫码等待页的下拉刷新容器接入统一 `WatchSurface`，去除强制纯黑背景；扫码指引、二维码状态、刷新行为与亮屏等待逻辑保留。`qrLoginKeepsScreenAwakeWhileWaitingAndRestoresItWhenFinished` 原生回归通过，测试后安装成功；Android 11 模拟器启动 MainActivity 后 `AndroidRuntime` 无异常，`git diff --check` 通过。实体圆屏亮度／主题视觉仍待验收。
- Comment REQ-022（朗读控制页主题迁移）：朗读播控页使用统一 `WatchSurface`；标题、当前文章和速度文字及未选中控制图标改用主题前景色，状态文字改用次级主题色，移除内容区强制纯黑覆盖；停止入口与原控制布局、48dp 触控尺寸保留。`ReadAloudPlaybackScreenTest` 通过，覆盖所有主要动作触控目标与文章入口回调；测试后 `installDebug` 成功，Android 11 模拟器启动 MainActivity 后 `AndroidRuntime` 无异常，`git diff --check` 通过。实体圆屏主题视觉仍待验收。
- Comment REQ-022（RSS 设置主题背景迁移）：普通设置页与高级设置页改用统一 `WatchSurface`，取代强制纯黑容器；阅读字号、音量保护、缓存和媒体缓存控制保持现有控件／回调。`SettingsScreenTest` 3 项通过，覆盖设置显示、控件触控和既有回调；测试后 `installDebug` 成功，Android 11 模拟器启动 MainActivity 后 `AndroidRuntime` 无异常，`git diff --check` 通过。实体圆屏主题视觉仍待验收。
- Comment REQ-022（提示词预设选择语义）：每个预设整个卡片作为至少 48dp 的 RadioButton 单选目标，明确向辅助技术暴露当前选中状态；保留原提示词及选择回调。新增 `LlmPromptPresetScreenTest` 通过，验证当前选中状态、卡片触控高度与选择回调；测试后 `installDebug` 成功，Android 11 模拟器启动 MainActivity 后 `AndroidRuntime` 无异常，`git diff --check` 通过。
- Comment REQ-022（提示词设置主题背景迁移）：提示词预设页从强制纯黑背景改为统一 `WatchSurface`，预设卡片继续使用主题 surface、onSurface 与 onSurfaceVariant 色彩；RadioButton 选择语义和提示词保存逻辑不变。复用 `LlmPromptPresetScreenTest` 通过，测试后 `installDebug` 成功，Android 11 模拟器启动 MainActivity 后 `AndroidRuntime` 无异常，`git diff --check` 通过。
- Comment REQ-022（通用操作菜单语义与危险色）：共用 ActionDialog 使用主题 `WatchSurface`／surfaceVariant；删除动作改用 Material error 语义色，动作入口声明 Button 角色并确保至少 48dp 高度；禁用状态仍不可触发。`StaticCommonScreenTest` 7 项通过，操作菜单回归覆盖触发与禁用动作；测试后 `installDebug` 成功，Android 11 模拟器启动 MainActivity 后 `AndroidRuntime` 无异常，`git diff --check` 通过。
- Comment REQ-022（B站搜索入口主题迁移）：搜索页统一使用 `WatchSurface`，搜索历史标题从固定白色改为次级主题前景色；历史点击／长按删除、热门搜索及清空操作保持不变。`BiliActivitySmokeTest#biliSearchActivity_launches` 通过，测试后 `installDebug` 成功；Android 11 模拟器启动 MainActivity 后 `AndroidRuntime` 无异常，`git diff --check` 通过。搜索结果实际主题视觉仍待实体圆屏验收。
- Comment REQ-005（停止朗读同时取消云端合成）：应用默认语音请求改为 OkHttp 异步请求，并绑定朗读协程取消；停止／取消会中止未完成的合成网络调用，释放响应体，避免请求完成后意外准备或启动音频。当前主机未连接 adb 设备，按仓库要求以 `./gradlew assembleDebug` 验证编译；运行时停止入口及取消效果仍需设备验收。
- Comment REQ-019（Watch 协议就地拦截回归）：Watch OOBE 未同意协议时点击“下一页”会显示紧邻勾选项的解释，复选框保持未选且页面回调不前进；该规则已在现有手表流程中实现，本轮补齐明确的原生回归证据；`OobeScreenTest` 6 项通过。未勾选协议时通过按钮继续的路径已被覆盖；真实圆屏提示位置待验收。
- Phone 帮助／样式最终全量 448 项测试通过，日志 /tmp/watchrss-phone-final-help-pilot-unit-validation.log。随后自动云同步新增 4 项调度测试，全量 452 项零失败／零跳过、assembleDebug 通过，日志 /tmp/watchrss-cloud-scheduler-validation.log；最后权限退出时停调度补丁正在执行安装验证。
- RSCH-003 B：自动云同步改为首次启动立即检查已有本地／云端状态，后续变更 5 秒合并；在传输期间用有界合并队列保留一次补传，编辑不取消正在进行的同步；临时失败按 15／60／120 秒有限重试，取消立即传播，非法参数等待用户操作。继续调用现有增量快照／密钥／网络策略服务。4 项回归覆盖初始补传、合并、重试上限、停止、重新启动与传输期间编辑。此为 B 的调度缺口修复，A 的蓝牙自动补传及多账号隔离仍未完成。

- Comment RSCH-003（B 调度部分）：4 项调度测试与 Phone 全量 452 项均通过，`./gradlew :app:testDebugUnitTest :app:installDebug` 成功安装到临时 Android 37 模拟器。启动 `com.lightningstudio.watchrss.phone/.MainActivity` 成功，crash buffer 无记录。日志 /tmp/watchrss-cloud-scheduler-installed-validation.log。协议／授权失效路径同时停止调度。尚未用真实账号执行云同步，也未将 A+B 整体标记完成。
- SQL 检查补验：`cargo test --locked --doc` 3 项 compile_fail 示例全部通过，确认类型化 SQL 的错误示例仍被编译拒绝；日志 /tmp/watchrss-backend-doctest-validation.log。
- REQ-020 范围核对：现有账号退出会撤销／排队撤销会话并释放授权，但 Room 数据库、正文文件、备忘录图片、阅读资源和部分云状态仍是进程共享。仅加切换按钮会让新账号接触旧资料，不能作为 B 交付；仍需统一数据空间、旧资料归属迁移以及切换前停止 UI／传输／云任务的实现和回归。本轮未对用户现有资料做移动或删除。

## REQ-020 手机账号空间实现与验证

- Comment REQ-020（手机端数据空间）：以远端环境与账号 ID 的 SHA-256 摘要形成稳定空间名；Room 数据库、长正文、笔记图片、阅读字体／背景／预设选择、导入暂存／撤销记录、云游标／缓存／续传状态、AI 配置、阅读偏好与引导档案分别归属账号。旧根目录只绑定启动时加密会话所确定的原账号；没有可确定会话时保留为未登录空间，不自动并入后来登录的账号，不移动或删除现有资料。
- 登录／退出改变身份时，先同步提交加密会话，但不把新身份发布给旧数据消费者；Application 重建具有不可变存储路径的容器，关闭旧连接并取消旧账号任务，再重新打开入口。旧 Activity 销毁后关闭其旧 Room 数据库。直接由 Activity 创建的图片／正文／资源对象通过 Activity 所属容器解析，旧界面不会重定向到新账号文件。未使用进程强杀或闹钟重启。
- 账号页新增“切换账号”确认，明确退出／名额释放及资料保留，需要重新验证另一个账号；返回原账号恢复原空间。没有保存多账号明文凭据，也没有宣称无需验证的一键登录。原退出登录与名额／会话撤销队列保留。
- BLE 已排队的备忘录 RPC 在入队前绑定原容器，关闭服务时取消旧子任务；云 Worker 的取消不再被吞为重试结果。离线授权签名校验增加当前账号匹配，避免仅因同一设备而接受另一账号的租约。
- 2 项 JVM 回归验证账号／环境／未登录路径分离；5 项真实原生测试覆盖 Room 同 ID 数据、正文、图片删除、阅读资源、云游标、迟到写入、切回恢复、旧资料归属、同账号令牌刷新、新身份不发布给旧消费者、取消时提交与重绑定连续性，以及真实 ES256 签名租约的账号／设备／过期边界。全量 Phone 454 项单测零失败；账号、帮助、同步卡片合计 9 项原生回归零失败／零跳过，`:app:installDebug` 成功。日志 /tmp/watchrss-account-spaces-regression-validation.log；单独账号 5 项日志 /tmp/watchrss-account-spaces-lease-validation.log。测试只用隔离目录与模拟凭据，不请求登录、付款或生产账号接口。
- Comment REQ-020（手机存储管理按账号空间统计／清理）：设置页存储管理器改用容器已绑定的 `libraryContext`，阅读资源占用与“清理阅读背景副本”现在限定于当前账号目录；数据库容量统计从当前 Room 数据库所在目录读取，避免多账号数据统计错位。未删除、搬移或清理其它账号的私有数据。
- Comment REQ-020（避免存储页触碰共享外部缓存）：继续将统计／清理范围收紧到当前账号的内部 `cacheDir`，不再清空无法按账号归属的 `externalCacheDirs`；统计只计当前账号前缀的 SharedPreferences 文件，旧的未分区偏好不被错误归属给当前账号。缓存清理不会影响其他账号的外部临时文件。
- 完成范围限制：这些证据证明手机空间和会话提交边界，尚未证明真实账号的完整认证／授权网络流程。当前 Phone `syncAll` 会传播账号同步失败并中止本次连接；手表切换容器的会话交接已有独立隔离实现与模拟器回归，但真实手机／手表配对后的账号切换及完整同步仍待设备验收，不能将本行标为全量完成，也不能把账号界面文字当作跨端验收证明。
- Comment REQ-020（账号同步失败时中止资料合并）：复核 `PhoneBluetoothSyncManager.syncAllUnlocked()`，账号同步失败会从 `runCatching` 重新抛出并进入外层 abort 路径，因此不会调用 `syncLibraryUnlocked()`；合并前协议还要求手表资料库版本至少为 v13。此前记录的“账号同步失败仍继续合并”已由当前实现修正，不再作为待办。真实双设备认证与切换流程仍待验收。

- 交付前额外执行手表 `./gradlew :app:installDebug`，安装成功，日志 /tmp/watchrss-watch-account-phase-install.log；随后显式启动 com.lightningstudio.watchrss/.MainActivity，crash buffer 无崩溃记录。Phone 同步卡片／帮助与账号测试已全部终态通过后才做本次手表启动检查。

## REQ-020 手表与同步协议续修

- 已接入手表不可变账号数据上下文：远端后端地址与账号共同决定空间，旧根目录一次性绑定已有加密会话；Room、正文、笔记图片、阅读资源／预设、DataStore 阅读设置、媒体缓存与账号私有偏好分离。设备标识继续使用原全局身份，云状态／密钥／缓存使用账号空间。
- DefaultAppContainer 在切换时停止朗读与本账号任务，并等待 DataStore 所属作用域结束后创建新容器，避免重进同一账号时同时打开两份同文件 DataStore。旧 Activity 保留原数据上下文直到销毁，之后关闭旧 Room。WatchAccountStore 的旧实例不读取新身份，迟到令牌刷新不能覆盖新账号。
- WatchBluetoothSyncServer 每个连接固定容器；只有账号同步成功才将该连接切到新空间。资料读取／写入前核对手机声明的账号与后端地址，探测能力依旧公开；不匹配先返回可读错误，再结束该连接。资产帧也写入连接绑定的账号目录。
- Phone 在申请／发送手表账号凭据前，先要求探测支持 supportsAccountDataSpaces；缺失时要求升级手表。账号同步失败不再继续合并资料，单向覆盖也先完成账号同步。新手机请求携带不可变的 libraryAccountUserId／libraryAccountBackend。给资料分帧预算预留 4 KiB 标识空间，避免原本刚好达到帧上限的数据加入新字段后越界；标识本身限制在同一预算内。
- 初轮手表 584 项单测零失败、assembleDebug 通过，日志 /tmp/watchrss-watch-account-policy-validation.log。最新事务取消保护、真实容器 A→B→A、以及实际帧错误路径正在模拟器验证，日志 /tmp/watchrss-watch-account-runtime-validation.log；Phone 协议修改全量测试／构建正在执行，日志 /tmp/watchrss-phone-account-wire-validation.log。未将运行中的日志视为成功终态。


## REQ-020 缓存、迟到回调与同步会话交接

- Comment REQ-020（两端账号边界续修）：Phone 最终账号上下文全量 455 项 JVM 测试通过，日志 /tmp/watchrss-phone-account-context-final-validation.log；最新分帧版本 9 项账号／帮助／同步卡片原生测试零失败、零跳过，并成功 installDebug，日志 /tmp/watchrss-phone-account-wire-final-runtime.log。之后显式启动 com.lightningstudio.watchrss.phone/.MainActivity，crash buffer 无记录。
- Comment REQ-020（手表缓存隔离）：图片内存缓存与宽高缓存加入不可变账号目录命名空间；load／preload／loadBitmap 在开始前捕获所属数据上下文，迟到旧账号请求不会写入新账号内存或磁盘。两端都以 WeakHashMap 保留已销毁 Activity 的原上下文，在同一锁内转移／读取，避免迟到回调落到新账号。手表该版本 584 项 JVM、4 项真实原生回归全部通过并安装，日志 /tmp/watchrss-watch-account-cache-final-validation.log。4 项覆盖真实容器 A→B→A、错误账号实际分帧拒绝、销毁 Activity 迟到回调和同 URL 的跨账号图片缓存；未使用真实登录凭据或生产接口。
- 首轮手表原生构建中的 Application CoroutineScope 接收者歧义已修正，重跑通过记录在 /tmp/watchrss-watch-account-runtime-repaired-validation.log；DDMLib 属性超时警告后设备恢复且测试完成，不计为应用崩溃。
- 同步可靠性复核发现：在账号帧处理过程中重建 Activity 会取消属于该界面的连接协程，手机可能收不到账号响应或后续资料。现将数据容器切换与 UI 重建分离：容器立即隔离，但由引起切换的会话在响应 ACK／后续传输结束或退出后统一收尾。更早或无关会话不能提前刷新新会话的界面，后台同步不主动拉起应用。新加 2 项会话归属／多次切换回归，Watch 全量 586 项 JVM 测试零失败、零跳过。
- 本次首条原生命令有两个测试类名拼写错误，导致 2 项 initializationError；有效的容器和实际帧用例通过，但整个命令判为失败，日志 /tmp/watchrss-watch-account-handoff-validation.log。已按实际类名重跑完整 4 项原生回归及 installDebug，全部通过，BUILD SUCCESSFUL（35 秒），日志 /tmp/watchrss-watch-account-handoff-final-validation.log。
- REQ-020 仍需真实手机／手表配对认证、授权、切换账号后的完整同步与返回流程验收；上述隔离测试不替代真实网络／配对验收。RSCH-003 A 的自动本地补传仍未实现，不因隔离边界补齐而关闭该项。

- Comment REQ-020（同步会话交接）：完整 4 项原生回归及安装终态成功，随后显式执行 adb shell am start -n com.lightningstudio.watchrss/.MainActivity，crash buffer 无记录。2 项会话归属回归与 586 项全量 JVM 测试通过。真实双设备账号同步仍待验收。


## RSCH-003 A 自动本地补传实现与验证

- 已接入账号私有的自动补传目标／开关／状态：只有用户在该账号下成功完成手动 MERGE 后，才记录稳定手表 ID 与已配对蓝牙地址；自动任务不扫描或任选其他手表，不执行单向覆盖。默认允许后续自动补传，没有已确认目标时不连接。切换账号后使用各自配置。设置→应用功能可关闭自动补传并查看最近状态。
- 自动调度监听文章同步序号、备忘录／目录、阅读预设／选择与资源变化；变更 5 秒合并，传输期间保留一次后续补传；启动／返回前台及目标蓝牙重新连接时检查已有待传资料。临时失败按 15／60／120 秒有限重试；账号变化、权限撤回、删除冲突等待用户处理。协议／授权门禁关闭及账号切换取消调度。现有蓝牙协商与 IP 升级保持原流程，没有新增连接模式。
- 所有手动同步、探测、导出日志与阅读预览入口改由同一整操作互斥保护；内部阶段直接复用私有实现，避免嵌套锁。探测返回的会话在选择器／预览期间仍计为占用；恢复会话交接所有权后释放临时实例，重复 close 不会提前放行自动任务。自动任务碰到占用则排队有限重试，不插入账号／资料／资源帧。
- 自动账号帧在申请凭据前要求手表声明自动同步能力、当前账号／环境匹配且设备 ID 与已确认目标一致；手表再独立拒绝自动切到其他账号。公开探测只返回“是否匹配请求账号”的布尔值，不返回他人账号 ID。删除冲突自动任务不代选；备忘录冲突保留并提示手动处理。
- 新增 7 项 JVM 行为回归（4 项调度、3 项整操作互斥），Phone 首轮修正后全量 462 项零失败／零跳过，日志 /tmp/watchrss-local-auto-sync-repaired-validation.log。初次重构曾误改 client 的既有预览方法名，编译已发现并修正，失败记录 /tmp/watchrss-local-auto-sync-validation.log。设备身份复核补丁的最终全量 462 项、installDebug 已通过，日志 /tmp/watchrss-local-auto-sync-final-validation.log。
- 手表新增 2 项纯策略回归，全量 588 项零失败、安装通过，日志 /tmp/watchrss-watch-auto-account-validation.log。含实际自动账号帧／探测路径在内的 5 项原生账号与缓存回归全部通过、零跳过，并成功 installDebug，日志 /tmp/watchrss-watch-auto-wire-runtime.log；随后显式启动 MainActivity，crash buffer 无记录。原生用例只用隔离模拟凭据，不调用生产账号接口。
- Phone 目标配置隔离／会话租约 2 项与既有账号／帮助／同步卡片 9 项原生回归合计 11 项零失败、零跳过，日志 /tmp/watchrss-phone-auto-local-runtime-validation.log。尚待真实手机／手表连接、首次成功绑定、断线恢复与新内容补传。模拟器不具备真实配对链路，不能将调度单测或错账号拒绝当作真实自动同步已验收。
- RSCH-003 B 补充审计：当前 WatchRssApplication、WatchCloudSyncWorker 和 AccountStatusActivity 仍以 BuildConfig.DEBUG 控制云任务／云入口；这不满足已请求的发布版 A+B 完整能力。后续需结合云会员／设备批准和内容同步选择补齐显式用户控制，并核对旧隐私说明；本轮未通过简单删除开关绕过该产品边界。Phone 云调度已有回归证据，但 A+B 整体保持未关闭。

- 额外补齐等待边界：手动操作（含长时间阅读预览）结束后发布一次独立的完成事件，再唤醒待补传任务；公开探测不发布完成事件，自动任务完成也不发布，避免探测抢占选择器或自动同步自循环。新加第 8 项本地同步回归。该版本 Phone 463 项全量单测、installDebug 与 11 项原生回归全部通过，日志 /tmp/watchrss-local-auto-sync-handoff-final-validation.log。

- 继续补齐非重试状态：账号／权限／删除与备忘录冲突会按账号持久暂停自动补传，后续内容变化或重新连接不反复提交；成功手动 MERGE 或用户重新开启自动补传才恢复。原生配置隔离用例同时检查暂停的跨实例保留、跨账号隔离及显式恢复。最新构建／installDebug／463 项全量／11 项原生验证全部成功，零失败、零跳过，日志 /tmp/watchrss-local-auto-sync-paused-final-validation.log（BUILD SUCCESSFUL，2 分 1 秒）。原生测试结束后 AGP 清理了 Phone APK，首次直接启动返回 Activity 不存在。单独再次执行 installDebug 成功（/tmp/watchrss-local-auto-sync-final-install.log，9 秒）；随后 adb am start -W 返回 Status: ok，正常进入 PhoneOobeActivity，crash buffer 无记录。未跳过协议或授权流程。


### 本轮逐点 comment

- Comment RSCH-003 A（自动补传）：已确认目标的本地增量同步加入启动／前台／重新连接检查，文章、备忘录与阅读资源变更 5 秒合并，并保留传输期间一次补传；临时失败 15／60／120 秒有限重试。设置页可关闭并查看最近状态。Phone 463 项全量单测与最新 11 项原生回归通过；真实双设备链路仍待验收。
- Comment RSCH-003 A（会话互斥）：手动与自动操作共享整操作锁，租约在探测选择器和恢复交接期间持续生效，长时间手动预览结束后重新唤醒补传；自动完成不唤醒自身。4 项锁行为回归和真实会话租约计数／交接用例通过，避免只靠单帧锁造成协议交叉。
- Comment RSCH-003 A（自动同步账号边界）：自动任务只使用手动确认的蓝牙地址和稳定设备 ID，发凭据前核对匹配能力；手表独立拒绝自动切号。Watch 588 项全量单测、含真实分帧的 5 项原生回归通过；无需或未使用真实认证凭据。
- Comment RSCH-003 A（持久暂停）：账号／权限／冲突问题按账号持久暂停；内容变更和重新连接不再反复失败。手动合并成功或显式重新开启后恢复，跨账号不继承暂停或目标。新增原生用例验证跨实例保留、跨账号隔离及恢复，最新 11 项原生回归全部通过。

本轮交付范围为 A 的本地实现及上述验证；B 的发布版云入口／用户控制、真实双设备、真实云账号、在线朗读授权音色、支付商户与设备矩阵均不视为已验收或已关闭。完整工单范围保持不变。


## RSCH-003 B 发布版入口、用户控制与网络边界（验证中）

- 已移除两端云同步入口／手表运行时的 DEBUG 专属限制。Phone 保留原会员云空间、恢复词、设备批准与快照操作；Watch 增加自动开关、网络策略、真实状态、上次完成时间和手动同步确认。自动同步默认关闭，正式确认首台手机的恢复词并启用后才开启；仅生成／查看恢复词不会开始后台传输。既有已授权账号可在云空间显式打开自动同步。
- Watch 手动“立即云同步”会先确认临时覆盖网络限制；自动开关也需确认。自动关闭会取消进行中的自动任务；手动操作单独遵循明确操作意图。Phone Worker 和服务层检查自动开关、密钥、协议与应用授权；Watch 服务层检查协议，并在未选择自动同步时提前返回，不读取账号凭据。配置与网络策略分别存入各账号空间。
- 两端调度增加网络恢复与充电状态唤醒，关闭后注销监听；只比较网络标识、联网／计费属性与充电状态，不因带宽估计更新触发自身。断网按临时错误有限重试。Phone 云调度覆盖备忘录与阅读预设／资源变化；Watch 继续使用已实现的 RSS／资料库版本源。
- 修复 Phone 小状态策略误传备忘录正文／图片包：非完整传输只允许 rss-state.json，其他和未知对象均视为完整内容，等待允许的网络。生成小状态快照时携带前次私密对象描述，不重传其块、不丢弃此前备份；真实加解密回归验证引用保留与最终可恢复。
- 修复只读恢复路径：Watch 先读取服务端会员状态，只读期跳过设备登记、密钥版本登记及快照上传，保留读取／解密；Phone 使用恢复词完成本地密钥恢复后，只读期不再调用写权限设备登记或密钥信封上传。新增设备批准仍要求可写会员，明确指引只读用户使用恢复词或此前已批准的设备。
- 首轮最新主干前的单测／安装／release Kotlin 编译曾全部通过：Watch 592 项、Phone 465 项；日志 /tmp/watchrss-watch-cloud-controls-final-validation.log（10m33s）、/tmp/watchrss-phone-cloud-controls-final-validation.log（9m53s）。后续网络／关闭取消补丁继续执行验证，不能将这两个历史通过结果当作所有后续补丁的终态。
- 初轮 2 个旧测试按源码要求“云同步仅限调试版”，与本次明确要求冲突；已删除这两项旧实现断言，保留其他调试工具／功能隔离测试，新增真实开关、策略、取消、加密与原生路径验证。Phone 首次构建在源码增量编辑期间未包含新 cloudObjectAllowed 函数，单测编译报告缺失；完整重建后已通过，历史日志 /tmp/watchrss-phone-cloud-controls-validation.log。
- 进一步完整回归中 Watch OobeViewModelTest 出现 Main 调度器在异步 IO 恢复时已经被重置的错误（/tmp/watchrss-watch-cloud-release-runtime-validation.log）。独立 Oobe 2 项诊断通过（/tmp/watchrss-oobe-dispatcher-diagnostic.log）。发现多个测试自行创建 ViewModel，未在重置 Main 前等待其独立作用域完成；给 MainDispatcherRule 增加跟踪及取消等待，并让相关 ViewModel 测试注册实例，原行为断言保留。修复后的完整验证日志 /tmp/watchrss-watch-cloud-runtime-repaired-validation.log。
- 新增原生验证包括：Watch 默认关闭及未确认协议时不读取凭据；账号配置持久化；开启／取消和手动覆盖网络限制确认；真实 AndroidKeyStore ECDH 密钥信封在只读账号下解密，HTTP 拦截只接受 bootstrap／heads，任何登记／上传即失败。Phone 增加按账号保存自动开关／网络策略以及真实恢复词加解密的只读恢复路径，HTTP 拦截只接受 bootstrap。均使用 UUID 隔离测试空间／模拟凭据，不发出生产请求。
- 当前运行：Watch /tmp/watchrss-watch-cloud-runtime-repaired-validation.log；Phone 最后只读修复 /tmp/watchrss-phone-cloud-readonly-final-validation.log。需分别读取终态，并在原生回归后单独重新安装、启动与检查 crash buffer（AGP 可能清理 APK）。
- 保留的范围缺口：现有设备私钥实现依赖 Android 12+ 的 Keystore ECDH；界面已说明 Android 11 能力限制，未改用较弱的临时密钥方案。Watch 云路径目前实际覆盖文章／频道／阅读状态，备忘录和阅读预设／资源的跨云桥接尚未接入；Phone 加密完整备份包含这些类型。后续仍需补齐目标设备能力和跨类型云桥接，并完成真实账号／设备批准／双端增量冲突及费用／流量验收。RSCH-003 A+B 保持未关闭，未用发布入口代替完整云能力。


- B 原生第一轮终态：Watch 592 项 JVM、9 项原生（含 UI 确认／取消与 Keystore ECDH 只读信封）全部通过、零跳过，debug 安装及 release Kotlin 编译通过，日志 /tmp/watchrss-watch-cloud-runtime-repaired-validation.log；Phone 466 项 JVM、release Kotlin 编译与安装通过（/tmp/watchrss-phone-cloud-readonly-final-validation.log），13 项原生全部通过（/tmp/watchrss-phone-cloud-readonly-runtime-validation.log），含恢复词真实解密及只读不登记／上传。
- 信封证据补强：将 Watch 原生签发端夹具改为独立 JCA ECDH／RFC5869 HMAC／AES-GCM 运算，不复用解码器的 HKDF／AES 帮助函数。3 项云控制原生测试重跑通过，日志 /tmp/watchrss-watch-cloud-independent-envelope-validation.log。模拟账号与目标密钥均 UUID 隔离，测试后清理专用 ECDH alias，不删除应用共用 MasterKey。
- 后续取消状态补丁：两端取消时先更新为空闲／已停止提示，保留未完成项目后继续传播 CancellationException，不误标完成或错误。Watch 新增第 4 项云原生用例，确认停止反馈、取消标志与 lastCompletedAt 不增加；Phone 在真实恢复词只读用例中阻塞模拟 bootstrap、取消并释放请求，核对同样边界。最后验证日志 /tmp/watchrss-watch-cloud-cancel-final-validation.log、/tmp/watchrss-phone-cloud-cancel-final-validation.log；原生部分仍需读取终态。


### RSCH-003 B 云控制与取消回归终态

- Watch：592 项 JVM、10 项原生零失败／零跳过；installDebug 和 release Kotlin 编译通过（/tmp/watchrss-watch-cloud-cancel-final-validation.log）。Phone：466 项 JVM、13 项原生零失败／零跳过；release Kotlin 编译通过（/tmp/watchrss-phone-cloud-cancel-final-validation.log、/tmp/watchrss-phone-cloud-cancel-runtime-validation.log）。原生测试后重新 installDebug 成功（/tmp/watchrss-phone-cloud-cancel-posttest-install.log，17 秒）。两端显式启动 MainActivity 返回 Status: ok，分别进入未确认协议的 OobeActivity／PhoneOobeActivity；adb crash buffer 无记录。未绕过协议／授权、未连接生产账号。
- Comment RSCH-003 B（发布入口和确认）：两端发布版提供云入口；自动传输需显式开启，手表手动覆盖网络限制先确认。真实 Compose 用例验证确认／取消；未确认协议或开关关闭时不读取凭据。
- Comment RSCH-003 B（调度与关闭）：网络恢复／充电变化触发合并检查，关闭取消自动任务并注销监听；带宽估计变化不触发自身。两端调度取消及有限重试回归通过。
- Comment RSCH-003 B（正文网络边界）：手机小状态模式仅传 RSS 状态，备忘录正文、附件与未知私密对象等待完整传输条件；携带前次私密对象描述，真实加解密回归确认旧块不重传、备份可恢复。
- Comment RSCH-003 B（手表只读）：批准过的只读手表可通过 Keystore ECDH 解密信封，不登记设备／密钥、不上传快照；独立 JCA 签发端原生用例通过。
- Comment RSCH-003 B（手机只读恢复）：只读账号用恢复词在本地恢复密钥，跳过设备登记与信封上传；真实恢复词加解密和 HTTP 边界原生回归通过。
- Comment RSCH-003 B（取消反馈）：两端取消后立即显示空闲／已停止，保留未完成项目并传播取消异常，不增加完成时间。Watch 原生直接取消服务、Phone 阻塞模拟 bootstrap 后取消服务，均验证通过。
- 继续排查发现两端导出时间戳及手机 ZIP 元数据会制造虚假内容变化；正在修正内容指纹并增加回归。此前 592／466 单测通过不能替代后续补丁验证。完整 RSCH-003 B 仍未关闭。


### RSCH-003 B 无改动时重复上传修复

- Comment RSCH-003 B（手表内容指纹）：云状态的 exportedAt 和中继帧的 sentAt 每次导出都会变化，原始字节散列会误判为资料更新。改为规范化内容指纹，仅排除根级传输时间，保留阅读进度、内容版本、私密正文与密钥版本；真实导出及变更对照 2 项回归通过。
- Comment RSCH-003 B（手机内容指纹）：RSS／中继 JSON 排除对应传输时间；WRSS 与备忘录 ZIP 按路径和条目内容计算指纹，忽略 ZIP 打包时间／顺序，WRSS 仅忽略 manifest 的导出时间。正文、图片及阅读预设版本仍影响结果；未知对象按原始字节比较。保留实际归档与快照时间用于展示，不改动备份数据。3 项回归验证 JSON 键序／传输时间、ZIP 重新打包、文件流、正文／图片／预设版本及未知对象。
- 最新两端全量验证：Watch 594 项 JVM、Phone 469 项 JVM，均零失败／零跳过；installDebug 与 release Kotlin 编译均通过。日志 /tmp/watchrss-watch-cloud-fingerprint-validation.log（2m50s）、/tmp/watchrss-phone-cloud-fingerprint-validation.log（2m12s）。分别显式启动两端 MainActivity 返回 Status: ok，进入各自协议引导；未绕过授权。云控制／取消的 10／13 项原生证据来自此前一轮，未将其计为新增指纹代码的原生覆盖。


### RSCH-003 B 阅读预设／资源双向云桥接

- Comment RSCH-003 B（双向阅读资源）：Watch 接入现有 private-library.wrss 对象。完整网络策略下读取手机 WRSS 的阅读预设、字体、背景及 watch／watchPoster 派生资源；上传同格式的阅读器归档，手机沿用现有 WRSS 解码和合并。手表导出的文章列表为空，文章继续通过既有 library-sync.json 交换，没有新增账号或连接方式。小状态模式不下载或上传阅读资源，只携带上次对象描述。
- Comment RSCH-003 B（资源校验与恢复）：解压使用当前账号缓存中的独立临时目录；校验路径白名单、重复条目、数量／体积上限、引用闭合、文件长度及 SHA-256，再安装到账号私有资源目录。通过临时文件和重命名替换，损坏、缺失、未引用资源不会被当作成功。失败或结束清理临时目录；本机缺少待上传资源时不生成不完整快照。
- Comment RSCH-003 B（版本合并与当前样式）：按现有阅读器版本／删除状态／来源规则选择胜出记录，只安装胜出记录需要的资源，旧远端快照不覆盖较新的本地字体。合并后通知资源变化，当前同 ID 的预设即时更新；原生测试验证当前预设名称变化、字体真实落盘、重复恢复无变化及其他账号未收到文件。
- Comment RSCH-003 B（升级后补收旧资源）：为完整阅读能力使用新的 applied_full_reader_v2 游标，旧文章版 applied_full／applied 不作为阅读资源已接收凭据；保留小状态游标，升级后重新处理旧完整快照。原生回归验证旧完整游标清零、状态游标保留及新游标跨实例持久化。
- Comment RSCH-003 B（变更与指纹）：自动云同步增加预设、字体、背景和资源完成通知的变更源；阅读归档计入内容指纹，忽略导出和 ZIP 打包时间，真实版本／文件变化仍触发同步。只读接收完成后直接返回，不为上传生成本地资源归档。
- 双向协议证据：Watch 的真实编码器生成 watch-reader-cloud.wrss，由 Phone 真实 WatchRssBackupArchive 解码、重新编码 phone-reader-cloud.wrss，再由 Watch 解码和落盘验证。两份夹具保存在对应 app/src/test/resources/cloud 中；均仅含固定测试文本／资源，不含用户数据。Watch 7 项 JVM 用例覆盖完整资源往返、损坏／缺失／未引用资源、路径／版本边界、本地版本胜出、指纹及手机重编码兼容；Phone 增加 1 项真实编解码互操作用例。
- 最新主验证：Watch 全量 601 项 JVM、9 项原生零失败／零跳过，installDebug、release Kotlin 编译通过（/tmp/watchrss-watch-reader-cloud-final-validation.log，1m32s）；Phone 全量 470 项 JVM、installDebug、release Kotlin 编译通过（/tmp/watchrss-phone-reader-cloud-final-validation.log，32s）。首轮 6 项原生亦通过（/tmp/watchrss-watch-reader-cloud-runtime-validation.log，1m30s）。补充账号生命周期／分帧边界回归仍在运行，日志 /tmp/watchrss-watch-reader-cloud-account-regression.log，不能提前计为通过。
- 仍未关闭 RSCH-003 A+B：备忘录正文／目录／图片的 Watch 云桥接尚待实现；Android 11 设备密钥能力、真实云账号批准／冲突、真实双设备自动补传及流量／费用／OEM 生命周期仍需验收。本轮只补齐阅读预设／资源这一部分，不把文章／阅读器成功推断为所有内容同步成功。

- 最后账号回归终态：WatchActivityContextLifetimeTest／WatchLibraryScopeWireTest 的 3 项原生零失败／零跳过，/tmp/watchrss-watch-reader-cloud-account-regression.log（35 秒）。与本轮主验证 9 项合计覆盖 12 项不同原生用例；测试结束后 installDebug 成功。两端再显式启动 MainActivity 均返回 Status: ok，进入协议引导，adb crash buffer 无记录，未绕过账号／协议流程。两端 git diff --check 通过。
- 下一段已核对的备忘录缺口：Phone notes-state.json 尚无目录字段，Watch 的现有 merge 是基于时间／基线的覆盖合并；不能直接复用为完整云冲突解决。Phone 云图片只恢复文件、未恢复 note_assets 登记，后续导出依赖登记时可能漏掉这些文件；notes-assets.zip 导出还吞掉所有异常。后续需连同目录、冲突保留／处理和附件再导出一起修复，不能仅解析正文就宣布备忘录云同步完成。


### RSCH-003 B 备忘录图片恢复／再次导出可靠性

- Comment RSCH-003 B（恢复后再次导出）：Phone 云图片恢复补上资产登记，导出同时以正文 Markdown／HTML 的 assets/ 引用为依据，覆盖此前只恢复文件、没有资产表记录的资料。图片共享时同一个 ZIP 路径只写一次，避免按笔记逐条追加造成重复路径失败；全局同路径索引用于内容校验。真实 Room／账号文件空间用例验证两条笔记分别以 Markdown／HTML 引用同一图片，恢复、再次导出、重复恢复均保留该图片，且只生成一个图片条目。
- Comment RSCH-003 B（损坏与同名冲突）：恢复前核对引用闭合与已有索引，校验同名图片的大小／SHA-256；索引正确而文件损坏时通过临时文件和重命名修复。不同内容占用同一已有图片路径时明确失败，保留原文件，不静默覆盖。原生测试验证同长度损坏缓存可修复、同名不同内容被拒绝，以及其他账号目录未收到文件。测试资源为固定字节串，只证明存储／再导出路径，不替代实际图片渲染验收。
- Comment RSCH-003 B（导出失败不再伪装成功）：新增 exportCloudZip，只有没有活动笔记时返回 null；缺失／损坏图片、IO 和取消异常继续传播。PhoneCloudSyncService 的自动增量上传删除旧的 runCatching(...).getOrNull()，不再把导出故障当成“没有附件”，避免上传缺失图片的完整快照。原生测试验证空资料无归档、有缺失引用时失败、已有未登记文件仍可导出。
- Comment RSCH-003 B（路径与格式边界）：正文图片解析覆盖 Markdown、HTML、实体转义及角括号包裹的文件名；普通链接、远程 URL 不读取本地文件，嵌套／越界路径拒绝。ZIP 写入端增加与读取端一致的条目数及展开体积上限，避免生成自身无法恢复的包。4 项新增 JVM 用例覆盖引用／边界与写入条目上限。
- 最新验证：Phone 全量 474 项 JVM、15 项原生零失败／零跳过，release Kotlin 编译、installDebug 通过（/tmp/watchrss-phone-note-assets-final-validation.log，1m49s）。其中新增 2 项真实 Room／账号目录原生用例，其余为云控制／取消、账号隔离、本地自动同步设置、离线帮助和同步状态回归。原生结束后再次 installDebug 成功（/tmp/watchrss-phone-note-assets-posttest-install.log，11 秒）。Watch 本轮生产源码未变化，重新 installDebug 通过（/tmp/watchrss-watch-note-assets-companion-install.log，24 秒）。两端 MainActivity 显式启动返回 Status: ok，进入各自协议引导；adb crash buffer 无记录，未绕过协议或账号授权。两端 git diff --check 通过。
- 范围校正：本地同步 PhoneBluetoothSyncManager 原本已从正文引用取图，本轮未修改该逻辑；上述修复针对云端增量接收后的附件恢复与 ZIP 再导出。Watch 备忘录正文／目录／冲突桥接、Phone 手动 restoreSnapshot 的备忘录范围以及整体真实云／双设备验收仍待补齐，不能据此关闭 RSCH-003 A+B。


### RSCH-003 备忘录合并与冲突基础修复

- Comment RSCH-003（正文与元数据）：两端新增相同的合并规则和配套行为用例。正文相同时仍合并较新的标题、目录归属、置顶等元数据；对可证明的正文祖先／后代保持正确方向。版本和来源相同时按元数据字段稳定选择，防止两端各保留不同值。内容散列与纯文本投影从原始 Markdown／HTML 重算，不把投影当作编辑正文。
- Comment RSCH-003（共同基线与自动合并）：仅在共同基线明确时做三方合并；独立修改可以合并，并把接收到的远端正文记录为新基线，让合并结果回到发送端时可识别为后代。基线不同、同位置插入以及插入与其他修改边界相交时保留冲突，不猜测拼接顺序。两端共享同组往返、元数据、基线和插入边界行为断言。
- Comment RSCH-003（删除与版本时钟）：未删除的旧／未变正文不能隐式撤销删除标记；删除与不同正文编辑并发时保留冲突。两端保存及管理操作的版本在同一毫秒连续操作或系统时钟回退时仍递增，删除时间与实际新版本保持一致。该策略不等于已经实现跨端显式“恢复已删除原 ID”操作，手动历史恢复的规则仍需单独完成。
- Comment RSCH-003（手表冲突保留）：现有手表备忘录同步不再按远端时间直接覆盖本机不同正文。保留本机记录，远端分支以有明确标题的冲突副本呈现；副本身份由远端分支确定，重试不反复生成副本，也不覆盖用户对既有副本的编辑。实际本地同步入口传入当前手表设备 ID，用于自动合并结果的来源标识。真实 Room 用例验证本机／远端都保留、重复接收无新副本、用户修改副本后不被重试覆盖。
- Comment RSCH-003（手机冲突解决）：重复接收同一组冲突使用稳定的冲突记录 ID；用户保存解决结果时，保存已处理远端正文作为基线，避免同一远端正文再次到达又变成同一冲突。远端删除造成的冲突在已有来源说明中明确标注。真实 Room 用例验证一条冲突、保留本机、解决后重试无新冲突。
- Comment RSCH-003（编辑与同步互斥）：同一账号容器中的备忘录保存、管理更新、接收合并和冲突解决使用同一仓库操作互斥，避免读取旧记录后交叉写回。手机冲突解决调用锁内保存实现，避免嵌套加锁。两端原生用例增加 32 个并发管理更新，检查版本全部唯一且递增；此项以最后一轮并发回归终态为准，未提前计为通过。
- 并发补丁前的终态：Watch 611 项 JVM／14 项原生、Phone 484 项 JVM／17 项原生均通过，release Kotlin 编译和 debug 安装通过。日志 /tmp/watchrss-watch-note-merge-final-validation.log、/tmp/watchrss-phone-note-merge-final-validation.log、/tmp/watchrss-phone-note-merge-runtime-validation.log。最后互斥补丁另行重跑，日志 /tmp/watchrss-watch-note-merge-serialized-validation.log、/tmp/watchrss-phone-note-merge-serialized-validation.log，Phone 原生尚需在 Watch 测试结束后单独执行，避免 UI 测试互相干扰。
- 范围保持不变：上述修复已接到现有两端备忘录仓库／本地同步合并，也作为 Watch 云桥接的前置基础；WatchCloudSyncService 尚未读写 notes-state.json／notes-assets.zip，目录协议、手动历史恢复和完整云桥接仍未完成。跨类型云能力、Android 11 密钥兼容、真实双设备／云账号、支付／朗读配置和其他工单验收仍按原完整目标继续，不因合并测试通过而关闭 RSCH-003 A+B。

- 最后互斥补丁验证终态：Watch 611 项 JVM、14 项原生零失败／零跳过，debug 安装与 release Kotlin 编译通过（/tmp/watchrss-watch-note-merge-serialized-validation.log，3m23s）；Phone 484 项 JVM、release Kotlin 编译与 debug 安装通过（/tmp/watchrss-phone-note-merge-serialized-validation.log，2m21s），17 项原生零失败／零跳过（/tmp/watchrss-phone-note-merge-serialized-runtime-validation.log，1m35s）。两端真实 Room 的 32 个并发更新均得到 32 个不同的连续版本；计入既有原生方法中的断言增强，不额外虚增测试条数。

- 安装／启动终态：Phone 原生测试后单独 installDebug 成功（/tmp/watchrss-phone-note-merge-posttest-install.log，22 秒）；Watch 由 connectedDebugAndroidTest 后置任务完成安装。两端再显式启动 MainActivity 返回 Status: ok，进入未确认协议的 OobeActivity／PhoneOobeActivity；adb crash buffer 无记录。两端 git diff --check 通过。未执行生产账号登录、真实支付或在线朗读请求，未将这些本地回归等同于真实双设备／云账号验收。


### RSCH-003 B 备忘录正文／目录／图片增量云桥接

- Comment RSCH-003 B（目录协议与兼容）：两端 notes-state.json 沿用版本 1，增加可选目录列表，包含名称、排序、来源、版本及删除标记；旧快照没有目录字段时仍读取正文和目录 ID，不把缺字段解释为删除。编码重算正文 SHA-256，解码在写库前校验正文、空／重复标识。目录删除标记不会被普通活动记录隐式撤回，删除目录不删除正文。
- Comment RSCH-003 B（快照一致性）：两端仓库在已有操作互斥内取得正文与目录快照；Phone 的 JSON 和附件 ZIP 使用同一份冻结笔记列表，不再各自读取可能已经变化的列表。原生用例在获取快照后把活动笔记改成引用另一张缺失图片，再导出冻结快照，验证仍完整携带原图片，不错误混入新引用。
- Comment RSCH-003 B（手表增量收发）：WatchCloudSyncService 已在完整传输条件下读取、合并并导出 notes-state.json 与 notes-assets.zip；小状态网络策略继续仅同步 RSS 状态，正文／目录／图片均等待完整传输条件。自动调度增加备忘录和目录的变更源。手表使用已修复的正文合并与冲突副本规则；图片先校验路径／引用，再以临时文件重命名发布到当前账号目录，不通过展示用纯文本覆写原文。
- Comment RSCH-003 B（升级补收）：Watch 完整游标改为 applied_full_notes_v3，Phone 改为 applied_full_folder_v2_heads，重新处理此前未理解备忘录／目录的旧完整快照；保留小状态游标。原生用例验证旧游标不误阻止新能力补收、状态游标仍保留，新完整游标可跨实例读取。
- 双向数据证据：Phone 实际编码器／ZIP 写入器生成测试包，由 Watch 实际适配器恢复；从模拟器中导出 Watch 适配器真实生成的 JSON／ZIP，放入 Phone 测试资产，再经 Phone 实际仓库与附件恢复路径读取。夹具仅含固定测试正文及图片字节串；它们验证传输、存储和再导出，不替代真实图片渲染或实体设备验收。
- 实际服务链路证据：Watch 新增原生用例使用独立 JCA ECDH／HKDF／AES-GCM 签发已批准的设备信封，模拟 HTTP 返回加密 manifest／chunks，执行真实 WatchCloudSyncService.syncNow。验证目录／正文／图片落盘、接收确认、只读不登记／上传、重复同步不重下已应用快照，并从旧阅读器游标补收。全部请求由本地拦截器处理，未连接生产账号或云空间。

### 阅读器专用快照的覆盖恢复保护

- Comment RSCH-003 B（防止部分快照覆盖资料库）：检查发现之前接入的 Watch 阅读器归档只有阅读设置和资源，却使用完整资料库格式，Phone 原先只按私有归档是否存在决定覆盖恢复资格。现在新文件使用独立 watchrss-reader-backup 格式及 reader-only 范围；使用原完整资料库格式校验的旧读取器会拒绝新文件。新版 Phone 也识别旧 watch-cloud 标记的阅读器文件，限定为合并，不把它们当作完整资料库。
- Comment RSCH-003 B（界面与底层双重限制）：云恢复预览不提供部分归档的覆盖操作并说明原因；两个本地导入界面的覆盖操作按预览资格禁用。即便绕过界面，云服务也在恢复前检查完整范围，通用 WRSS 读取／导入层在安装任何阅读资源或进入数据库替换前拒绝 reader-only 覆盖模式。完整 Phone 备份继续保持原格式，旧完整备份没有范围字段时仍可正常恢复。
- 回归包括：两端各 5 项目录／正文协议用例；Phone 新增 3 项范围／旧完整备份兼容用例；已有双向阅读器归档用例验证重编码不会丢失 reader-only 范围；原生增加旧阅读器文件在覆盖模式下不会安装资源的检查。源代码与行为覆盖不同层，未只检查按钮文案。
- 最后主验证已通过：Watch 616 项 JVM／16 项原生、release Kotlin 编译与安装（/tmp/watchrss-watch-note-cloud-format-final-validation.log，2m28s）；Phone 492 项 JVM、release Kotlin 编译与安装（/tmp/watchrss-phone-note-cloud-format-final-validation.log，1m44s）。Phone 当前 20 项原生回归正在运行，日志 /tmp/watchrss-phone-note-cloud-format-runtime-validation.log，不提前计为成功。此前增量桥接的 Phone 19 项原生已通过，日志 /tmp/watchrss-phone-note-cloud-bridge-runtime-validation.log（1m6s），历史结果不替代最后格式保护补丁。
- 完整目标继续保持未关闭：增量备忘录路径已接入，Phone 手动历史恢复的备忘录内容／冲突状态反馈、跨端显式恢复已删除原 ID 的规则仍需补齐；Android 11 密钥兼容、真实批准／双端增量冲突、流量／费用和设备生命周期仍需验收。在线朗读授权音色、支付商户及其他原始工单仍按完整范围推进。

- 最后原生回归终态：Phone 20 项零失败／零跳过（/tmp/watchrss-phone-note-cloud-format-runtime-validation.log，1m40s），包括实际 Watch 适配器导出的正文／目录／图片包、冻结版本再导出、目录能力游标升级，以及旧阅读器归档拒绝覆盖且不安装任何资源。Phone 原生测试后再次 installDebug 成功（/tmp/watchrss-phone-note-cloud-format-posttest-install.log，16 秒）。两端显式启动 MainActivity 均返回 Status: ok，进入各自未确认协议的引导页；adb crash buffer 无记录。两端 git diff --check 通过。以上未替代真实账号授权、真实云空间或双实体设备验收。


### RSCH-003 B／REQ-015 历史快照恢复的备忘录与完成反馈

- Comment RSCH-003 B（历史内容不再遗漏）：Phone 手动 restoreSnapshot 已接入备忘录正文、目录和图片；此前只恢复文章／阅读数据却发送完成确认的路径已修正。快照中活动备忘录明确恢复为副本，保留当前内容、原 ID 删除记录和快照中已删除项的语义，不把历史删除操作重放到当前资料。恢复副本／目录使用快照与源记录确定的身份，重复恢复保留已有副本及用户后续编辑；若用户后来删除了恢复副本，再次显式恢复会创建新的副本而不撤回删除标记。
- Comment RSCH-003 B（历史图片与当前图片隔离）：恢复前要求快照包含活动笔记引用的所有图片，不用当前图片代替缺失的历史内容。图片使用内容散列别名保存，重写 Markdown／HTML 图片引用时保留其余原文和格式，避免历史图片同名时覆盖当前文件。相同字节可共用一个别名，文件通过临时写入和重命名发布，恢复后补资产登记。JVM 回归覆盖 Markdown／HTML、引号中的大于号、实体转义、缺失图片、已删除项、相同图片去重和路径越界。
- Comment REQ-015（预览与结果范围）：恢复预览增加活动备忘录、目录、图片、快照已删除笔记及阅读预设数量，并说明“恢复为副本、重复恢复保留已有副本”。结果区分新建副本与保留已有副本，不再只报告文章数量。覆盖确认明确仅覆盖资料库；备忘录仍按上述副本规则恢复，既有阅读器部分归档覆盖保护继续生效。
- Comment RSCH-003 B／REQ-015（完成与失败边界）：历史图片与数据在变更前解析校验；完成数据恢复后才发送接收确认，确认成功后再推进本地游标与完成时间。服务为恢复失败设置明确错误状态，取消恢复设置空闲／已取消并传播取消异常，二者都不误标完成。确认失败／取消后的重试不会重复创建副本，已有用户编辑不被重试覆盖。
- 最新验证：Phone 全量 498 项 JVM 零失败／零跳过，release Kotlin 编译、installDebug 通过（/tmp/watchrss-phone-note-history-validation.log，1m24s）；25 项原生零失败／零跳过（/tmp/watchrss-phone-note-history-runtime-final-validation.log，1m4s），包含新增 5 项真实 Room、Android 加密密钥存储、实际 PhoneCloudSyncService 与加密 HTTP 测试包的历史恢复回归。逐项验证预览计数、当前图片不变、当前删除记录保留、快照中删除项不重放、重复恢复／用户编辑／再次删除、缺图无确认、确认失败游标不前进、取消后仍可重试、仅备忘录快照不能覆盖资料库。
- 原生首轮只因测试夹具构造器直接调用 suspend 会话保存而编译失败（/tmp/watchrss-phone-note-history-runtime-validation.log），尚未运行测试；将夹具初始化置于协程后重跑，生产行为与断言均未放宽。
- 完整目标保持未关闭：本轮补齐历史备忘录范围及其结果反馈，自动增量同步的冲突汇总、Android 11 密钥兼容、真实授权／云空间／双设备和既有外部配置／物理验收仍继续按完整目标推进。

- 安装／启动终态：Phone 原生回归后单独 installDebug 成功（/tmp/watchrss-phone-note-history-posttest-install.log，12 秒）；Watch 本轮无生产源码变化，重新 installDebug 成功（/tmp/watchrss-watch-note-history-companion-install.log，20 秒）。两端显式启动 MainActivity 返回 Status: ok，进入各自协议引导；adb crash buffer 无记录。两端 git diff --check 通过。所有恢复 HTTP 由测试拦截器提供，图片为固定测试字节串，未用真实云空间、生产账号或实际支付／朗读请求替代验收。


### RSCH-003 B／REQ-015 自动同步确认与冲突汇总

- Comment RSCH-003 B（确认失败可重试）：两端自动增量路径改为先完成本地合并、发送接收确认，成功后再推进完整／状态游标。此前先写游标再确认，确认失败后下次会跳过该快照；现由重试再次确认，本地合并继续使用稳定身份与幂等规则。
- Comment REQ-015（冲突不会被下一次完成提示掩盖）：Phone 完成时查询当前账号仍未解决的备忘录冲突，在服务状态和返回结果中报告；没有新快照时也重新查询。Watch 汇总尚未删除的稳定冲突副本，明确提示去备忘录检查，不将副本数误称为人工未解决数。Phone 手动同步结果提示使用实际状态消息，取消写死的“云同步完成”。
- Comment REQ-015（小状态不等于全文完成）：Phone 即使成功上传小状态，也保持正文等待 Wi-Fi 的阶段；Watch 的只读和可写路径同样区分部分传输。两端部分传输不更新完整同步完成时间，传输成功仍正常结束，不把等待正文的策略条件变成无限失败重试。
- 新增／扩展原生回归使用隔离账号、真实 Room 与实际服务路径，检查接收确认失败后的游标／时间、重试、无新快照时持续显示冲突，以及人工处理或删除副本后提示消失；HTTP 全部在测试拦截器中处理。构建与回归结果待本轮终态补记。

- 本轮最终构建／测试：Watch 全量 616 项 JVM、release Kotlin 编译及 installDebug 通过（/tmp/watchrss-watch-cloud-receipt-validation.log，6m32s）；4 项相关原生用例零失败／零跳过，测试后自动重新 installDebug 通过（/tmp/watchrss-watch-cloud-receipt-runtime-validation.log，2m34s）。Phone 全量 498 项 JVM、release Kotlin 编译、installDebug 和原生测试包编译通过（/tmp/watchrss-phone-cloud-receipt-final-validation.log，2m37s）；8 项相关原生用例零失败／零跳过（/tmp/watchrss-phone-cloud-receipt-runtime-validation.log，45 秒），测试后单独 installDebug 通过（/tmp/watchrss-phone-cloud-receipt-posttest-install.log，15 秒）。本轮按影响范围运行原生用例，之前完整原生套件记录仍保留在上节。
- 验证过程记录：首轮额外并行启动了同工程的 Gradle 测试包编译，Phone 两项 classes JAR 打包任务因编译产物争用失败，未运行该轮测试；已停止重复构建并按工程串行重跑。最终构建与原生行为断言均通过，未放宽生产逻辑或测试断言。
- 启动终态：两端显式启动各自 MainActivity 均返回 Status: ok，按未勾选协议的测试环境进入 OobeActivity／PhoneOobeActivity；adb crash buffer 与 AndroidRuntime／SQLite 错误日志为空，两端 git diff --check 通过。完整工单目标仍未关闭；Android 11 密钥兼容、真实双端批准／增量冲突与外部服务配置等继续保留验收边界。


### RSCH-003 A+B Android 11 云设备密钥兼容

- Comment RSCH-003（Android 11 设备密钥）：发现 Android 11 不支持原实现依赖的 Keystore ECDH purpose，且整页云同步因此被禁用。API 30 改用不可导出的 Keystore RSA-2048 私钥，通过 RSA-OAEP（SHA-256，Android Keystore 兼容的 MGF1-SHA1）封装 AES-256-GCM 设备信封；API 31 及以上继续使用现有 P-256/ECDH，已有设备密钥仍可读取。注册请求显式声明 RSA-2048 或 P-256，后端校验两种算法并为旧客户端保留 P-256 默认值。
- Comment RSCH-003（Android 11 验证）：Watch API 30 兼容／同步原生用例 4 项通过，Phone API 30 设备密钥原生用例 2 项通过；Watch 616 项 JVM、Phone 500 项 JVM 零失败，双方 release Kotlin 与 AndroidTest 构建通过，Phone／Watch Debug APK 均通过 installDebug。Watch 与 Phone MainActivity 在 API 30 模拟器启动成功，logcat 未发现 AndroidRuntime、崩溃或 SQLite 错误；Android 11 专项文件 `git diff --check` 通过。后端 RSCH-003 注册算法兼容单测通过。
- 运行边界：本轮设备验证使用 Android 11 API 30 模拟器及本地测试夹具／加密拦截器，没有真实账号授权、生产云端或双实体设备；这些跨端验收仍未完成。


### RSCH-004 B 火山方舟音频格式校验

- Comment RSCH-004（V3 SSE 输出格式）：按火山 V3 SSE 接口支持列表修正后端白名单，接受 `mp3`、`pcm`、`ogg_opus`，拒绝仅属于其他接口能力的 `wav`；此前实现恰好接受 `wav` 并拒绝 `ogg_opus`。新增协议回归覆盖两种边界，现有客户端 MP3 请求不变。依据：[火山 V3 SSE 语音合成文档](https://docs.volcengine.com/docs/DoubaoVoice/HTTPChunkedSSEUnidirectionalStreaming-V3?lang=zh)。
- 验证：RSCH-004 请求协议单测通过（1 项），目标 Rust 源文件经 rustfmt；全仓 `cargo fmt --check` 会因多处现有、与本次无关的格式差异退出，未对其余文件批量重排。授权音色／生产账号联调仍待真实密钥与资源配置。


### RSCH-005 A 小程序最小范围复核

- Comment RSCH-005（已有能力与范围）：复核 `WatchRSS_WeChat/docs/current-scope.md` 与路由／业务模块，最小闭环限定为既有账号登录、授权或密钥恢复、云端 RSS 阅读、阅读状态修改和跨端合并；TXT／EPUB 导入为可选能力。笔记编辑、RFCOMM、商业支付、BLE 实机和手表播放不作为该闭环的前置项；文档分别标明 mock、开发台、真实微信与正式发布的证据边界。
- 验证：在小程序仓库运行 `npm test`，103 项通过，零失败、零跳过。微信登录、多端真实云互操作、文件选择器和 BLE 仍需微信测试账号／真机，未以 mock 结果宣称实机验收。


### RSCH-004 B 火山方舟配置启动校验

- Comment RSCH-004（拒绝不完整的 Volcengine 配置）：选择 Volcengine 提供方时，启动配置必须同时包含授权音色和 `WATCHRSS_TTS_UPSTREAM_API_KEY`；缺少 API Key 时直接报出对应配置项错误，避免服务正常启动后才在朗读请求中返回未配置。MiniMax 默认提供方仍允许 API Key 缺省，既有配置行为不变。
- Comment RSCH-004（上游语音失败诊断）：Volcengine HTTP 失败及 SSE 协议／截断错误现在以结构化日志记录状态、错误类别和官方 `X-Tt-Logid`，不记录朗读文本、API Key 或音频，便于授权资源联调时定位问题。
- 验证：Backend `cargo check --locked --lib` 通过；`git diff --check -- src/config.rs` 通过。`cargo fmt --check` 会报告多个已有文件（包括该配置文件原有行）的格式差异，未运行格式化以免批量改写其他工作。


### RSCH-007 A 扫码页亮屏与清理

- Comment RSCH-007（B站扫码等待亮屏）：BiliLoginScreen 在二维码有效且未登录时设置 Compose 宿主 keepScreenOn；二维码为空或登录成功时恢复进入页面前的值，离开页面由 DisposableEffect 清理。增加 Activity 原生生命周期用例，验证待扫码状态亮屏，并在模拟扫码成功、Activity 结束后恢复标记。
- 验证：API 30 模拟器 `BiliActivitySmokeTest#qrLoginKeepsScreenAwakeWhileWaitingAndRestoresItWhenFinished` 通过（1 项），AndroidTest APK 编译成功，`git diff --check` 通过。设备矩阵、不同屏幕／扫码端识别效果仍需真实手表验收。


### REQ-022 B 收藏列表主题迁移

- Comment REQ-022（收藏列表统一背景）：收藏列表外层改用 `WatchSurface`，移除页面自设的 Material 背景，使其与已迁移的手表页面共享统一背景规范；列表、排序状态、撤回和项目操作不变。
- 验证：`./gradlew :app:installDebug` 成功；Android 37 模拟器启动 MainActivity，进程正常且 logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。收藏列表圆屏视觉仍需真机验收。


- Comment REQ-022（B站回复详情背景迁移）：回复详情 Scaffold 与顶栏改为透明，由统一 `WatchSurface` 提供页面背景；主题前景色、回复加载与下拉刷新、导航行为不变。`./gradlew :app:installDebug` 成功；Android 37 模拟器启动 MainActivity，进程正常且 logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。回复详情实际路由与圆屏视觉仍待设备验收。

- Comment REQ-022（抖音列表背景迁移）：抖音列表页统一包在 `WatchSurface` 中，使未登录提示、空态与信息流共用手表页面背景；下拉刷新、分页加载及卡片操作不变。`./gradlew :app:installDebug` 成功；Android 37 模拟器启动 MainActivity，进程正常且 logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。列表路由与圆屏实际视觉仍待设备验收。
- Comment REQ-022（B站收藏列表接入统一手表背景）：收藏／稍后再看列表从自设 `MaterialTheme.background` 移入统一 `WatchSurface`，收藏夹选择弹窗仍独立显示；刷新、收藏夹切换、分页和条目点击不变。`./gradlew :app:installDebug` 成功；Android 37 模拟器启动 MainActivity，进程正常且 logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。收藏列表实际路由及圆屏视觉仍待设备验收。
- Comment REQ-022（首页列表统一背景）：首页下拉刷新容器从直接绘制 `MaterialTheme.background` 改由统一 `WatchSurface` 承载，保留原 `HomeTestTags.ROOT`、列表、刷新和滚动回调。`./gradlew :app:installDebug` 成功，MainActivity 启动后进程正常；API 37 模拟器当前被协议引导重定向到 `PrivacyPolicyConsentActivity`，因此未声称首页实际页面已打开；logcat 未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。首页圆屏视觉仍待设备验收。
- Comment REQ-022（B站 RSS 信息流统一背景）：B站 RSS 列表在保留固定尺寸密度、刷新状态及内容布局的前提下接入 `WatchSurface`，登录空态、内容卡片和分页提示共享同一页面背景；卡片媒体占位的局部黑色背景仍保留。`./gradlew :app:installDebug` 成功；Android 37 模拟器启动 MainActivity，进程正常且 logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。实际频道路由与圆屏视觉仍待设备验收。
- Comment REQ-022（抖音网络状态页统一背景）：抖音登录前的“正在检测网络”与“网络不可用／蓝牙网络”页改用统一 `WatchSurface`，保留互联网状态提示、返回按钮和原状态分支。`./gradlew :app:installDebug` 成功；Android 37 模拟器启动 MainActivity，进程正常且 logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。网络状态页的圆屏视觉仍待设备验收。
- Comment REQ-022（B站评论页背景迁移）：评论列表的 Scaffold 与顶栏改为透明，由统一 `WatchSurface` 承载页面背景；下拉刷新、分页加载、评论回复入口和返回动作不变。`./gradlew :app:installDebug` 成功；Android 37 模拟器 MainActivity 正常运行，logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。评论页需 B站数据／登录态，尚未实际进入页面，圆屏视觉仍待验收。
- Comment REQ-022（删除确认危险语义色）：通用删除确认按钮由固定 `danger_red` 资源改为 `MaterialTheme.colorScheme.error`，让危险操作跟随全局主题语义色；警告确认仍用主色，按钮图标、尺寸和确认／取消回调不变。`./gradlew :app:installDebug` 成功，MainActivity 启动后进入协议引导页且进程正常；未声称删除弹窗已在模拟器实际打开；logcat 未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。圆屏明暗主题对比度待设备验收。
- Comment REQ-022（抖音历史页统一手表背景）：播放历史列表外层改用 `WatchSurface`，统一空状态、清空历史入口与历史条目的页面背景；列表尺寸、清理回调和打开视频行为不变。`./gradlew :app:installDebug` 成功；Android 37 模拟器 MainActivity 进入协议引导页且进程正常，logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。历史页未在模拟器实际打开，圆屏视觉仍待验收。
- Comment REQ-022（确认弹窗深浅主题对比度）：通用确认弹窗的圆形内容面板改用 `MaterialTheme.colorScheme.surface`，遮罩改用主题 `scrim`；内容文字继续用 `onSurface`，确保浅色主题下文字与面板背景配对，按钮危险／警告语义色及回调不变。`./gradlew :app:installDebug` 成功；MainActivity 启动后落在协议引导页，弹窗未实际打开；进程正常且 logcat 未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。浅色／深色圆屏对比度仍需页面级验收。
- Comment REQ-022（抖音历史条目触控与语义）：每条历史记录整行设置至少 48dp 高度，并向辅助技术声明 Button 角色；原条目点击和打开对应视频的行为保持不变。`./gradlew :app:installDebug` 成功；Android 37 模拟器 MainActivity 进程正常，logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。条目实际无障碍树与圆屏触控体验仍待页面级验收。
- Comment REQ-027（收藏夹标题按钮语义）：可切换的当前收藏夹标题除了已有“切换收藏夹”点击说明外，额外声明 Button 角色；非收藏列表仍禁用该点击语义，选择弹窗与分页状态逻辑不变。`./gradlew :app:installDebug` 成功；Android 37 模拟器 MainActivity 进程正常，logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。收藏夹弹窗真实列表交互仍待登录数据与圆屏验收。
- Comment REQ-027（收藏夹标题触控高度）：收藏列表可交互标题的目标高度补到至少 48dp，与“切换收藏夹”按钮语义和提示范围一致；其他列表标题保持禁用。`./gradlew :app:installDebug` 成功；Android 37 模拟器 MainActivity 进程正常，logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。收藏夹标题实际触控仍待真实页面与圆屏验收。
- Comment REQ-021（关于页备案入口可访问性）：关于／信息页备案号入口设置至少 48dp 高度，增加 Button 角色及“查看备案信息”动作提示；显示备案编号与既有导航回调不变。`./gradlew :app:installDebug` 成功；Android 37 模拟器 MainActivity 进程正常，logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。备案入口实际页面与辅助技术朗读仍待页面级验收。
- Comment REQ-021（个人中心备案入口可访问性）：个人中心备案号入口扩大为至少 48dp 高度，并公开 Button、轻点“查看备案信息”和长按“复制备案号”动作；备案号文案、MIIT 跳转与复制反馈保持不变。
- Comment REQ-017／REQ-022（帮助主题导航层级）：帮助主题由多行全宽按钮改为横向单选 FilterChip，选中状态使用控件语义呈现，正文集中显示当前主题；本地帮助、版本说明与在线公告加载流程不变。
- Comment REQ-017（在线帮助刷新状态播报）：帮助页区分在线刷新成功和未取得内容／协议限制的视觉状态，并将反馈设为礼貌级 live region；失败时仍保留离线帮助及已缓存公告。
- Comment REQ-019／REQ-021（协议确认与隐私链接可访问性）：登录协议页隐私链接目标高度至少 48dp，声明 Button 角色和“打开隐私政策”提示；同意／不同意按钮保留 48dp 圆形目标，并向辅助技术暴露各自按钮名称。协议勾选校验及三个原有回调不变。`./gradlew :app:installDebug` 成功；Android 37 模拟器 MainActivity 进程正常，logcat 未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。协议页实际无障碍树与圆屏操作仍待页面级验收。


- Comment RSCH-006（Android 11 生命周期回归）：`BiliAudioServiceTest` 两项在 API 30 模拟器复跑通过，覆盖空会话不自动播放、无效 URL 拒绝及媒体会话暂停／继续／停止状态；这补充 Android 37 既有生命周期证据，不替代 B站 CDN、真实中断与设备功耗验证。
- Comment RSCH-006（后台音频状态反馈）：播放页分别显示正在播放、缓冲、暂停、结束和空会话；控制按钮依据 `playWhenReady` 决定“暂停／继续”，状态文案依据播放器真实输出状态，避免把缓冲和暂停都显示成播放中。


### BUG-005 小说章节与书籍未读红点一致性

- Comment BUG-005（小说状态同源）：以实际 EPUB 书籍频道和章节记录复核统一 Room 聚合查询。打开单章后该章 `isRead` 与书籍 `unreadCount` 同步从 2 降为 1；整书已读后聚合归零，数据库中仍有章节目录。未新增另一份缓存红点状态，章节及书籍都由 `rss_items.isRead` 驱动。
- 验证：本轮重新运行 `NovelUnreadProjectionRuntimeTest`，API 30 模拟器通过（1 项）；目标 AndroidTest APK 编译成功。当前证据覆盖 Room 持久层与聚合 SQL，不覆盖实体圆屏上的红点重绘时序，后者仍待真机。


### BUG-003 网络切换后的封面恢复

- Comment BUG-003（已验证网络有限重试）：抽出默认网络状态跟踪器；封面只在每个新的已验证网络首次可用时重试，重复能力回调不重复请求，旧网络迟到的丢失事件不清除当前网络。加载中的封面明确显示状态，失败封面保留点击重试；已成功缓存的封面不因换网重新加载。
- 验证：新增两项网络切换状态回归，Watch 全量 621 项 JVM 测试零失败／零跳过，`:app:installDebug` 成功。API 30 模拟器启动 MainActivity，logcat 未见 AndroidRuntime／SQLite 错误。该模拟器未实际模拟 Wi‑Fi 与蜂窝互切，真实网络切换仍待设备验收。


### BUG-002 最新 Toast 立即替换旧提示

- Comment BUG-002（先取消后展示）：所有应用 Toast 共用主线程槽位；每次新提示先同步 cancel 当前 Toast，再创建并 show 新提示。槽位只持有当前 Toast，调用统一使用 applicationContext；后台调用先投递主线程。新增顺序回归，断言 `show(old) → dismiss(old) → show(latest)`。
- 验证：Watch 全量 621 项 JVM 测试零失败／零跳过，`:app:installDebug` 成功。Android 11 模拟器主界面启动成功，logcat 未见 AndroidRuntime／SQLite 错误。测试覆盖替换顺序，不模拟 Wear OS 系统 Toast 的实际动画时长。


### BUG-004 同步预设后当前样式立即更新

- Comment BUG-004（活动样式跟随同步结果）：阅读器继续订阅活动预设，字体与背景属性变化会重组运行时；背景图片缓存键包含资源版本。云端归档恢复测试现在对已应用预设同时覆盖字号和纯色背景，并等待活动预设流实际发出新值后断言两项更新。
- 验证：`:app:assembleDebugAndroidTest` 与 `:app:installDebug` 构建／安装成功；API 30 模拟器运行 `WatchCloudReaderRuntimeTest` 的 2 项原生测试通过。启动 MainActivity 后 logcat 未见 AndroidRuntime 崩溃或 SQLite 异常。此测试验证 Room 活动预设数据流及资源恢复，不替代 Wear OS 阅读器界面实时重绘验收。

- Comment REQ-022（手机主导航标签语义）：首页、RSS、小说导航项加入可选择标签页语义，导航组声明为单选组，当前项向辅助技术公开选中状态；保留原有玻璃背景、触摸尺寸和页面切换回调。
- Comment REQ-022（RSS 分段导航语义）：频道、收藏、稍后和独立分段改为单选标签页组，每项向辅助技术公开选中状态；保留原有选中视觉、无涟漪交互和切换回调。
- Comment REQ-022（RSS 分段选中态对比度）：选中背景／文字改用 `secondaryContainer/onSecondaryContainer` 成对语义色，移除受玻璃背景影响的半透明主色配白字；分段标签语义、未选中颜色和切换行为不变。
- Comment REQ-022（分类字体设置入口语义）：分类字体设置整行继续作为开关旁的独立“调整分类字体”入口，并显式声明 Button 角色和操作标签；关闭分类字体时仍禁用该入口，行内开关行为不变。
- Comment REQ-022（视频背景缩略图编辑语义）：可编辑的视频背景缩略图声明 Button 角色及“编辑视频背景”操作标签；图片背景继续作为不可点击预览，原视频编辑回调和网格布局不变。
- Comment REQ-022（手机浮动操作按钮一致性）：RSS、列表与指南页共用的玻璃浮动按钮改用主题 `onSurface` 前景色，并声明 Button 角色；按钮文案、启用状态、背景透明度与点击回调不变。
- Comment REQ-022（频道详情返回转场边界）：频道详情卡片的返回按钮只在收起转场的可返回阶段启用，并公开 Button 角色与“返回频道列表”操作标签；转场完成后禁用无效点击，避免按钮可点但无动作。
- Comment REQ-022（手机频道行主操作语义）：普通 RSS 频道整行明确声明 Button 角色和“打开频道”操作标签；右侧频道操作菜单保持独立按钮，避免辅助技术将主操作与次级菜单混为一体。
- Comment REQ-022（频道文章操作触控目标）：文章卡片的收藏、稍后再看与打开原网页操作扩大为至少 48dp 触控布局，并声明 Button 角色；图标仍按转场进度缩放，既有动态操作描述及回调保留。
- Comment REQ-022（导入提示关闭入口）：导入状态和错误提示不再要求点击提示文字来关闭，改为显式关闭图标按钮并提供“关闭提示”语义标签；提示文本、错误颜色与消息状态处理不变。
- Comment REQ-022（RSS 频道卡片操作语义）：频道卡片主操作声明 Button 角色和“打开频道”标签，长按菜单公开“频道操作”标签；普通点击、长按菜单与置顶／删除回调不变。
- Comment REQ-022（RSS 文章卡片操作语义）：文章卡片主操作声明 Button 角色和“打开文章”标签；仅可删除的条目公开长按“文章操作”，原文、收藏、稍后和删除动作保留。
- Comment REQ-022（同步卡片主操作语义）：手表同步主按钮声明 Button 角色和“同步资料库”点击标签；长按仍明确提示强制全量同步入口，忙碌时两种触发继续禁用。
- Comment REQ-022（字体导入系统字体选项）：系统字体选项公开 Button 角色和“选择系统字体”操作标签；字体来源说明、触控区域和选项回调不变。
- Comment REQ-022（字体导入文件选项）：自定义字体文件选项公开 Button 角色和“从文件导入字体”操作标签；支持格式说明及文件选择流程不变。
- Comment REQ-022（字体库详情卡片）：字体卡片主区域公开 Button 角色和带字体名称的详情操作标签；右侧字体菜单仍独立提供详情、重命名与删除。
- Comment REQ-022（同步失败状态主题对比）：同步失败提示改用 `errorContainer/onErrorContainer` 配对色，并增加轻量内边距突出错误阶段；正常同步状态继续沿用卡片主主题，错误详情仍由任务详情页提供。
- Comment REQ-022（手表实时预览开关交互）：移除实时预览设置行与内部 Switch 重复的整行点击切换，只保留 Switch 作为唯一开关入口，避免读屏焦点出现嵌套重复操作以及行点击与开关状态难以区分。
- Comment REQ-022（阅读预设资源选择器语义）：字体等资源下拉按钮向辅助技术明确播报设置名称和当前选项，并标记选择操作；导入按钮补充明确的操作标签，避免仅依赖相邻静态标题推断控件用途。
- Comment REQ-022（调色盘触控目标）：设置页颜色预览／调色盘入口高度从 40dp 增至 48dp，并声明按钮角色及打开操作标签，提升圆屏上直接点按色块的命中率；颜色输入和调色盘交互逻辑不变。
- Comment REQ-022（备忘录文字颜色选项触控与语义）：颜色菜单项增加至少 48dp 高度，并声明按钮角色及包含颜色名称的操作标签；选中状态、颜色值与文本格式应用回调保持原样。
- Comment REQ-022（协议正文加载与错误状态背景）：协议正文加载中和加载失败页面改用统一 `WatchSurface`，避免这两个状态独立写死黑底而与信息页主题容器不一致；重试按钮、提示文案和网络加载逻辑不变。
- Comment REQ-022（RSS 搜索命中对比度）：搜索结果标题与摘要的命中底色继续使用主题 `primary`，前景文字改用配对的 `onPrimary`，避免固定黑字在深色主色上难以辨认；未命中正文仍各自继承标题／摘要主题色。
- Comment REQ-022（RSS 搜索结果卡片操作语义）：搜索结果整卡显式声明 Button 角色并提供“打开搜索结果”操作标签；辅助技术可将卡片识别为可打开入口，结果打开回调和卡片布局不变。
- Comment REQ-022（RSS 实时搜索入口状态）：共享搜索框仅在调用方提供提交回调时展示搜索按钮；RSS 实时过滤页面不再展示一个无动作的禁用放大镜，B站显式提交搜索入口保持不变。
- Comment REQ-022（B站视频卡片操作语义）：共享 B站视频卡片声明 Button 角色、“打开视频”点击标签及“视频操作”长按标签；搜索结果与信息流共用该卡片，原播放和长按回调不变。
- Comment REQ-022（B站搜索加载失败恢复）：首次刷新和追加分页失败时都提供持续可见的“重试”按钮并调用 Paging `retry()`，不再只依赖短暂 Toast 与错误文字；成功、加载中和空结果逻辑不变。
- Comment REQ-022（B站胶囊按钮操作语义）：登录、重试、播放控制与详情等共用胶囊按钮声明 Button 角色，并使用按钮可见文案作为点击操作标签；启用状态、样式及原回调不变。
- Comment REQ-022（RSS 信息流卡片语义）：RSS 文章卡片声明 Button 角色、“打开文章”点击标签和“文章操作”长按标签，频道标题入口标记为“查看频道信息”；滑动操作、长按菜单和文章回调不变。
- Comment REQ-022（B站 RSS 信息流卡片语义）：B站 RSS 文章卡片声明 Button 角色及打开／文章操作标签，频道标题入口声明“查看频道信息”；收藏滑动、长按与页面导航回调不变。
- Comment REQ-022（信息流侧滑操作按钮语义）：RSS、B站及B站 RSS 共用的收藏／稍后再看侧滑操作声明 Button 角色，并使用已有无障碍名称作为点击操作标签；图标、文字、操作宽度及回调不变。
- Comment REQ-022（抖音登录错误状态语义色）：错误图标改用主题 `error`，错误标题与说明改用 `onSurface/onSurfaceVariant`；当前暗色下保持既有层级，同时不再依赖固定白／灰／橙色。
- Comment REQ-022（阅读器点击翻页辅助语义）：点击翻页模式左右触控区分别声明“上一页”和“下一页”按钮操作标签，辅助技术可直接调用对应翻页动作；区域布局、触摸手势与翻页边界不变。
- Comment REQ-022（AI 总结入口触控范围）：阅读页 AI 总结展开／收起按钮的可点范围扩至至少 48dp，内部圆形图标和底色继续保持 38dp；加载、展开状态和回调不变。
- Comment REQ-022（阅读页图标操作按钮角色）：阅读页分享、阅读方式、自动滚动设置及 AI 总结重试等自定义点击入口通过共享点击修饰符声明 Button 角色；原有图标描述、启用状态与动作保持不变。
- Comment REQ-022（阅读页收藏按钮触控尺寸）：收藏动画外层不再用 32dp 图标尺寸限制已有 48dp 最小交互目标，确保子按钮的触控范围能实际扩展；星标视觉尺寸与收藏动画逻辑不变。
- Comment REQ-022（B站 RSS 阅读操作触控与角色）：收藏／分享圆形入口将可点击层扩展为至少 48dp，图标圆形视觉尺寸保持原样，并为点击声明 Button 角色；收藏状态、分享回调与忙碌禁用逻辑不变。
- Comment REQ-022（侧滑操作图标防重复播报）：收藏／稍后等侧滑按钮由父级统一播报操作名称，内部图标改为装饰语义，避免图标名与按钮无障碍名称重复朗读；可见图标和文字不变。
- Comment REQ-022（B站详情操作触控与语义）：详情页收藏等图标按钮扩大到至少 48dp 触控范围并公开 Button 角色和动作名称；圆形图标外观、下方文字、收藏状态及回调保持不变。
- Comment REQ-022（B站详情卡片操作语义）：视频封面、简介展开／收起、评论入口和分集卡片均公开 Button 角色及具体点击动作标签；点击回调、卡片布局和选集行为不变。
- Comment REQ-022（B站互动状态辅助播报）：点赞、投币和收藏按钮向辅助技术公开已／未操作状态；分享按钮不附带选择状态，现有可用性、颜色反馈和操作回调不变。
- Comment REQ-022（B站搜索历史交互语义）：搜索历史胶囊标记为按钮角色，同时保留“搜索关键词”点击标签和“删除这条搜索历史”长按动作标签；48dp 触控范围与单条删除确认流程不变。
- Comment REQ-022（B站播放器控制触控范围）：播放／暂停与快退／快进命中区域至少 48dp，视觉图标尺寸不变；快退／快进仍保留现有按住连续加速逻辑。
- Comment REQ-022（B站快退快进辅助动作）：自定义手势快退／快进按钮声明 Button 角色和可访问单步点击动作；关闭时公开禁用状态，物理长按及松手恢复行为保持不变。
- Comment REQ-022（B站播放器控制播报去重）：播放控制按钮通过按钮层播报唯一操作名称，内部图标改为装饰语义，避免按钮和图标重复朗读相同动作；按钮角色、播放控制和快退快进手势不变。
- Comment REQ-022（B站 RSS 收藏状态播报）：收藏入口向辅助技术公开可选中状态及“已收藏／未收藏”描述，操作名称由按钮层统一播报；视觉状态、分享入口及收藏回调不变。
- Comment REQ-022（B站评论卡片操作语义）：可回复的评论卡片声明 Button 角色和“查看评论回复”操作标签；不可回复的评论仍不暴露虚假点击能力，卡片内容与回复回调不变。
- Comment REQ-022（B站频道入口触控与语义）：频道信息页搜索、稍后再看、历史与收藏夹入口至少 48dp 高，并公开 Button 角色和对应可见操作名称；登录状态、入口宽度和导航回调不变。
- Comment REQ-022（网页阅读缩放按钮触控与语义）：放大／缩小／标准缩放入口至少 48dp，并统一播报当前可执行操作及 Button 角色；图标视觉尺寸、网页缩放状态与回调不变。
- Comment REQ-022（圆形确认对话框动作语义）：删除确认和取消图标按钮声明 Button 角色及明确动作标签，删除确认播报“删除”；警告确认仍播报“确认”，对话框视觉与回调不变。
- Comment REQ-022（抖音历史清空按钮触控范围）：播放历史页的“清空历史”按钮高度至少为 48dp，避免小尺寸设备上难以点中；现有按钮语义、确认／清空回调及列表布局不变。
- Comment REQ-022（抖音历史条目播放操作标签）：每条可播放历史记录的 Button 语义加入带视频标题的“播放”操作标签，空标题使用“播放抖音视频”；条目摘要、排序和点击导航不变。
- Comment REQ-022（个人中心入口触控与语义）：收藏、稍后、手机连接、设置、关于及联系开发者入口至少 48dp 高，并声明 Button 角色及入口名称；页面排列、条件显示与导航回调不变。
- Comment REQ-022（抖音二维码脚本错误状态配色）：登录 WebView 的脚本错误遮罩改用 `errorContainer/onErrorContainer`，警告图标使用主题 `error`；错误信息和登录流程保持不变，深浅主题均有成对语义色。
- Comment REQ-022（抖音 Cookie 对话框主题遮罩）：仅 Debug 可用的手动 Cookie 输入层使用 Material `scrim` 主题色，而非固定黑色；输入框、保存／取消按钮及遮罩透明度保持不变。
- Comment REQ-022（确认对话框图标播报去重）：确认与取消动作名称仅由按钮节点播报，图标设为装饰语义，避免 TalkBack 同时朗读按钮标签和相同图标描述；Button 角色及动作标签不变。
- Comment REQ-022（阅读正文行内链接对比度）：正文行内链接默认继承当前阅读预设的正文色并保留下划线，不再固定使用浅蓝色；浅色／深色预设下跟随正文对比度，显式指定链接色的调用方不变。
- Comment BUG-003（RSS 封面失败重试语义）：失败占位区域在保留点击重试的同时声明 Button 角色和“重试封面加载”操作标签；切网有限重试、成功缓存与占位文案不变。
- Comment REQ-022（B站评论加载失败恢复）：评论和回复列表的刷新及追加分页失败态显示持续可用的“重试”按钮，并调用 Paging retry；加载中提示、错误文案与成功列表不变。
- Comment REQ-024（同 URL 图片请求合并）：可见缩略图与相邻预取同时请求同一 URL 时共享一个内存／磁盘／网络加载结果，避免并发重复下载和重复解码；缓存键继续按账号资料库隔离，失败仍交由有限切网重试或手动重试恢复。
- Comment REQ-023（B站二维码失败恢复入口）：二维码过期或登录失败时直接展示“刷新二维码”按钮，并在刷新中禁用；原有下拉刷新、扫码帮助和状态提示保留。
- Comment REQ-022（RSS 推荐频道操作触控与语义）：推荐分组公开带名称的打开动作；添加频道按钮扩大为至少 48dp 命中区，视觉加号保持 20dp，并播报具体频道添加操作。
- Comment REQ-022（B站共享胶囊按钮触控高度）：共享 B站胶囊按钮高度从资源定义的 44dp 提升至至少 48dp，覆盖登录、重试与其他同组件入口；文字、宽度、颜色和点击语义不变。
- Comment REQ-004／REQ-022（自动滚动文字操作触控）：自动滚动控制页文字型动作从 36dp 提升为至少 48dp，并通过 Button 点击标签播报动作；速度调节状态和原始点击回调不变。
- Comment REQ-022（日志上传隐私页主操作）：“开始上传”入口至少 48dp 高，并向辅助技术声明 Button 角色与操作标签；隐私文案、上传确认前置和跳转回调不变。
- Comment REQ-022（抖音频道信息按钮语义）：频道页登录、设置、历史与标记已读按钮公开 Button 角色和各自操作名称；48dp 尺寸、启用状态与原导航／动作回调不变。
- Comment REQ-022（关于页入口动作标签）：项目介绍、隐私、服务条款与贡献者入口使用标题作为按钮动作标签，提升读屏操作播报的明确性；48dp 触控高度、摘要和原导航回调不变。
- Comment REQ-022（新手设置步进按钮触控目标）：字号和静音开播上限的圆形加减按钮扩大为至少 48dp 命中区，20dp 圆形视觉保持不变；读屏播报明确的 Button 角色与增减操作名称，禁用状态仍不可执行。新手引导步骤与设置值更新逻辑不变。
- Comment REQ-022（新手设置主题开关触控目标）：阅读主题切换扩大为至少 48dp 的 Switch 命中区，20dp 的浅／深主题圆形预览保持原样；已选主题状态继续通过开关语义播报，点击仍即时切换预览主题。
- Comment REQ-022（共用开关垂直触控范围）：`WatchSwitch` 的交互区域最小高度扩大到 48dp，原 60×35dp 轨道与滑块视觉保持不变；单选开关角色、已选状态、禁用状态及各设置回调不变，覆盖新手引导与 RSS／B站设置页共用入口。
- Comment REQ-022（设置胶囊行按钮语义）：通用设置胶囊行仅在具备点击回调时声明 Button 角色，并以可见标题作为操作标签；静态设置行仍不暴露虚假点击能力，现有 52dp 触控高度、布局及回调不变。
- Comment REQ-022（RSS 设置圆形步进按钮）：阅读设置与 TTS 音色／语速入口使用的圆形按钮触控范围扩大为至少 48dp，20dp 圆形视觉保留；按钮声明 Button 角色并播报对应的增减／音色操作标签，禁用行为及原始设置回调不变。
- Comment REQ-022（抖音登录结果页主题适配）：登录成功与加载失败两个原生结果页使用 Material 主题背景和对应前景色，错误详情在深浅主题下保持层级对比；登录 WebView 与扫码等待页保留用于扫码识别的黑底，流程和结果状态不变。
- Comment REQ-022（首页侧滑删除主题语义色）：首页卡片侧滑“删除”操作由固定 `danger_red` 改用 `MaterialTheme.colorScheme.error`，危险动作颜色随深浅主题变化；其他侧滑操作、图标布局和删除回调不变。
- Comment REQ-022（新手引导离线确认按钮动作语义）：离线继续确认弹窗的取消／确认圆形按钮分别声明 Button 角色和“取消离线继续／确认离线继续”操作标签；原有圆形视觉、触控尺寸、测试标签及回调不变。
- Comment REQ-022／REQ-028（下载手机 App 弹窗关闭动作语义）：圆形关闭图标入口声明 Button 角色和“关闭下载页”操作标签；104dp 视觉目标、二维码展示、浏览器能力判断和关闭回调不变。
- Comment REQ-022／REQ-028（下载方式文字入口语义）：打开手机端下载弹窗的说明文字声明 Button 角色和“查看手机端下载方式”操作标签；可用浏览器能力检测、原文案、点击行为及弹窗内容不变。
- Comment REQ-022／REQ-014（B站搜索卡片操作名称）：视频、用户和影视搜索卡片分别声明 Button 角色及包含结果标题的打开动作标签，空标题使用明确类型兜底；高亮、卡片布局和原点击导航不变。
- Comment REQ-022／REQ-018（网络状态操作按钮语义）：网络可用性页面和覆盖层的重试／设置动作声明 Button 角色，并以实际按钮文案作为操作标签；检查中禁用状态继续保留，网络判断、提示和回调不变。
- Comment REQ-022（RSS／B站信息流侧滑动作语义）：RSS 胶囊操作和 B站侧边菜单动作均声明 Button 角色，并以实际动作文案作为标签；禁用操作仍不响应点击，视觉状态、动作可用条件和回调不变。
- Comment REQ-022／REQ-014（B站视频卡片与抖音频道标题动作语义）：B站整卡视频入口声明 Button 角色并播报带标题的打开动作；抖音主 feed 与沉浸页标题区播报“打开抖音频道信息”。卡片与标题视觉、内嵌登录入口和导航回调不变。
- Comment REQ-022（文章阅读操作轻点／长按语义）：通用文章操作按钮（阅读原文、朗读、离线媒体重试）声明 Button 角色与轻点动作名称；有长按说明回调的操作额外播报“查看…说明”，无长按入口时不暴露虚假动作。启用状态、48dp 最小高度及回调保持不变。
- Comment REQ-022（分享二维码页关闭操作标签）：整页点击返回的二维码分享界面为关闭动作补充“关闭二维码分享页”标签，保留页面说明、二维码子节点和原返回回调；页面本身不误声明为 Button。
- Comment REQ-022／REQ-009（抖音标题页主入口操作语义）：标题页圆形箭头根据内容状态声明“进入抖音视频流”或“刷新抖音内容”按钮动作；图标替代描述、空／有内容分支和原有导航／刷新回调不变。
- Comment REQ-022（RSS 与 B站频道提示符主题对比）：RSS、B站首页和 B站 RSS 首页标题中的点击提示符从固定浅灰改用 `onSurfaceVariant`，确保浅色主题下不再与白色背景混为低对比装饰；标题、字号层级和点击行为不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过；浅色圆屏实机观感仍待验收。
- Comment REQ-022／REQ-014（B站频道标题入口可访问性）：B站首页可点击标题声明 Button 角色与“打开哔哩哔哩频道信息”操作标签；关闭频道菜单的遮罩入口声明相应关闭操作，视觉布局与原点击行为不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过；读屏实机体验仍待验收。
- Comment REQ-022／REQ-005（朗读自动下一篇状态播报）：自动下一篇入口现在播报稳定名称、独立开启／关闭状态及“切换自动下一篇”按钮动作，方便读屏用户识别当前值与执行结果；开关回调和布局不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过；TTS 页面读屏实机体验仍待验收。
- Comment REQ-022（B站搜索页面层级语义）：搜索页标题、搜索历史和热门搜索分组现在声明标题层级，读屏可按页面结构跳转；文本、顺序、搜索和删除操作不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过；设备读屏导航仍待验收。
- Comment REQ-022／REQ-005（自动下一篇开关角色修正）：朗读自动下一篇入口由通用 Button 角色改为 Switch 角色，保留独立开启／关闭状态和“切换自动下一篇”动作名称，使辅助技术按真正的开关呈现；`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。
- Comment REQ-022（RSS 搜索页标题层级）：RSS 搜索页面标题声明标题语义，读屏可与 B站搜索页一样按页面结构导航；标题样式和搜索行为不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。
- Comment REQ-022（RSS 搜索结果动作名称）：搜索结果卡片的轻点操作包含文章标题，空标题回退播报“无标题”，让读屏用户区分各条结果；卡片内容、高亮与跳转不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。
- Comment REQ-022（文章内图片查看动作名称）：正文图片点击入口增加“查看文章图片”动作标签，读屏可分辨查看操作与图片说明；图片布局、滚动禁用规则和预览回调不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。
- Comment REQ-022（文章内视频播放动作名称）：正文视频卡片增加“播放文章中的视频”点击标签；其他共享点击入口默认不带额外标签，既有导航与媒体回调保持不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。
- Comment REQ-022（AI 总结失败重试动作名称）：AI 总结错误区域的整块点击重试入口声明“重试 AI 总结”动作标签；错误展示、重试可用条件与原回调不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。
- Comment REQ-022（B站评论回复入口可区分）：评论卡片打开回复的按钮标签包含作者及最多 40 个字符的评论摘要，缺作者时使用“用户”；列表内仍使用原回复目标和点击逻辑。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。
- Comment REQ-022（B站评论下拉刷新状态）：刷新指示器现在等待 Paging 刷新状态经历 Loading 并结束后关闭，不再在 `refresh()` 调用返回时提前消失；最多 45 秒兜底清除异常卡住的旋转状态，错误态重试和分页行为不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。
- Comment REQ-022（B站回复空结果状态）：回复详情刷新成功且结果为空时明确展示主题次级色“暂无回复”，避免根评论下方留下无说明的空白；加载、错误重试和已有回复分页保持原样。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。
- Comment REQ-022／REQ-014（回复详情显示父评论）：回复首屏分页结果中的 `root` 评论现在回传到 `BiliReplyViewModel.rootComment`，详情页能显示正在查看的父评论及其“所有回复”分隔区；回复分页数据与打开目标不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，真实 B站登录回复链路仍待设备验收。
- Comment REQ-022（回复刷新创建新 PagingSource）：Pager factory 改为每次刷新创建新的 `ReplyPagingSource`，不再重复返回同一个已消费源；父评论回传回调随新源建立，分页参数与缓存策略不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，真实回复翻页仍待设备验收。
- Comment REQ-022（评论刷新防重复触发）：刷新入口在回调内同步置位刷新状态，并在启动新分页请求前检查该状态，快速重复下拉不会并发发起多个刷新；现有请求完成等待与 45 秒兜底逻辑保持不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。
- Comment REQ-022／REQ-011（播放器进度滑块播报）：进度滑块现在声明用途，并随拖动播报当前位置／总时长，Material Slider 的可调范围和 seek 写入逻辑保持不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常；播放器读屏实机体验待验收。
- Comment REQ-022／REQ-011（播放器音量滑块播报）：音量滑块现在声明“音量”并读出当前百分比，表冠音量调节与滑块更新仍使用同一音量状态。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常；圆屏无障碍验收待完成。
- Comment REQ-022／REQ-011（播放器画面位置滑块播报）：水平画面位置滑块现在声明用途及相对中点的百分比，拖动、旋转映射和回中操作不变；不可平移时仍禁用。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常；真实圆屏操作待验收。
- Comment REQ-022（B站回复区块标题语义）：父评论下方“所有回复”声明为标题层级，读屏可从父评论内容直接跳转到回复列表；文本位置与分页逻辑不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，登录态回复页仍待设备验收。
- Comment REQ-022（B站评论刷新保留已加载内容）：只有没有旧评论时才以全屏状态替换列表；已有评论在下拉刷新期间继续显示，刷新失败时在列表顶部提供重试入口。初次加载、无结果空态、追加分页及原点击导航不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过。
- Comment REQ-022（B站回复刷新保留已有回复）：回复详情在重新加载时继续展示已加载的回复，刷新失败时在回复列表内显示重试入口；全屏加载／错误状态只用于暂无缓存数据的首次请求，父评论、空态和分页逻辑不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，真实登录态刷新仍待设备验收。
- Comment REQ-013（B站画面旋转当前值）：旋转选项旁持续显示当前角度，用户选择 0／90／180／270 度后可以确认当前结果；角度应用、平移回中和播放器返回逻辑不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常，实际旋转触控仍待设备验收。
- Comment REQ-015（同步进度避免误导性百分比与剩余时间）：手机同步首页改为展示阶段不定进度，并显示实际累计传输量与当前速度；移除把连接累计字节、阶段百分比混算出来的剩余时间估算。同步详情页已有阶段记录与错误信息保持不变。手机端 `./gradlew assembleDebug`、`./gradlew installDebug` 成功；Pixel 10 Pro（Android 17）启动 `com.lightningstudio.watchrss.phone/.MainActivity`，最近 3000 行日志未发现 AndroidRuntime／FATAL EXCEPTION／SQLiteException，`git diff --check` 通过。
- Comment BUG-003（B站视频封面网络恢复与失败重试）：将 RSS 已验证网络切换监听提取为共用修订号；B站视频列表卡片和详情封面在新可用网络到达后仅重试一次，并在加载失败时显示可点击的重试提示。成功封面保持不变；`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。
- Comment BUG-003（网络验证状态恢复后重试封面）：默认网络的同一个 `Network` 若先失去 `NET_CAPABILITY_VALIDATED`、后恢复，追踪器现会在失效事件清除旧网络标识，并在再次验证时仅发出一次重试 revision，避免只对“换成新 Network 对象”有效。已有成功封面仍不因验证状态变化而重载。
- Comment REQ-022（TTS 连通性结果主题配色）：朗读服务连通成功提示改用 `primaryContainer/onPrimaryContainer`，失败提示改用 `errorContainer/onErrorContainer` 成对主题色，移除固定绿色和半透明错误叠色；状态文字、延迟显示与测试流程不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。
- Comment REQ-022（B站 RSS 卡片缩放底色）：卡片滑动／按压缩放时露出的底层填充由固定纯黑改为 `MaterialTheme.colorScheme.surface`，与前景文章卡片一致，避免浅色主题出现黑色边缘；手势、缩放和滑动操作不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。
- Comment REQ-022（网络状态指示器主题色）：互联网不可用、蓝牙网络、互联网可用三个状态点改用主题 `error`、`tertiary`、`primary`，移除固定 RGB 红／橙／绿，使状态提示跟随应用主题；文字状态、布局和继续操作不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。
- Comment REQ-022（抖音登录成功徽标主题色）：登录成功圆形徽标改用 `tertiaryContainer/onTertiaryContainer` 配对主题色，移除固定绿色背景和白色勾号；成功图形、文案和扫码二维码的黑底保持原样。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。
- Comment RSCH-004（火山方舟语音部署默认路由）：后端通用、生产和测试环境示例与 Compose 默认路由均改为 Doubao Voice V3（`volcengine`／`seed-tts-2.0`）；示例明确要求配置服务端 API Key 和已授权 speaker，不提交或伪造凭据，MiniMax 仍可显式选用。当前真实音色联调仍需运维配置有效凭据；`cargo check --locked` 成功，本次修改的 5 个部署示例文件及 Android 工单记录 `git diff --check` 通过；后端全量 diff check 仍报告仓库原有 `src/observability.rs` 空白字符，本次未改该文件。
- Comment RSCH-004（Doubao Voice V3 SSE 结束帧）：解码器现在按官方协议接受完成码后的 `data: [DONE]`，并且只有收到成功完成帧与结束标记才返回音频；提前结束、DONE 顺序错误或完成后继续推送数据均视为无效流，避免把不完整结果缓存并播放。现有协议样例已同步更新；依据[火山引擎 Doubao Voice V3 SSE 文档](https://docs.volcengine.com/docs/DoubaoVoice/HTTPChunkedSSEUnidirectionalStreaming-V3?lang=zh)，后端 `cargo check --locked`、`rustfmt --check` 与相关文件 `git diff --check` 均通过；未运行测试。
- Comment RSCH-003 A（冲突暂停自动补传）：Phone 本地自动同步发现备忘录冲突时改抛显式“需要手动处理”状态，调度器不再把它按临时网络错误重试，并暂停自动补传直到用户手动同步成功；手动同步成功清除暂停，连接忙和网络异常仍按原有限次数重试。Phone `./gradlew installDebug` 成功，Pixel 10 Pro（Android 17）启动 `com.lightningstudio.watchrss.phone/.MainActivity` 后日志无 AndroidRuntime／FATAL EXCEPTION／SQLiteException，Phone 与 Watch `git diff --check` 均通过；未运行测试。
- Comment REQ-022（RSS 信息流卡片缩放底色）：卡片按压缩放时露出的底层填充改用主题 `surface`，与 RSS 卡片容器一致，浅色主题下不再闪出黑边；图片、收藏和侧滑行为不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。
- Comment REQ-022（B站信息流卡片缩放底色）：卡片按压缩放时露出的底层填充改用主题 `surface`，与 B站卡片容器一致，浅色主题下不再闪出黑边；播放、长按和侧滑行为不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。
- Comment RSCH-003 A（手动全量同步不重复触发自动补传）：自动调度变化源限定为内容变更与用户启用开关，不再因首次记住手表或解除暂停的配置更新而重复跑完整同步；手动全量合并完成不发送“待补传”完成事件，其他长时间手动操作结束仍会唤醒排队补传。Phone `installDebug` 成功，Pixel 10 Pro（Android 17）启动手机伴侣 `MainActivity` 后日志未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；Phone 与 Watch `git diff --check` 均通过，未运行测试。
- Comment REQ-022（OOBE 离线确认主题对比）：离线继续确认对话框遮罩改用主题 `scrim`、圆形内容底色改用主题 `surface`，与现有 `onSurface/onSurfaceVariant` 文字配对，避免浅色主题下深色文字落在固定黑底；网络提示、确认／取消操作和布局不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动且日志未见 AndroidRuntime／SQLite 异常；`git diff --check` 通过。
- Comment REQ-022（朗读自动下一篇关闭态主题对比）：关闭态图标和划线由固定浅灰改为主题 `onSurface` 的 38% disabled alpha，随主题前景变化并保留关闭态层级；开启态继续使用主题 `primary`。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动，最近日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过；朗读页实际主题视觉仍待圆屏验收。
- Comment REQ-022（播放器网络状态浮层语义色）：播放器网络状态浮层的红／橙／绿固定色改为主题 `error`／`tertiary`／`primary`，与网络状态独立页的指示语义一致；黑色遮罩、状态文案与重试行为不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动，最近日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过；播放器浮层视觉仍待圆屏验收。
- Comment BUG-004（同步预设资源安全替换）：蓝牙同步收到完整且哈希校验通过的新阅读器资源后，使用同目录原子替换并在不支持原子移动时回退到替换移动；不再先删除旧资源再尝试重命名，避免落盘失败时损坏当前活动预设所引用的文件。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动，最近日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过；同步中断与真实设备资源加载仍待验收，未运行测试。
- Comment BUG-004（手机到手表预设资源安全替换）：手机端接收手表字体／背景资源时，在分块完整性校验通过后使用同目录原子替换；平台不支持原子移动时回退到替换移动，不再先删除旧文件，避免同步中断或落盘失败破坏已可用资源。Phone `installDebug` 成功，Pixel 10 Pro（Android 17）启动手机伴侣后日志未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException，Phone `git diff --check` 通过；真实设备资源同步仍待验收，未运行测试。
- Comment REQ-007（蓝牙备忘录图片安全替换）：备忘录图片收到最后一块并通过 SHA-256 校验后，使用同目录原子替换；不再先删除目标图片，避免同步落盘失败时现有笔记失去可用图片。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动，最近日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过；手表与手机实机图片同步待验收，未运行测试。
- Comment RSCH-003（云恢复备忘录图片安全替换）：云归档校验后的图片安装改为原子替换并提供文件系统兼容回退，替换失败时保留原有图片文件；笔记状态仍在资源安装后提交。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动，最近日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过；真实云账号恢复待验收，未运行测试。
- Comment BUG-004（云恢复阅读预设资源安全替换）：云归档中的字体／背景资源安装改为原子替换，先校验临时文件再覆盖目标，避免写入失败删除当前预设可用资源；元数据仍在资源处理后合并。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动，最近日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过；真实云账号预设恢复待验收，未运行测试。
- Comment RSCH-003（云同步缓存安全更新）：清单与分块缓存更新使用原子替换并在文件系统不支持时回退，不再在临时文件重命名失败时删除已缓存版本；当前缓存原子写入与云传输流程不变。`:app:installDebug` 成功，Android 17 模拟器 MainActivity 启动，最近日志未见 AndroidRuntime／SQLite 异常，`git diff --check` 通过；云端真实恢复待验收，未运行测试。
- Comment BUG-004（手机备份恢复预设资源安全替换）：Phone 导入 WRSS 备份时，阅读器资源完整验证后以同目录唯一临时文件原子替换目标；不再先删旧字体／背景文件，避免恢复写入失败破坏当前预设资源。Phone `installDebug` 成功，Pixel 10 Pro（Android 17）启动手机伴侣，最近日志未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；Phone `git diff --check` 通过，真实备份恢复待验收，未运行测试。
- Comment REQ-007／RSCH-003（手机笔记图片恢复安全替换）：Phone 云笔记资源恢复与历史版本图片恢复都先校验临时文件，再原子替换图片目标；失败时保留旧图片，笔记引用发布顺序不变。Phone `installDebug` 成功，Pixel 10 Pro（Android 17）启动手机伴侣，最近日志未见崩溃／SQLite 异常；Phone `git diff --check` 通过，真实云恢复与历史回滚待验收，未运行测试。
- Comment RSCH-003（手机云缓存安全更新）：Phone 云端清单及密文分块缓存采用唯一临时文件和原子替换，失败时不先删除旧缓存；并发写不同版本时避免共享临时文件互相覆盖。Phone `installDebug` 成功，Pixel 10 Pro（Android 17）启动手机伴侣，最近日志未见崩溃／SQLite 异常；Phone `git diff --check` 通过，真实云端断网恢复待验收，未运行测试。
- Comment BUG-004（备份恢复检测同长度损坏资源）：Phone WRSS 阅读器资源恢复现在会对本机目标逐文件流式计算 SHA-256；即使损坏文件长度与备份相同，也会由已验证的备份资源原子修复，避免误判为可用而跳过。Phone `installDebug` 成功，Pixel 10 Pro（Android 17）启动手机伴侣，最近日志未见崩溃／SQLite 异常，Phone `git diff --check` 通过；损坏资源实机恢复仍待验收，未运行测试。
- Comment RSCH-003（两端原子替换兼容回退）：同步、云缓存、阅读器／备忘录资源、文章正文和备份文件的原子移动若遇到任一 I/O 异常（包含平台对已存在目标的处理差异），都会再尝试同目录 `REPLACE_EXISTING`；回退仍失败时错误向上返回，不会删除旧数据。Watch `:app:installDebug` 与 Phone `installDebug` 均成功，Pixel 10 Pro（Android 17）依次启动两端应用，最近日志未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；两端 `git diff --check` 通过，未运行测试。
- Comment BUG-003（RTOS B站封面网络恢复重试）：RTOS 的 B站信息流、搜索结果和详情页订阅网络状态，只在离线恢复在线时重新加载缺失封面，并在页面销毁时取消监听；已成功缓存的图片仍直接复用，避免网络事件重复拉取已显示封面。RTOS `bun run typecheck` 与 `bun run build:emulator` 均成功（Glyphix CLI 包 `glyphix-cli@0.8.0-rc`，实际二进制 `gx v0.11.0`），`git diff --check` 通过；OPPO Watch S 真机网络切换仍待验收，未运行测试。
- Comment BUG-003（RTOS B站收藏／历史／稍后列表封面网络恢复）：共享 `bili-list` 页面也接入离线恢复在线时的缺失封面重试，并在页面销毁时取消订阅；补齐此前只覆盖信息流、搜索和详情的遗漏列表入口。RTOS `bun run typecheck` 与 `bun run build:emulator` 均成功，Glyphix `gx v0.11.0` 成功编译 `src/bili-list/index.ux`，`git diff --check` 通过；真机切网仍待验收，未运行测试。
- Comment REQ-024（RTOS B站列表复用已显示封面）：信息流、收藏／历史／稍后列表和搜索结果追加分页或网络恢复时，仅对缺少本地封面的条目发起缓存／网络读取；已有 `localCover` 不再重复访问文件缓存或重新请求，失败条目仍由 BUG-003 网络恢复监听重试。RTOS `bun run typecheck`、`bun run build:emulator` 均成功，Glyphix 编译三个列表／搜索页面，`git diff --check` 通过；快速滚动设备体验待验收，未运行测试。
- Comment BUG-003（RTOS 在线网络切换封面重试）：网络监听除离线恢复在线外，也比较在线网络的设备／类型标识；Wi‑Fi 与蜂窝网络直接切换且 `online` 始终为真时，会重试失败封面，同一网络重复状态事件不会重复触发。RTOS `bun run typecheck`、`bun run build:emulator` 均成功，Glyphix 编译更新后的 `src/media/image-cache.ts`，`git diff --check` 通过；OPPO Watch S 真实网络切换仍待验收，未运行测试。
- Comment BUG-003（RTOS 封面／正文图片网络重试与失败反馈）：B站信息流、搜索、收藏／历史／稍后列表与详情，以及 RSS 频道预览和文章图片页，均在页面存续期间监听最多 3 次网络恢复事件；恢复时只重试仍缺失的图片。若同 URL 旧网络请求仍在途，加载器等待其失败后只补发一次新请求，并让并发页面共享该次重试。B站页面区分加载中、加载失败和无封面，失败列表卡片／详情封面提供轻点重试；销毁页面会解除网络监听。`bun run typecheck` 通过，六个修改过的 `.ux` 脚本块通过 TypeScript 语法转译检查；`bun run build:emulator` 因当前 PATH 没有 Glyphix `gx` 可执行文件而未能开始设备配置／构建，`git diff --check` 通过；Glyphix 页面构建和真实设备切换仍待验收，未运行测试。
- Comment REQ-026（RTOS 单条删除搜索历史）：B站搜索历史词条支持长按后确认单条删除，轻点仍执行搜索，删除后立即写回本地历史；原有“清空”继续可批量清理。RTOS `bun run typecheck`、`bun run build:emulator` 与 `git diff --check` 均通过；手表上的长按与确认弹层视觉仍待设备验收，未运行测试。
- Comment REQ-022（网络状态浮层文字对比）：网络不可用浮层固定黑色遮罩上的标题与说明改为白色，次级状态说明使用半透明白色；浅色应用主题下不再把暗色 `onSurface` 文字绘制在黑底上。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。浮层浅色主题实机视觉仍待设备验收。
- Comment BUG-002（RSS 首页重复提示不被 StateFlow 合并）：首页刷新反馈改为带递增 ID 的消息事件；相同错误连续发生时也会重新显示 Toast，清除回执携带事件 ID，只能清除对应提示，避免旧回执清掉更新提示。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（RSS 频道刷新相同错误提示）：频道刷新错误也改为带递增 ID 的反馈事件；相同错误再次出现不会被 `StateFlow<String?>` 相等值合并，自动清除只接受当前事件 ID，避免旧 Toast 生命周期覆盖新反馈。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（文章阅读操作提示去重）：阅读页的原文缺失提示改为带递增 ID 的事件，相同提示再次触发也可重新展示；清除回执按 ID 校验，旧事件不会清掉新提示。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（B站收藏／历史／稍后列表错误提示去重）：列表刷新与收藏分页错误增加独立消息事件编号，重复错误仍触发最新 Toast；延迟清除按编号匹配，旧提示不能覆盖新反馈。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（B站信息流点赞／收藏反馈去重）：推荐信息流的登录提示、刷新／分页错误、点赞收藏及稍后再看结果均通过递增事件 ID 触发反馈；相同文案连续操作不会被状态流合并，旧回执按 ID 清除，不会吞掉新 Toast。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（B站详情点赞／投币／收藏反馈去重）：详情页点赞、投币、收藏、稍后再看及操作错误都通过递增事件编号触发 Toast；连续相同文案也会刷新反馈，延迟清除只匹配发起时的编号。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（B站扫码登录结果提示去重）：二维码请求失败、手动 Cookie 结果、扫码登录成功／过期／失败均递增提示事件 ID；重复结果可重新反馈，旧回执只清理对应事件。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（抖音沉浸播放反馈去重）：抖音视频流的登录失败、播放刷新失败、需要登录和源刷新错误均递增 Toast 事件编号；播放页按编号消费，迟到的旧清除回调不能移除新反馈，相同文案重复触发也会显示。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（B站频道信息页刷新反馈消费）：频道信息页现在与共用的 `BiliFeedViewModel` 消息事件编号同步，重复刷新相同错误仍触发新 Toast，消费回执仅清除当前编号，避免频道页沿用旧字符串监听而漏掉最新反馈。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment REQ-022（自动滚动设置开关触控高度）：将“自动开始”开关所在行的固定 42dp 高度提升为至少 48dp，避免父行约束压缩开关点击区域；自动滚动状态、开关文案与保存回调不变。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（抖音 RSS 模式 Toast 消费）：RSS 模式下由 `DouyinEntryActivity` 直接显示的提示也改为监听消息事件编号，并按对应编号清除；视频流模式继续由沉浸页消费，共用 ViewModel 的两种模式都能正确呈现重复相同文案。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（B站设置退出登录反馈）：退出登录结果加入独立事件编号，Activity 按编号显示并清除提示；重复结果不会被同文案状态合并，旧回执不影响新消息。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（抖音设置退出登录反馈）：退出登录结果加入独立事件编号，Activity 按编号显示并清除提示；连续退出／登录状态切换时旧消息回执不能清除最新反馈。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（全局 Toast 并发请求排序）：公共 Toast 入口为每次有效调用分配原子递增编号；若旧后台请求排队后已有更新的提示请求，主线程会丢弃旧 Runnable，避免排队乱序让旧反馈覆盖最新反馈；实际展示时仍先取消当前 Toast 再显示。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment REQ-022（RSS 推荐分组触控高度）：推荐分组整卡点击区域增加 48dp 最小高度，避免单行标题与 9dp 垂直内边距组合时触控目标不足；卡片内容、分组跳转语义与点击回调保持不变。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment REQ-022（抖音信息流主题背景迁移）：抖音信息流外层容器从固定黑色改用 Material `background`，随浅／深主题保持页面底色一致；视频卡片、封面占位、刷新与打开视频行为不变。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment REQ-022（连接手机二维码外框对比迁移）：二维码外框从固定黑色改为随页面黑白渐变同步的反差文字色，避免深色阶段外框融入背景；二维码图像、亮屏时序及配对流程不变。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment REQ-022（手机端下载弹窗主题面板）：下载手机端弹窗圆形信息面板改用 Material `surface`，使其与 `onSurface` 标题、说明和关闭图标在浅色主题下保持足够对比；外层遮罩继续保持黑色，二维码内容和浏览器能力判断不变。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-002（Phone 全局 Toast 最新事件）：Phone 所有直接 `Toast.makeText` 调用统一迁移到 latest-wins 主线程槽，覆盖首页／RSS／列表事件、备份／备忘录导入导出、支付与更新反馈、复制确认、权限及外链错误。槽按请求 ID 丢弃排队旧调用，并在显示新反馈前取消当前 Toast；数据管理页不再依赖相等字符串 Compose 状态。Phone `:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，MainActivity 启动后最近 2500 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment REQ-022（Phone 支持日志授权按钮语义色）：同意与拒绝按钮从固定浅色值切换到 Material `primaryContainer`／`errorContainer` 及对应前景色，随深浅主题保持一致对比和操作语义；授权文案、点击行为与日志收集范围不变。Phone `:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，MainActivity 启动后最近 2500 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-005（首页频道观察统一未读聚合）：`observeHomeChannels()` 不再把所有频道未读数硬编码为 0，改为复用 Room 聚合后的 `observeChannels()` 流；任何首页观察入口都与频道详情和文章已读状态共用 `rss_items.isRead` 数据源。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-005（已有 RSS 频道返回对象保持未读一致）：添加预览、确认添加和重复添加路径不再将已存在频道的 `unreadCount` 写成 0；通过 DAO 读取同一 `rss_items.isRead` 聚合计数后返回，避免红点在 Room 观察流更新前短暂消失。新增频道仍返回 0。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-006（导航栏图标随阅读底色切换）：阅读页面现在按活动预设背景亮度切换系统导航图标的明暗样式；页面关闭或预设重组替换时恢复进入页面前的系统栏标志，避免浅色背景配浅色图标且不污染下一页面。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment REQ-010（生命周期暂停后保留控制入口）：应用进入后台且设置为不后台播放时，除停止播放器与禁用自动播放外，也持久保留当前视频的暂停 UI 状态；返回页面后即使用户此前已开启声音、菜单已收起，仍能看到“已暂停 · 控制”入口并恢复播放。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 2500 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment REQ-022（B站后台音频页统一手表背景）：后台音频播放页接入 `WatchSurface`，使状态、进度及播放／停止操作共享统一手表页面背景；媒体会话生命周期、后台播放与控制逻辑不变。 `:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，MainActivity 已启动，最近 2500 行 logcat 未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。后台音频页实际路由与圆屏背景视觉仍待页面级验收。
- Comment REQ-022（信息流侧滑删除语义色统一）：RSS 与 B站共用的侧滑删除按钮从固定 `danger_red` 改用 `MaterialTheme.colorScheme.error`，与首页侧滑删除保持一致并随主题语义色变化；其他操作颜色、图标布局、触控与删除回调不变。 `:app:installDebug` 成功，Pixel 10 Pro（Android 17）已启动 MainActivity，应用 PID 最近日志未见 E AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。RSS／B站实际列表的明暗主题视觉仍待页面级验收。
- Comment REQ-022（新手引导共用品牌强调色）：引导页步骤强调色改为引用共享 `BrandOrangeLight` 设计 token，移除与启动页重复定义的十六进制橙色；各步骤图标、强调背景透明度、页面布局和交互不变。 `:app:installDebug` 成功，Pixel 10 Pro（Android 17）已启动 MainActivity，应用 PID 最近日志未见 E AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment REQ-002（小说保留适用的朗读入口）：阅读页不再把小说与所有内部导入文章一并排除出朗读能力；TXT／EPUB 小说显示“大声朗读”，普通内部导入文章仍不显示，网页 RSS 的既有行为不变。朗读队列对小说章节强制使用章节正文，不沿用频道“原文阅读”设置去请求无效网页内容。 `:app:installDebug` 成功，Pixel 10 Pro（Android 17）已启动 MainActivity，应用 PID 最近日志未见 E AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。小说阅读页实际入口仍待真实内容与圆屏验收。
- Comment REQ-001（同书最近打开章节时间严格递增）：打开小说章节时，在 Room 事务内读取同一本书当前最大打开时间戳并分配更大的值；快速连续打开不同章节即使发生在同一毫秒，也不会因时间戳并列而让“继续阅读”恢复到较早章节。正文阅读进度不变，沿用 `readingProgress` 同步记录。 `:app:installDebug` 成功，Pixel 10 Pro（Android 17）已启动 MainActivity，应用 PID 最近日志未见 E AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment REQ-005／REQ-022（朗读悬浮入口动作与状态语义）：悬浮控制入口声明 Button 角色，动作名称准确描述为“打开朗读控制”，并通过状态描述播报读取中、合成中、失败、朗读中或已暂停；不再误报轻点入口会直接暂停／停止，拖动吸边和面板控制不变。 `:app:installDebug` 成功，Pixel 10 Pro（Android 17）已启动 MainActivity，应用 PID 最近日志未见 E AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。读屏实际播报仍待圆屏验收。
- Comment REQ-001（小说阅读进度变更维持同书严格顺序）：章节进度写入也在 Room 事务内基于同书最大章节变更时间分配严格递增时间戳；同毫秒内写入不同章节时不会因时间戳并列选错续读章节。进度百分比、字节位置、正文内容与已有云同步事件保持不变。 `:app:installDebug` 成功，Pixel 10 Pro（Android 17）已启动 MainActivity，应用 PID 最近日志未见 E AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment REQ-005／REQ-022（朗读悬浮入口触控面积）：朗读悬浮入口的可点击与拖动容器扩大至 48dp，圆形按钮视觉维持 40dp 并居中；表冠／触摸控制、圆屏安全吸边范围和展开回调不变。 `:app:installDebug` 成功，Pixel 10 Pro（Android 17）已启动 MainActivity，应用 PID 最近日志未见 E AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。实机拖动和圆屏边界仍待验收。
- Comment REQ-022（添加 RSS 提交按钮触控高度）：添加订阅输入页的圆形提交按钮从 44dp 扩至 48dp，达到手表触控下限；加号文案、Button 语义、禁用／加载状态、点击回调及周边表单布局不变。 `:app:installDebug` 成功，Pixel 10 Pro（Android 17）已启动 MainActivity，应用 PID 最近日志未见 E AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。实际圆屏触控体验仍待验收。
- Comment REQ-022（协作者名单统一页面背景）：移除协作者名单页基于 Material 主题色重复绘制、实际渐变端点相同的页面背景，改用共享 `WatchSurface`；保留调试圆形内容遮罩、发布版全屏黑底、名单内容与表冠滚动。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后日志筛查未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。协作者页面真实路由和圆屏主题视觉仍待设备验收。
- Comment BUG-003／REQ-024（封面网络重试限额、旧请求交接与监听共享）：RSS 与 B站列表／详情封面共用单个进程级默认网络回调，避免每张卡片各自注册系统监听；每个 URL 最多因网络切换自动重试 3 次，耗尽后明确显示失败并保留手动重试。若新网络到达时旧 URL 请求仍在途，重试调用会等待旧失败请求释放去重槽，再对新网络额外发起一次请求，不再把旧网络失败结果当作新网络结果。成功缓存与并发同 URL 去重保持不变。`:app:installDebug` 成功后显式启动 MainActivity，最近 3000 行日志未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。真实 Wi‑Fi／蜂窝切换和长列表体验仍待设备验收。
- Comment BUG-005（首页未读聚合路径复核）：代码复核发现首页流仍从不含未读数的频道实体查询读取，并将 `unreadCount` 硬编码为 0，与先前修复记录不符；现改为订阅 `observeChannelsWithUnreadCounts()`，首页红点与频道详情直接共享 `rss_items.isRead = 0` 的 Room 聚合。`:app:installDebug` 成功，Android 17 模拟器启动 MainActivity，最近 4000 行 logcat 未发现 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过，未运行测试。
- Comment BUG-006（共享阅读背景覆盖系统底部区域）：将窗口背景、导航栏背景及导航图标明暗同步从 RSS `DetailActivity` 专用逻辑下沉到共享 `ReaderBackgroundSurface`，使 RSS／B站文章、备忘录及其他复用阅读背景的页面均随活动预设更新，并在组件离开或预设改变时恢复对应旧窗口状态。`:app:installDebug` 成功，Android 17 模拟器启动 MainActivity，最近 4000 行 logcat 未发现 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过。当前只验证构建与主界面启动，阅读页面底部实机渲染仍待验收，未运行测试。
- Comment REQ-015（蓝牙探测失败保留同步结果）：手机同步探测开始时明确显示“正在查找手表”；探测异常、未发现设备和等待用户选择的状态现在保留在同步任务详情中，并清除过期连接标签，避免错误后页面退回空白或把旧连接误当当前结果。手机端 `./gradlew :app:installDebug` 成功；Pixel 10 Pro（Android 17）启动手机伴侣 MainActivity，最近 4000 行日志未发现 AndroidRuntime／FATAL EXCEPTION／SQLiteException；Phone 与 Watch 工单记录 `git diff --check` 通过；未运行测试。
- Comment REQ-018（设置页蓝牙权限与首页保持一致）：设置页的同步与手表预览入口现在和首页一样检查并请求 Android 12+ 的 `BLUETOOTH_CONNECT` 与 `BLUETOOTH_ADVERTISE`，只有两项都获准才继续操作；此前只请求连接权限，广播权限缺失时可能无法启动手机端蓝牙服务，却仍进入同步／预览。`./gradlew :app:installDebug` 成功，Pixel 10 Pro（Android 17）启动手机伴侣 MainActivity，最近 4000 行日志未发现 AndroidRuntime／FATAL EXCEPTION／SQLiteException；Phone 与 Watch 工单记录 `git diff --check` 通过；未运行测试。
- Comment REQ-028（按当前设备浏览器能力显示下载入口）：手机端下载弹窗只在 PackageManager 能解析 HTTPS 浏览器 Intent 时显示“浏览器打开下载页”；入口仍以二维码为主，无浏览器时不会展示不可用按钮，点击后浏览器暂不可用则提示改用手机扫描二维码。`:app:installDebug` 在 Pixel 10 Pro（Android 17）成功，显式启动 MainActivity 后最近 4000 行日志未见 AndroidRuntime／FATAL EXCEPTION／SQLiteException；`git diff --check` 通过；未运行测试。
- Comment RSCH-001（区分首次引导事件与下载指标）：后台原 `downloads`／`watchDownloads` 字段按兼容性保留，但页面改称手机／手表“首次引导事件报告”，明确说明这不是下载请求、APK 下载完成、安装成功或独立用户数；补充手机大屏筛选、清除数据／重装可能重记、重复上传按日安装设备平台覆盖写入等口径，功能事件排行也明确按次数而非人数。依据两端首次引导触发条件、`telemetry_rollup` 幂等键、`observability.rs` 聚合 SQL 及 `docs/telemetry-metric-definitions.md` 复核。`admin_observability.html`、`telemetry.rs`、指标定义文件及主工单记录的定向 `git diff --check` 通过；全量 Backend diff check 仍报告 `src/observability.rs` 与本次指标无关的既有 SQL 模板尾随空白；未查询生产数据或运行测试。
- Comment RSCH-003（Android 11 手表设备算法重复登记）：手表完成云密钥信封导入后会再次登记设备；此前该调用漏传设备密钥算法，`WatchCloudClient.register()` 默认回退为 P-256，可能把 API 30 实际使用的 RSA-2048 设备误登记为椭圆曲线算法。现在重复登记与首次登记一样读取当前设备实际算法。`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 4000 行日志未发现 AndroidRuntime／FATAL EXCEPTION／SQLiteException；目标文件 `git diff --check` 通过。未运行测试；API 30 云端设备重新批准／真实同步仍待 Android 11 模拟器和有效云账号验收。
- Comment REQ-022（Token 用量供应商图表主题色）：供应商分布环图及图例统一使用当前 Material `primary`、`secondary`、`tertiary`、`error`、`outline`、`onSurfaceVariant` 色板；移除固定红／绿／黄值，浅深主题下均随应用主题切换，图表计算与图例顺序不变。验证：`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录的 `git diff --check` 通过。未运行测试；Token 用量图表的浅深主题实际页面视觉仍待页面级验收。
- Comment RSCH-003（同步前确认网络已验证）：手表与手机的云同步门控及自动网络事件改查 `NET_CAPABILITY_VALIDATED`，不再把仅声明具备互联网能力的 captive portal／未验证网络当作可同步网络；验证通过后网络事件仍会触发有限自动补传。依据 [Android NetworkCapabilities 文档](https://developer.android.com/reference/android/net/NetworkCapabilities#NET_CAPABILITY_VALIDATED)。Watch `:app:installDebug` 和 Phone `:app:installDebug` 均成功；Pixel 10 Pro（Android 17）分别启动 Watch 与 Phone MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；两端相关文件及记录的 `git diff --check` 通过，未运行测试。实际门户登录、网络切换和云账号同步仍待真机／有效账号验收。
- Comment REQ-015／RSCH-003（手表退出账号后的云同步状态）：手表云同步因无本地账号而早退时，现将状态明确更新为“请先登录后再同步”，不再保留上一次的同步成功／进行中状态；同步结果时间和账号数据不变。验证：手表 `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录的 `git diff --check` 通过，未运行测试。退出登录后页面反馈仍待云同步页面级验收。
- Comment REQ-015／RSCH-003（区分手机自动同步暂停原因）：手机云同步现在分别说明自动同步已关闭、未登录、隐私／应用授权未完成，或本机缺少云解密密钥；不再把这些不同状态合并成“关闭或未启用”，同时统一自动同步调度与启动门控使用的阻止原因。验证：Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动手机 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录的 `git diff --check` 通过，未运行测试。真实账号退出／权限撤销等状态仍待云同步页面级验收。
- Comment REQ-015／RSCH-003（WorkManager 自动任务反馈同步暂停原因）：周期性 Phone WorkManager 不再在同步服务前静默检查账号／授权并早退；所有周期任务都交给 `PhoneCloudSyncService` 的统一门控，该门控会记录“已关闭／未登录／需授权／缺少密钥”等准确暂停状态，且仍在任何云端请求前结束。验证：Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动手机 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录的 `git diff --check` 通过，未运行测试。后台 WorkManager 实际周期触发与页面状态展示仍待设备级验收。
- Comment BUG-006／REQ-022（B站 RSS 阅读加载与错误状态背景）：B站 RSS 阅读详情的无内容／错误分支现在也使用共享 `ReaderBackgroundSurface`，让正文、系统导航栏底色及导航图标明暗都跟随活动阅读预设；错误文案、重试状态与正常内容分支不变。验证：手表 `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录的 `git diff --check` 通过，未运行测试。B站 RSS 错误态实际页面及浅色／视频背景下导航区仍待页面级验收。
- Comment REQ-022（B站信息流共用手表页面背景）：B站推荐信息流根容器由单独的 `MaterialTheme.background` 平色层改为与 RSS／其他手表页面一致的 `WatchSurface`，统一调试圆屏遮罩及发布版底色；卡片、侧滑、菜单、刷新和分页行为不变。验证：手表 `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录的 `git diff --check` 通过，未运行测试。B站登录／内容列表实页面及圆屏视觉仍待页面级验收。
- Comment REQ-022（分享二维码页不再覆盖共享底色）：移除 `ShareQrScreen` 内层重复绘制的 `MaterialTheme.background`，恢复外层 `WatchSurface` 的圆屏遮罩、调试渐变与发布版底色；QR 图像、黑白扫描对比与页面关闭手势不变。验证：手表 `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录的 `git diff --check` 通过，未运行测试。扫码分享页的真实设备识别仍待验收。
- Comment RSCH-004（V3 词数与字符数计量分离）：火山 V3 SSE 的 `usage.text_words` 不再错误写入接口／数据库的 `usage_characters`；该字段改按实际发送文本的 Unicode 字符数记录，缓存命中仍记录零上游字符消耗，quota 仍按成功调用次数计算。验证：Backend `cargo check --locked` 成功，修改文件 `git diff --check` 通过；未运行测试。真实上游 usage 对账仍需授权账号。
- Comment REQ-017（独立在线帮助文章接口）：新增可单独维护的 Phone Markdown 帮助内容配置 `WATCHRSS_PHONE_HELP_MARKDOWN` 和 `/functions/v1/phone/help`，不再要求帮助正文伪装成带下载地址的版本公告；Phone 帮助页分别刷新／缓存在线帮助文章与版本说明，缓存按账号环境隔离，在线失败仍保留本地内容。部署配置示例与 Backend README 已同步。验证：Backend `cargo check --locked` 成功（仅现有无关 warning）；Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；涉及文件 `git diff --check` 通过，未运行测试。线上文章须由项目方在部署时填写该变量，当前未发布内容。
- Comment REQ-017（帮助缓存环境快照）：在线帮助请求现在在开始时固定后端环境快照，并用同一快照生成目标缓存键；请求期间切换测试／生产环境时，迟到响应不会写入另一个环境的帮助缓存。验证：Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录 `git diff --check` 通过，未运行测试。
- Comment RSCH-004（切换到方舟 Agent Plan 语音模型接口）：新增 `volcengine_ark` provider，按官方方舟 Agent Plan 专用 `/api/v3/plan/tts/unidirectional` 路径、专属 API Key 和逐行 JSON 音频响应解码调用豆包 Seed TTS 2.0；普通语音 V3 SSE 保留为独立兼容 provider，缓存键区分两类服务；生产 compose 默认值、环境示例和集成说明已统一指向 Agent Plan。未使用凭据或发起计费合成。依据 [Agent Plan 语音模型接入](https://docs.volcengine.com/docs/ark/agent-plan-personal-voice-model?lang=zh) 和 [V3 SSE 接口](https://docs.volcengine.com/docs/doubao-voice/http-chunked-sse-unidirectional-streaming-v3?lang=en)。验证：Backend `cargo check --locked` 成功；相关变更 `git diff --check` 通过；未运行测试。
- Comment REQ-022（通用平台空状态层级与可读性）：通用平台入口的标题现在声明标题层级，说明文字由 `bodySmall` 提升到 `bodyMedium`，让抖音播放页等空内容状态更易浏览、读屏定位；文案、主题颜色、滚动和导航行为不变。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件和记录 `git diff --check` 通过，未运行测试。圆屏页面级视觉与读屏验收仍待设备。
- Comment REQ-022（账号状态页背景迁移）：账号状态页移除 `pureBlack` 背景覆盖，接回共用 `WatchSurface`，使调试样式与首页／个人中心的圆屏渐变背景一致；发布版仍由共享背景组件保持全屏黑底，账号状态、云同步操作及页面布局不变。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录 `git diff --check` 通过，未运行测试。账号状态页圆屏视觉仍待设备验收。
- Comment REQ-022（备忘录列表背景迁移）：备忘录列表移除 `pureBlack` 覆盖并接入共用 `WatchSurface`，调试样式下与其他首页列表共享圆屏渐变背景；发布版仍保持黑底。列表、卡片操作、备忘录阅读预设和原始编辑背景均未改变。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录 `git diff --check` 通过，未运行测试。备忘录列表圆屏视觉仍待设备验收。
- Comment BUG-006／REQ-022（备忘录编辑背景跟随阅读预设）：备忘录原始编辑态现在使用 `ReaderBackgroundSurface`，编辑底色及系统导航栏跟随当前阅读预设，不再出现浅色预设正文与强制黑底不协调，也覆盖编辑内容之外的窗口区域；备忘录列表、保存和光标逻辑不变。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录 `git diff --check` 通过，未运行测试。浅色预设下编辑页圆屏实视觉仍待设备验收。
- Comment REQ-022（备忘录加载／缺失状态背景迁移）：备忘录详情外层容器不再强制 `pureBlack`；加载中与备忘录不存在状态现在使用共用 `WatchSurface`，与备忘录列表的页面层级一致。阅读与编辑状态继续由内层阅读预设背景覆盖，保存、导航和状态文案不变。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录 `git diff --check` 通过，未运行测试。加载／缺失状态的圆屏视觉仍待设备验收。
- Comment REQ-022（手机连接不可用状态背景迁移）：通用连接错误页从强制纯黑改用共享 `WatchSurface`，与首页和其他空／错误状态共用页面底色；错误标题、说明及连接流程不变。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录 `git diff --check` 通过，未运行测试。连接失败页圆屏视觉仍待设备验收。
- Comment REQ-022（蓝牙与声波连接页面背景统一）：蓝牙同步和声波连接状态页移除 `pureBlack` 覆盖并改用共享 `WatchSurface`，与通用连接失败页的圆屏背景规则一致；能力选择、进度／状态文案、重试和关闭回调不变。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；两个目标文件及记录 `git diff --check` 通过，未运行测试。实际蓝牙／声波连接页面圆屏视觉仍待设备验收。
- Comment RSCH-003／REQ-020（手表云备忘录附件账号目录）：云备忘录导出／恢复适配器改用当前账号的 `libraryContext.filesDir`，与手表图片导入、显示和账号隔离共用同一资料库目录；附件不再错误地从应用全局目录读取或写回，备忘录数据格式与云加密对象不变。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录 `git diff --check` 通过，未运行测试。需要真实账号及双设备同步来验收附件往返。
- Comment RSCH-003／REQ-007（云归档识别转义图片说明）：云备忘录资产引用解析现在能识别包含 Markdown 转义方括号的图片说明，并继续从正确的捕获组提取资产路径；这类图片不再被漏出导出归档，也不再跳过恢复时的缺件校验。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录 `git diff --check` 通过，未运行测试。真实云归档往返仍待有效账号验收。
- Comment REQ-007／BUG-001（手表图片预览与编辑完整保护转义说明）：手表 Markdown 图片预览现能识别转义 `]` 并还原图片说明；编辑器的图片引用跨度识别也覆盖整条含转义说明的图片 Markdown，光标映射和删除保护不再把内部路径／链接当作普通正文。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；两个目标文件及记录 `git diff --check` 通过，未运行测试。包含特殊说明的备忘录预览／编辑圆屏验收仍待真机。
- Comment REQ-007／RSCH-003（转义图片说明全链路一致）：进一步修正 Watch 图片资产归档、预览及光标／删除保护中的 Markdown 识别，使说明含 `]` 的图片在显示、编辑和云附件收发各阶段都保留同一资产引用。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；涉及源码及记录 `git diff --check` 通过，未运行测试。特殊说明图片的圆屏行为与真实云附件往返仍待验收。
- Comment REQ-007／RSCH-003（代码示例不再误作云图片附件）：备忘录资产扫描现跳过围栏代码和行内代码，再解析真实正文中的 Markdown／HTML 图片；代码示例里的 `assets/...` 不会触发缺失文件错误、误打包图片或在恢复时充当附件引用。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录 `git diff --check` 通过，未运行测试。代码示例与真实图片混排的云归档仍需有效账号验收。
- Comment REQ-007／BUG-001（图片说明含右方括号时保留图片引用）：修正富文本图片占位的 Markdown 链接匹配，使转义方括号作为图片说明的一部分解析，不再因文件名／说明包含 `]` 而把内部 `watchrss-note-image:` URL 原样存回笔记；普通文本光标和图片稳定路径格式不变。验证：Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录 `git diff --check` 通过，未运行测试。
- Comment REQ-007／RSCH-003（转义图片引用贯通摘要、导入和历史恢复）：Phone 的资产扫描跳过代码示例并识别转义图片说明；归档导入、历史图片路径恢复及纯文本摘要改用同一解析结果，Watch 的笔记标题／摘要也识别转义说明且不会把代码示例当作真实图片。验证：Phone 与 Watch `:app:installDebug` 均成功，分别启动对应 MainActivity，最近 3000 行 logcat 均未见 AndroidRuntime／FATAL／SQLiteException；两个仓库 `git diff --check` 通过，未运行测试。真实跨端归档与含转义说明的圆屏显示仍待验收。
- Comment REQ-007／RSCH-003（历史恢复保留 HTML 代码示例）：补齐 Phone 历史图片路径恢复的 HTML 图片改写过滤；围栏及行内代码里的 `<img src="assets/...">` 不会被错误改成快照别名，真实正文 HTML 图片仍按历史附件映射恢复。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。
- Comment REQ-007／RSCH-003（Watch 资产与摘要共用图片解析器）：Watch 云资产扫描复用笔记摘要所用的转义图片匹配函数，统一捕获 Markdown 图片路径及跳过代码示例的规则，避免独立正则在图片引用格式演进后产生漏导出或误认附件。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Watch 源码及工单记录 `git diff --check` 通过，未运行测试。
- RSCH-004 实现核对（官方 HTTP 接口）：按火山方舟当前文档复核，Agent Plan 语音模型仍提供 `POST /api/v3/plan/tts/unidirectional`，使用专属 `X-Api-Key` 和资源 ID `seed-tts-2.0`，请求体含 `req_params.text/speaker/audio_params`；后端当前分支与此契约一致。[官方接入说明](https://docs.volcengine.com/docs/ark/agent-plan-personal-voice-model?lang=zh)。此核对只确认协议接线，真实授权音色、音质、时延与计费验收仍待账号环境联调。
- RSCH-004 端到端接线复核：Watch 的“应用默认语音”调用后端 `/api/v1/tts/default-model/speech`，按后端响应的十六进制 `audio` 解码；后端生产 Compose 默认选择 `volcengine_ark`，将配置的授权音色和 `seed-tts-2.0` 转接至方舟 Agent Plan，API Key 留在服务端。测试部署样例仍默认普通豆包 V3，因此测试环境冒烟不能代替生产方舟音色验收。配置引用与请求／响应格式已对照源码核实；真实授权请求、声音质量、时延与计费仍待凭据环境验收。
- Comment REQ-022（抖音历史页标题层级）：播放历史主标题增加 Compose heading 语义，辅助技术可直接定位页面标题，不再只依赖字体大小和视觉位置判断层级；主题颜色、列表内容、清空及播放操作保持不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；源码及工单记录 `git diff --check` 通过，未运行测试。历史页读屏顺序仍待页面级验收。
- Comment REQ-022（抖音 Cookie 弹窗层级与触控范围）：手动输入 Cookie 弹窗标题声明 heading 语义，取消和保存按钮均设置至少 48dp 高度；主题字段配色、保存状态与回调不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；源码及工单记录 `git diff --check` 通过，未运行测试。弹窗读屏顺序与圆屏触控仍待页面级验收。
- Comment REQ-022（Phone GlassButton 按钮语义）：Phone 共享玻璃风格按钮统一声明 Button 角色，使主导航页面和文章工具栏的自定义圆角点击控件能被辅助技术识别为按钮；内容、触控区域和点击回调不变。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。读屏实际顺序仍待页面级验收。
- Comment REQ-022（Phone 状态提示关闭语义）：首页、RSS 列表和文章列表中可点击关闭的提示／错误卡片统一声明 Button 角色和“关闭提示”操作名称，使无障碍焦点不再只读到提示正文而缺少可执行动作说明；视觉内容与关闭行为不变。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。实际读屏顺序仍待页面级验收。
- Comment REQ-016／REQ-022（Phone 首次使用步骤标题层级）：首次使用引导页的主步骤标题与登录等待页标题声明 Compose heading 语义，使读屏用户能按标题定位当前引导步骤；视觉层级、引导顺序和认证行为不变。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。TalkBack 实际导航顺序仍待设备验收。
- Comment REQ-017／REQ-022（Phone 帮助中心标题导航）：帮助中心主标题、离线帮助、单个帮助主题、版本说明、在线内容及缓存文章标题声明 Compose heading 语义，使长帮助页可按标题导航；帮助内容、缓存与刷新逻辑不变。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。TalkBack 实际导航顺序仍待页面级验收。
- Comment REQ-017（错误页跳转到对应离线帮助主题）：帮助 footer 支持带主题打开帮助中心；B站扫码登录错误直达媒体与扫码说明，RSS 导入错误直达导入说明，列表页操作错误直达导入说明，通用 footer 仍默认同步排障。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码与本记录 `git diff --check` 通过，未运行测试。
- Comment REQ-018（首页核验完整蓝牙权限组）：权限申请回调不再只检查 `BLUETOOTH_CONNECT`；Android 12+ 只有 `BLUETOOTH_CONNECT` 与 `BLUETOOTH_ADVERTISE` 均已授权才执行挂起的同步操作，否则显示拒绝后的设置入口和本地功能说明，与设置页及实际服务要求一致。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码与工单记录 `git diff --check` 通过，未运行测试。设备上的逐项拒绝交互仍待手动验收。
- Comment REQ-019（未勾协议时的就地提示可听见）：Phone 必要首次引导和扩展引导均在点“同意并继续”但未勾选时说明需要确认两份协议，定位回复选框，不代勾选；提示文案改为准确表达“确认已阅读并同意”，并作为 assertive live region 播报给屏幕阅读器。`./gradlew :app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。两种首次引导的 TalkBack 实际播报顺序仍待页面验收。
- Comment REQ-020（提示状态按账号资料空间隔离）：提示卡片的已读／关闭状态与本地触发计数改由当前账号的 `libraryContext` 持有；账号切换后分别恢复各自状态，旧资料所属账号继续使用原有 SharedPreferences 名称与数据。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。真实双账号切换后的提示状态恢复仍待设备验收。
- Comment REQ-021（关于页备案入口可访问性复核修正）：关于页备案号触控目标扩大到至少 48dp，并为短按查看备案信息、长按复制备案号声明按钮语义和动作标签；备案号与工信部查询跳转保持不变。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码与本记录 `git diff --check` 通过，未运行测试。页面级 TalkBack／短按与长按仍待设备验收。
- Comment REQ-022（设置页标题层级一致）：设置页主页面及各子页的动态 TopAppBar 标题统一暴露 Compose heading 语义，辅助技术可用同一标题导航方式定位当前设置层级；标题文案、Material 样式、返回转场和设置内容不变。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。各子页 TalkBack 导航顺序仍待页面验收。
- Comment REQ-022（RSS 首页添加入口标题层级）：“添加 RSS”卡片主标题字号由 12sp 提升到 14sp，明确区别于下方说明文字并匹配首页其他主要入口的标题层级；主题强调色、卡片尺寸约束和点击路径保持不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；源码与工单记录 `git diff --check` 通过，未运行测试。圆屏下标题截断与浅深主题视觉仍待实际页面验收。
- Comment REQ-016／REQ-015／REQ-022（Phone 引导与同步详情弹窗层级）：四步入门弹窗标题、同步任务详情弹窗标题及阶段记录标题声明 heading 语义，使读屏用户可直达当前引导步骤与同步阶段；阶段状态、任务操作和进度文案不变。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。TalkBack 页面级导航仍待设备验收。
- Comment REQ-015（Phone 同步阶段状态播报）：同步任务详情中的“当前阶段”标记为 polite live region，使连接／传输／校验等阶段切换可被辅助技术播报，同时不把变化频繁的传输速度加入动态播报区域。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。TalkBack 动态播报仍待同步流程实机验收。
- Comment REQ-022（Phone 首页入口标题层级）：首页三个主要入口卡片“开始使用腕上RSS”“同步RSS和小说到手表”“加群学习更多玩法”声明 Compose heading 语义，方便读屏用户快速定位不同任务区域；卡片内容、视觉样式和入口行为不变。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。TalkBack 页面级导航仍待设备验收。
- Comment REQ-015（Phone 同步进度跨入口播报）：同步阶段文字在首页同步卡片与同步页状态卡片统一声明 polite live region，使阶段变化在任务详情弹窗外也能被读屏用户及时获知；忙碌状态、进度动画、阶段和速度显示逻辑不变。Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。真实双端同步中的 TalkBack 播报仍待设备验收。
- Comment REQ-024（RSS 可见卡片优先加载封面）：RSS LazyColumn 将当前可见 item keys 传给缩略图；预组成但尚未进入可见区域的卡片不再启动新图片请求，进入可见区域后再读取／加载，已缓存图片仍可立即显示，失败项仍保留手动重试。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Watch 源码及工单记录 `git diff --check` 通过，未运行测试。快速滚动图片时序、真实图片源的网络请求竞争仍待圆屏设备验收。
- Comment REQ-024（B站推荐与收藏列表可见封面优先）：B站推荐流和收藏／历史列表将 LazyColumn 的可见 item keys 传给共用卡片，封面复用 RSS 图片缓存／网络重试逻辑，仅当前可见卡片启动新请求，滚动预组成项目等待显示后加载。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Watch 源码及工单记录 `git diff --check` 通过，未运行测试。真实账号图片源上的快速滚动延迟及圆屏视觉仍待设备验收。
- Comment REQ-024（B站搜索结果可见封面优先）：分页搜索结果按 LazyColumn 当前可见索引控制共用视频卡片的封面加载，预组成的非可见搜索结果不会抢先启动网络请求；显示时仍使用统一缓存、网络恢复重试和失败重试入口。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Watch 源码及工单记录 `git diff --check` 通过，未运行测试。分页结果快速滚动及真实图片源表现仍待设备验收。
- Comment BUG-003（离屏重建后的封面网络恢复重试）：RSS 与 B站封面在当前进程已经经历网络切换后，即使卡片离屏重建并把新网络版本当成初始版本，加入旧请求且失败时仍会触发一次新网络请求；手动重试继续可用。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；涉及源码及记录 `git diff --check` 通过，未运行测试。真实 Wi‑Fi／蜂窝切换时的请求恢复仍待设备验收。
- Comment REQ-022（B站搜索结果页标题层级）：B站搜索结果页主标题声明 Compose heading 语义，与搜索起始页保持一致，读屏用户可按标题定位结果页；搜索行为、标题文本和列表结构不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Watch 源码及工单记录 `git diff --check` 通过，未运行测试。读屏顺序仍待页面级验收。
- Comment REQ-007（加载含转义方括号的图片说明）：编辑器加载 Markdown 图片时也接受反斜杠转义的方括号，并将说明恢复成原始文字；说明含 `]` 的 `assets/` 图片因此仍转换为紧凑图片占位，内部路径不会挤进编辑正文。普通 Markdown 图片语法和稳定资产 URL 不变。验证：Phone `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 Phone MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及记录 `git diff --check` 通过，未运行测试。
- Comment REQ-028（B站播放器错误页遵循浏览器能力）：B站播放器错误页只在系统确实存在可处理 HTTPS 的浏览器时显示“浏览器打开”，点击后改为启动外部浏览器；原先误导性地打开应用内 WebView。启动失败会给出明确提示。验证：Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件与工单记录 `git diff --check` 通过，未运行测试。无浏览器设备上的隐藏状态和真实外部浏览器跳转仍待设备验收。
- Comment BUG-001／REQ-007（重复文本的编辑锚点）：备忘录阅读页将格式化文本位置回映射到源码时，回退匹配现在会在重复片段中选取最接近当前相对位置的匹配，并计入锚点裁剪掉的前导空白；重复段落不再一律把“编辑此处”光标落到第一次出现的位置。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；源码及工单记录 `git diff --check` 通过，未运行测试。复杂 HTML 富文本和重复段落的圆屏编辑定位仍待实机验收。
- Comment REQ-022／REQ-007（备忘录图片预览按钮语义）：可点开的图片块现显式声明 Button 角色并保留“查看图片”操作标签，读屏用户能辨识图片既是内容也是预览入口；图片说明、占位状态和预览回调不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及工单记录 `git diff --check` 通过，未运行测试。TalkBack 实际播报及圆屏触控范围仍待设备验收。
- Comment REQ-005／REQ-022（TTS 悬浮控制状态语义）：朗读控制弹窗标题声明 heading，当前读取／合成／播放／暂停／失败状态设为 polite live region；读屏用户可定位控制面板，并在状态切换时获知进展。停止、重试、暂停及收起操作不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及工单记录 `git diff --check` 通过，未运行测试。TalkBack 动态状态播报仍待设备验收。
- Comment REQ-020／REQ-024（账号切换时清理封面状态）：RSS 缩略图的 Compose Bitmap、失败状态、重试次数、网络重试预算及加载协程现以账号资料库缓存目录为键；账号空间改变时不会沿用前一账号的内存封面，旧请求效果随 key 变化取消。内存缓存读取也使用该卡片对应的账号 Context，而非全局当前账号。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件与工单记录 `git diff --check` 通过，未运行测试。双账号相同 URL 切换与快速滚动仍待设备验收。
- Comment REQ-022（设置页标题层级）：设置与高级设置共用标题现在声明 heading 语义，使读屏用户进入两级高频设置页后可以直接按标题导航；字号、主题样式和页面切换逻辑不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件及工单记录 `git diff --check` 通过，未运行测试。TalkBack 的标题导航顺序仍待设备验收。
- Comment REQ-027／REQ-022（收藏夹选择弹窗标题导航）：收藏夹选择弹窗标题声明 heading 语义，读屏用户可以从当前收藏夹列表直接跳转到弹窗标题；每个收藏夹的分页缓存、独立滚动位置及选择行为不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标文件与工单记录 `git diff --check` 通过，未运行测试。TalkBack 弹窗导航及真实收藏夹账户仍待设备验收。
- Comment REQ-027（收藏夹选择跨进程恢复）：当前收藏夹 ID 现在写入 `SavedStateHandle`，页面或进程重建后重新获取收藏夹清单时仍优先恢复用户上次选项；若收藏夹已删除，则安全回退到第一项并同步修正已保存 ID。原有各收藏夹分页快照与 SaveableStateProvider 滚动位置继续按文件夹 ID 隔离。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；源码与工单记录 `git diff --check` 通过，未运行测试。进程重建后的真实 B站账户与滚动恢复仍待设备验收。
- Comment RSCH-004（区分后端回退与部署默认）：更正旧记录中的“默认 MiniMax”表述：生产 Compose 明确默认方舟 Agent Plan，测试 Compose 默认普通 V3 SSE，通用 `Config::from_env` 的 MiniMax 仅是 provider 环境变量缺省时的通用回退；三者配置分别以 `deploy/production/docker-compose.yml`、`deploy/testing/docker-compose.yml` 与 `src/config.rs` 为准。按当前[方舟 Agent Plan 官方接入文档](https://docs.volcengine.com/docs/ark/agent-plan-personal-voice-model?lang=zh)复核，HTTP 路径、专属 `X-Api-Key`、`X-Api-Resource-Id: seed-tts-2.0` 及请求字段与后端一致。无需凭据的协议核对完成；真实授权音色合成仍待密钥与 speaker 配置。
- Comment REQ-020／REQ-024（图片宽高比缓存账号隔离）：RSS 与 B站详情中图片宽高比的内存缓存读写现通过调用方资料库 Context 计算账号键；文章图片比例状态与 B站 RSS 封面状态也把资料库缓存目录纳入 Compose key，切换账号后旧账号的同 URL 宽高比不会再影响新页面布局。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；涉及源码和工单记录 `git diff --check` 通过，未运行测试。双账号相同 URL 的实际切换效果仍待设备验收。
- Comment REQ-020（移除图片缓存全局活动账号读取）：删除 RSS 图片缓存中不带账号 Context、依赖单一 `activeCacheRoot` 的 Bitmap／宽高比接口及其全局根配置；所有内存缓存读写现在都必须提供调用方 Context，避免跨账号页面或后台加载误用当前全局账号的缓存索引。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；相关源码与工单记录 `git diff --check` 通过，未运行测试。多账号并发图片加载仍待设备验收。
- Comment REQ-020／REQ-024（限制账号专属 Coil Loader 常驻数量）：账号隔离的内联图片 Loader 改为最多保留最近使用的两个账号实例；切换到第三个账号时关闭最久未使用 Loader，避免每个账号永久持有一套内存缓存、HTTP 客户端与磁盘缓存对象。各账号磁盘缓存仍分别保存在原资料库目录中。`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。双账号缓存隔离和三账号切换释放仍待设备验收。
- Comment RSCH-007（二维码终态释放亮屏）：B站二维码页现在只在二维码存在且轮询状态为等待扫码或已扫码待手机确认时保持亮屏；二维码过期、轮询失败、登录成功或页面退出都会恢复进入页面前的亮屏标记，避免失效二维码持续阻止屏幕熄灭。`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。有效二维码等待时的真实亮屏与终态行为仍待手表设备验收。
- Comment RSCH-007（终态保持页面原有亮屏策略）：修正二维码轮询状态切换时重新捕获 `keepScreenOn` 的行为；现在固定记录进入页面时的原始标记，有效待扫码／待确认状态才叠加亮屏，过期、错误或成功后恢复该基线，离页时仍统一还原。这样不会在页面仍打开时因二维码终态提前清除进入前已有的常亮标记。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Watch 源码与工单记录 `git diff --check` 通过，未运行测试。非默认常亮基线的页面内状态仍待 Activity 级验证。
- Comment REQ-020／REQ-024（限定账号 Coil Bitmap 内存占用）：每个账号专属 Coil Loader 的强引用内存缓存上限设为应用可用堆的 10%；结合最多两个常驻账号 Loader，避免账号切换让 Bitmap 缓存预算叠加到 Coil 默认的多份全额比例，同时保留各账号独立磁盘缓存和当前可见封面优先加载。`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。真实低内存手表上跨账号快速切换与缓存命中表现仍待设备验收。
- Comment REQ-022（账号状态页标题层级）：账号状态页主标题“账号”增加 Compose heading 语义，辅助技术可按标题直接导航该高频页面；圆屏布局、账号信息及云同步控件不变。`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。账号状态页实际读屏标题导航仍待页面级验收。
- Comment REQ-022（RSS 阅读器文章标题层级）：文章标题容器增加 Compose heading 语义，读屏可将文章主标题作为页面内容标题导航；原标题排版、朗读高亮、长按朗读和正文触控手势保持不变。`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。文章页实际 TalkBack 标题导航仍待页面级验收。
- Comment REQ-022（B站 RSS 详情标题层级）：B站 RSS 文章详情的标题列表项容器声明 Compose heading 语义，读屏可按主标题导航；不改共用标题排版组件、作者／简介层级或视频播放入口。`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。B站文章页 TalkBack 标题导航仍待登录态页面验收。
- Comment REQ-022（B站视频详情主标题层级）：视频元信息卡中的主标题声明 Compose heading 语义，使读屏用户进入详情页后可直接按标题定位；字号、换行、作者统计信息及播放行为不变。`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。详情页实际 TalkBack 标题导航仍待 B站登录页面级验收。
- Comment REQ-022（B站视频详情分区标题层级）：详情页“分P”“简介”“评论”分区名通过共用 `BiliSectionTitle` 声明 heading 语义，读屏可在长详情内容中按区块导航；右侧分P数量仍作为独立补充信息播报，布局和交互不变。`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。实际视频详情页分区导航仍待 B站登录态验收。
- Comment REQ-022（RSS 正文标题层级）：RSS 正文解析为 `TITLE`／`SUBTITLE` 的 Markdown／HTML 分区标题现在暴露 Compose heading 语义，读屏可在长文章正文按内容标题导航；正文文本、阅读字体、自动滚动和 TTS 高亮不变。`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。真实长文滚动中的标题导航顺序仍待设备验收。
- Comment REQ-004／REQ-022（自动滚动播放状态播报）：控制页“正在自动滚动／自动滚动已暂停”文本设为 polite live region，播放／暂停按钮同时公开当前状态说明；按钮动作名、图标、颜色及正文触控开关不变。`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；`git diff --check` 通过，未运行测试。TalkBack 对动态播放状态的实际播报仍待控制页设备验收。
- Comment REQ-004（自动滚动控制四态）：阅读器控制页分别显示“自动滚动尚未开始、正在自动滚动、自动滚动已暂停、已到文章底部”；滚动到底时停止并记录底部状态，只有文章重新可向前滚动后清除该状态，底部状态下禁用启动按钮。控制页启动／暂停状态经 Activity 结果返回，并保留已启动会话标记。`:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；改动文件 `git diff --check` 通过，未运行测试。底部触控、手动回滚后状态和 TalkBack 播报仍待阅读页实机验收。
- Comment REQ-022（自动滚动控制页标题层级）：控制页“自动滚动控制”标题增加 Compose heading 语义，读屏用户可按页面标题直接定位控制面板；字号、主题、四态状态播报及播放操作不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；相关源码及工单记录 `git diff --check` 通过，未运行测试。标题导航实际播报仍待 TalkBack 与 Wear OS 实机验收。
- Comment REQ-023（扫码登录状态动态播报）：二维码页轮询状态“等待扫码／已扫码请确认／过期／登录失败”现在作为 polite live region 更新，读屏用户无需反复移动焦点即可收到关键登录状态变化；刷新按钮和扫码帮助流程不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；源码和工单记录 `git diff --check` 通过，未运行测试。TalkBack 对异步轮询状态的真实播报仍待 B站登录态与 Wear OS 实机验收。
- Comment REQ-022（RSS 频道首页标题层级）：RSS 频道首页“腕上RSS”主标题增加 Compose heading 语义，辅助技术可按页面层级快速定位首页标题；主题颜色、字号、频道列表及设置入口不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；源码及工单记录 `git diff --check` 通过，未运行测试。RSS 首页实际标题导航仍待 TalkBack／Wear OS 页面验收。
- Comment REQ-022（RSS／B站频道设置标题层级一致）：RSS 频道设置与 B站设置页的“频道设置／设置”主标题统一声明 Compose heading 语义，辅助技术可按同一层级导航两类频道设置页；标题样式、正文选项和操作不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；两个源码文件及工单记录 `git diff --check` 通过，未运行测试。标题导航顺序仍待 TalkBack 页面级验收。
- Comment REQ-024（信息流封面占位过渡）：RSS 与 B站共用缩略图组件在 160ms 内淡入新加载的封面，避免成功缓存返回时图片突兀替换加载／失败占位；可见卡片优先、账号隔离缓存及失败重试逻辑不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；源码和工单记录 `git diff --check` 通过，未运行测试。滚动压力下加载延迟与圆屏转场仍待列表页面实机测量。
- Comment REQ-025（单条循环仅限 B站）：共用播放器增加默认关闭的循环控件能力参数，只有 B站播放器入口启用“单条循环（本次播放）”；抖音与 RSS 继续使用同一播放控制面板，但不再误显示 B站专属循环选项，ExoPlayer 循环仍限定当前播放器会话。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；源码和工单记录 `git diff --check` 通过，未运行测试。需有登录态的 B站、抖音与 RSS 播放页实机确认各自控制项。
- Comment REQ-026（并发搜索历史更新顺序）：B站搜索历史的初次读取、新增、单条删除和整批清空共用 ViewModel Mutex，在锁内完成 DataStore 变更及刷新 UI 快照；较早异步读取不再晚于删除／清空结果写回，从而避免已删除关键词短暂复现。原单条长按确认、点击搜索与整批清空入口不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；源码和工单记录 `git diff --check` 通过，未运行测试。快速连续新增／删除／清空的页面状态仍待 B站登录态设备验收。
- Comment REQ-015（清空手表资料库失败状态）：Phone 清空手表资料库若失败，不再仅发 Toast 并遗留“正在清空…”状态；现在清除进行中进度，把失败原因保留到首页同步卡与任务详情，并提供支持诊断提示。成功时同步清除旧错误。Phone `./gradlew installDebug` 成功，Pixel 10 Pro（Android 17）启动 `MainActivity`；最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码与工单记录 `git diff --check` 通过，未运行测试。真实 RFCOMM 清空失败／成功状态仍待配对手表验收。
- Comment REQ-016／REQ-020（首次引导完成标记按账号隔离）：Phone 四步新手引导的已看标记改从当前账号的 `libraryContext` 读取和写入；旧资料归属账号继续使用原偏好名并保留其完成状态，其他账号使用各自隔离的偏好，不会因前一账号跳过而失去首次引导。Phone `./gradlew installDebug` 成功，Pixel 10 Pro（Android 17）启动 `MainActivity`；最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；Phone 源码及工单记录 `git diff --check` 通过，未运行测试。多账号切换后的首登提示仍待授权账号设备验收。
- Comment REQ-022（B站后台音频标题导航语义）：播放标题随当前曲目动态变化，现声明为 Compose heading，辅助技术可直接跳转到页面主标题并与其他已迁移页面保持一致；排版与播放操作不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标源码与记录 `git diff --check` 通过，未运行测试。动态曲目标题 TalkBack 导航仍待页面验收。
- Comment REQ-022（首页排序页面标题导航语义）：首页排序页“首页排序”标题声明 Compose heading，辅助技术可直接定位排序页面主标题；拖动、箭头调整、保存和取消行为不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标源码与记录 `git diff --check` 通过，未运行测试。排序页 TalkBack 标题导航仍待页面验收。
- Comment REQ-022／REQ-011（播放控制面板标题导航语义）：共用播放控制对话框标题“播放控制”声明 Compose heading，读屏用户可直接定位进度、播放／暂停、音量及展开画面设置的操作区；对话框布局及控制行为不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标源码与记录 `git diff --check` 通过，未运行测试。圆屏上标题焦点次序仍待页面验收。
- Comment REQ-022／RSCH-003（手表云同步控制区标题导航语义）：会员云同步标题声明 Compose heading，读屏可将账号要求、网络策略、自动同步开关和状态信息作为独立设置区域定位；按钮与同步状态逻辑不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标源码及记录 `git diff --check` 通过，未运行测试。账号页读屏导航顺序仍待页面验收。
- Comment REQ-022／REQ-006（阅读器预设页标题导航语义）：阅读器预设页“阅读器预设”主标题声明 Compose heading，读屏用户可从标题直接进入字号滑块、主题选择和预设管理区域；字号／主题操作及版式不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标源码与记录 `git diff --check` 通过，未运行测试。字号滑块与主题选择页面的 TalkBack 焦点次序仍待验收。
- Comment REQ-022／REQ-016（OOBE 步骤标题导航语义）：首次引导各步骤的可见标题及“自定义”设置页标题均声明 Compose heading，读屏用户可按标题快速定位当前步骤或进入的设置区域；步骤内容、字号和引导顺序不变。Watch `:app:installDebug` 再次成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标源码与记录 `git diff --check` 通过，未运行测试。首次引导页面的 TalkBack 标题焦点顺序仍待设备验收。
- Comment REQ-022（Token 用量页面标题导航语义）：统计页“Token 消耗”标题声明 Compose heading，使读屏用户可从页面标题直接导航到调用量、供应商和每日统计区域；主题色图表与统计内容不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标源码与记录 `git diff --check` 通过，未运行测试。Token 统计页 TalkBack 导航仍待页面验收。
- Comment REQ-022／REQ-018（网络状态页标题导航语义）：完整状态页和遮罩态中的“互联网”标题均声明 Compose heading，读屏用户可快速定位当前网络状态与后续操作；引导文案、重试入口及状态更新不变。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标源码与记录 `git diff --check` 通过，未运行测试。TalkBack 对网络断开／蓝牙状态页的焦点顺序仍待页面验收。
- Comment REQ-018（手表蓝牙权限拒绝路径）：蓝牙同步拒权后明确说明附近设备权限用于发现并连接手机，提供重新检查／等待和跳转应用权限设置两个路径；未授权时不启动蓝牙监听。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标源码及记录 `git diff --check` 通过，未运行测试。拒权、永久拒权及从系统设置返回后的权限流程仍待设备交互验收。
- Comment REQ-018（手表声波权限拒绝路径）：纯声波与声波引导 Wi‑Fi 拒权时分别说明麦克风、位置和附近 Wi‑Fi 权限用途，提供重新开始与打开应用权限设置的入口；未授权时不会进入监听。Watch `:app:installDebug` 成功（安装阶段曾有一次模拟器 shell 超时告警，最终仍报告安装成功），Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标源码与记录 `git diff --check` 通过，未运行测试。拒权、永久拒权及从系统设置返回后的实际权限交互仍待设备验收。
- Comment REQ-018／REQ-006（位置权限最小化与日出日落启用）：阅读主题说明位置只用于本机计算日出日落，并改为仅请求足够的粗略位置权限；首次启用日出日落时，授权成功取得坐标后会实际保存该模式，拒绝时保留固定时间且提供系统权限设置入口。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标源码与记录 `git diff --check` 通过，未运行测试。粗略位置授权、拒权回退及日出日落切换实际效果仍待 Wear OS 设备验收。
- Comment REQ-018（设置返回后的权限状态刷新）：从系统应用权限设置返回后，若粗略位置权限已恢复，点击“更新当前位置”会立即清除旧的拒权状态，再读取位置；不再同时保留过期的“打开位置权限设置”提示。Watch `:app:installDebug` 成功，Pixel 10 Pro（Android 17）启动 MainActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException；目标源码和记录 `git diff --check` 通过，未运行测试。设置返回授权状态仍待 Wear OS 实际权限交互验收。
- Comment REQ-018（手表 RFCOMM 权限最小化）：手表仅运行已配对设备的 RFCOMM 服务端，不执行蓝牙扫描；Android 12 及以上的运行时权限申请移除多余的 `BLUETOOTH_SCAN`，保留 `BLUETOOTH_CONNECT`。旧版 IP／BLE 服务所需的 Manifest 声明不变。`assembleDebug` 成功，调试 APK 已生成；`installDebug` 因无连接设备而未能安装／启动；目标源码与记录 `git diff --check` 通过。设备联调仍待模拟器或手表连接后验收。
- Comment REQ-001（真机十页小说调试导入）：新增仅调试构建可由 adb Intent 调用的小说导入入口，幂等写入一部原创测试小说的 10 个章节并自动打开目录；release manifest 不导出此入口。OWW251 已卸载旧签名版并安装当前 `1.3.2-12`（versionCode 39）调试 APK；Intent 成功导入，直接查询设备 Room 数据库确认该书目有 10 章，当前页面为 FeedActivity，最近 3000 行 logcat 未见 AndroidRuntime／FATAL／SQLiteException。`:app:assembleDebug` 成功，源码及记录 `git diff --check` 通过，未运行测试。旧版卸载清除了原本地数据。
- Comment REQ-001（小说目录无障碍语义）：FeedActivity 仅将 EPUB／TXT 小说频道标记为小说模式，FeedScreen 的列表无障碍描述按模式分支为“小说目录”或原有“RSS内容列表”；小说判断失败或普通 RSS 频道均保留 RSS 描述。`:app:assembleDebug` 待本次设备连接状态确认；源码及工单记录 `git diff --check` 通过，未运行测试。
- Comment REQ-001（小说目录 Skeleton Shimmer 无障碍状态）：骨架屏显示期间，小说模式的列表状态描述为“小说目录加载中”，加载完成后按章节数播报；RSS 模式仍保持原有状态描述逻辑。编译验证待执行；源码与记录 `git diff --check` 通过，未运行测试。
- Comment REQ-001（小说阅读页隐藏资讯总结）：DetailScreen 按章节 URL 判断小说模式，在小说章节中隐藏 AI 总结入口并阻止自动总结；RSS 资讯继续使用原总结入口与自动总结设置。源码与记录 `git diff --check` 通过，`:app:assembleDebug` 待验证，未运行测试。
- Comment REQ-001（小说章节不显示文章收藏）：DetailScreen 在小说模式隐藏当前章节的文章级收藏按钮，RSS 文章收藏保留；阅读方式等章节阅读操作继续显示。`:app:assembleDebug` 待验证，源码与记录 `git diff --check` 通过，未运行测试。
- Comment REQ-001（小说详情 Shimmer 骨架）：小说目录点章节时将模式标记传入 DetailActivity，并沿章节切换／恢复导航传递；小说正文加载骨架只显示标题与连续段落占位，隐藏 RSS 资讯用的大图和操作芯片，避免首帧误闪资讯骨架。RSS 详情骨架保持原样。`:app:assembleDebug` 待验证，源码与工单记录 `git diff --check` 通过，未运行测试。
- Comment REQ-004／REQ-001（点击翻页仅限小说）：详情页“阅读方式”菜单在 RSS 模式保留字号／主题设置但隐藏纵向／点击翻页切换项；RSS 的翻页遮罩被模式门控，始终竖向滚动。小说模式仍可切换纵向滚动和点击翻页，阅读菜单状态不改变 RSS 行为。`:app:assembleDebug` 待验证，源码与工单记录 `git diff --check` 通过，未运行测试。
- Comment REQ-004／REQ-001（小说阅读方式悬浮菜单）：小说阅读页提供三横线悬浮球，滚动／翻页时隐藏，停止后显示并过渡为低对比哑光表面；点击从球处圆形展开阅读方式面板，选择后收缩并切换。RSS 不显示该入口，正文底部预留空间。`assembleDebug` 与 `git diff --check` 通过；设备未连接，圆屏动效与交互待真机验收。
- Comment REQ-004（右滑收起悬浮菜单）：阅读方式面板展开时，详情页优先消费右滑并关闭面板，让圆形收拢动画执行，避免整个 Activity 跟随右滑退出；关闭后保留原返回手势。
- Comment REQ-004（悬浮球空闲透明度）：停止滚动后先显示不透明悬浮球，空闲后渐变至低透明度；触控范围保持不变，滚动时继续隐藏。
- Comment REQ-004（悬浮球避让正文）：真机发现悬浮球遮挡正文，缩小悬浮球并为小说正文视口预留底部空间，翻页截图与位移同步使用调整后的视口高度。
- Comment REQ-004（悬浮球覆盖正文）：按用户要求撤销正文底部预留空间，阅读视口恢复全屏，悬浮球保持在正文之上并优先响应自身点击；翻页截图与滚动使用完整视口高度。
- Comment REQ-022（使用前说明居中与纯黑背景）：首次使用说明改为纯黑全屏布局，标题与“我已了解”按钮居中，说明文字保留独立滚动区域及确认流程。
- Comment REQ-005（小说朗读入口集成）：小说详情的“大声朗读”移入阅读方式悬浮菜单，选择后收起面板并沿用原朗读启动逻辑，播放前暂停自动滚动；RSS 详情朗读入口保持原位。
- Comment REQ-004（小说翻页正文行对齐）：真机发现点击翻页后页首截断文字；翻页定位到段落中间时回退到当前文字行的顶部，并保留最后一页到底能力，避免阅读时丢失半行。
- Comment REQ-005／REQ-004（小说正文索引与朗读入口一致）：小说朗读按钮移入菜单后，同步修正正文列表起始索引计算，避免把已移除的操作栏计入，从而影响翻页行定位与阅读进度。
- Comment REQ-019（协议链接样式）：引导页协议与隐私链接保留强调色和独立点击，移除下划线。
- Comment REQ-022（引导页应用图标）：引导页移除手绘星环，改用应用实际启动图标。
- Comment REQ-022（引导页图标圆形遮罩）：应用启动图标在引导页按圆形裁切，去掉绘制位图时露出的方形底。
- Comment REQ-022（引导设置标题不再竖排）：字体大小与音量上限的设置项将标题和加减控件分为两行，确保标题有完整宽度并保留按钮触控区域。
- Comment REQ-022（引导设置恢复单行）：按用户要求将字体大小和静音开播改回单行，缩小这两项加减触控区域及数值宽度，为标题留出空间；其它设置按钮尺寸不变。
- Comment REQ-022（音量控制文案使用表冠）：将设置页、引导页、音量防干扰提示及相关说明中的用户可见“滚轮”统一改为“表冠”，准确对应手表实体操作部件。
- Comment REQ-016（联网时跳过联网说明页）：完成首次设置后，若 Wi-Fi、蜂窝或已验证的蓝牙网络可访问互联网，直接进入应用；仅在无可用互联网或仍在检测时显示网络页，避免向已联网用户展示“已联网”提示。
- Comment REQ-016（移除手机端编辑同步横幅）：从备忘录、收藏列表和 B 站设置页移除重复出现的“下载手机端 App 编辑和同步内容”宣传横幅，让用户直接看到当前页面内容与操作。
- Comment REQ-016（首页频道状态文案按内容类型区分）：B 站和抖音频道卡片不再显示无意义的更新时间；普通 RSS 首次抓取前显示“等待首次获取内容”，抓取后显示易读的相对更新时间。
- Comment BUG-003（RSS 添加错误不暴露英文异常）：预览和提交失败时将常见网络、超时、TLS 与解析异常映射为中文提示，未知异常使用中文兜底，不再直接显示第三方英文异常。
- Comment REQ-022（圆形页面背景去除方形色块）：调试版 WatchSurface 的圆形渐变外部改为纯黑底，消除圆形内容背后的方形深绿色背景。
- Comment REQ-019（协议勾选动画）：引导页勾选框在选中时逐笔绘制勾形，保留复选框语义和独立协议链接。
