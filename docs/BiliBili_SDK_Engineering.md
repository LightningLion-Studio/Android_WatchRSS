# Todo

- [x] 完成基础配置/模型/序列化与签名工具
- [x] 完成 HTTP 封装与 Cookie 解析/注入
- [x] 完成账号存储（加密 SharedPreferences）
- [x] 完成 TV/Web 二维码登录与账户写入
- [x] 完成 buvid/WBI key/bili_ticket 获取与缓存
- [ ] 实现内容接口：`BiliFeed`、`BiliVideo`、`BiliPlay`
- [ ] 实现动作与同步：`BiliAction`、`BiliHistory`、`BiliFavorite`
- [ ] 补齐 gRPC metadata 生成与 Play/Dyn 调用
- [ ] 完善 `access_key` 刷新策略与异常处理
- [ ] 增加 MockWebServer 回归测试覆盖登录/推荐/播放/动作

# BiliBili SDK Engineering (野生 API)

本文档基于 `docs/BiliBili_API/docs/` 中的野生 API 文档整理，面向 可穿戴 Watch RSS 项目的 `:sdk:bili` 设计与落地。由于接口非官方，策略以“可维护、可替换、可降级”为首要目标。SDK 以 App 端接口优先，Web 端作为兜底与降级通道。

## 配置

在 `local.properties` 中提供密钥配置（不提交到仓库）：

```
bili.appKey=YOUR_APP_KEY
bili.appSec=YOUR_APP_SEC
bili.tvAppKey=YOUR_TV_APP_KEY
bili.tvAppSec=YOUR_TV_APP_SEC
```

未配置时，SDK 将使用空值，相关能力不可用。

## 1. 适用范围与风险

- 数据来源为野生 API 收集文档，存在变更与风控风险；接口可能随时失效或收敛。
- `docs/BiliBili_API/README.md` 说明该文档为非官方、非商用授权（CC BY-NC 4.0），SDK 使用需评估合规与授权风险。
- 风控敏感字段（如 `buvid3`、`bili_ticket`、设备指纹等）必须统一管理，避免异常触发封禁。

## 2. 功能范围（与产品文档对齐）

- 登录：二维码为主，密码为备选。
- 列表：推荐流/动态流。
- 详情：视频基本信息、UP 主信息、统计字段。
- 播放：获取播放地址（DASH/MP4）。
- 动作：点赞/投币/收藏、稍后再看同步、历史记录读取。
- 收藏夹：列表与添加/移除。

## 3. SDK 模块设计

- `BiliClient`：统一请求封装、Header/Cookie 处理、签名与重试策略。
- `BiliAuth`：二维码登录/密码登录、Cookie 持久化、刷新流程。
- `BiliIdentity`：buvid、bili_ticket、设备指纹生成与缓存。
- `BiliFeed`：推荐列表、动态列表。
- `BiliVideo`：详情、分 P 信息、UP 主信息。
- `BiliPlay`：播放地址解析与清晰度选择。
- `BiliAction`：点赞/投币/收藏、稍后再看。
- `BiliHistory`：历史记录读取与分页游标。
- `BiliFavorite`：收藏夹列表与资源管理。

## 4. 鉴权与签名

### 4.1 Cookie 体系

必须持久化的 Cookie：

- `SESSDATA`：登录态核心。
- `bili_jct`：CSRF Token。
- `DedeUserID` / `DedeUserID__ckMd5` / `sid`：部分接口校验依赖。
- `buvid3` / `buvid4` / `b_nut`：风控字段，缺失可能导致动作类接口失败。

Cookie 获取与维护：

- buvid3/buvid4：`https://api.bilibili.com/x/frontend/finger/spi` 或 `https://api.bilibili.com/x/web-frontend/getbuvid`。(`docs/misc/buvid3_4.md`)
- 某些动作接口需 `buvid3` 正常存在（如点赞/投币）。(`docs/video/action.md`)

### 4.2 AppKey/AppSec 选择与轮换（App 端优先）

来源：`bilibili-API-collect` APPKey 列表（线上与本地文档一致）。

推荐组合（按用途）：

- 主用（内容获取通用）：`appkey=1d8b6e7d45233436` / `appsec=560c52ccd288fed045859ed18bffd973`，`platform=android`，`mobi_app=android`（粉版）。
- 备选（仅用户信息类接口）：`appkey=783bbb7264451d82` / `appsec=2653583c8873dea268ab9386918b1d65`（粉版 7.X 及更新版本）。
- TV 扫码登录（获取 `access_key`）：`appkey=4409e2ce8ffd12b8` / `appsec=59b43e04ad6965f34319062b478f83dd`（云视听小电视/TV 端）。

轮换策略：

- 请求返回 `code=-3`（密钥错误）或 `code=-663`（部分 API 拒绝该 key）时，切换到备选 key。
- 接口级配置：对“用户信息类”强制使用 `783bbb...`，其它默认 `1d8b6e7d...`。
- Key 与 `mobi_app`、`platform`、`build` 绑定配置，避免跨 key 混用导致风控。

### 4.3 App access_key 获取（TV 扫码）

App 端接口需要 `access_key` 才能获取个性化内容。优先采用 TV 扫码登录获取：

- 申请二维码：`https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code`
- 轮询登录：`https://passport.bilibili.com/x/passport-tv-login/qrcode/poll`
- 登录成功返回 `access_token` 与 `refresh_token`。(`docs/login/login_action/QR.md`)

注意：`access_key` 采用“每次打开刷新”的策略，若接口提示失效则要求用户重新登录（暂不做 refresh_token 续期）。

### 4.4 二维码登录（Web Cookie 兜底）

- 申请二维码：`https://passport.bilibili.com/x/passport-login/web/qrcode/generate`
- 轮询状态：`https://passport.bilibili.com/x/passport-login/web/qrcode/poll`
- 登录成功后响应头写入 Cookie，同时返回 `refresh_token`。(`docs/login/login_action/QR.md`)

### 4.5 密码登录（备选）

涉及极验与 RSA 加密：

- 获取公钥和盐：`https://passport.bilibili.com/x/passport-login/web/key`
- 登录：`https://passport.bilibili.com/x/passport-login/web/login` (`docs/login/login_action/password.md`)

### 4.6 Cookie 刷新（Web 端）

若 `cookie/info` 返回 `refresh=true`，触发刷新链路：(`docs/login/cookie_refresh.md`)

1. 检查：`https://passport.bilibili.com/x/passport-login/web/cookie/info`
2. 生成 `correspondPath` → 获取 `refresh_csrf`：
   `https://www.bilibili.com/correspond/1/{correspondPath}`
3. 刷新 Cookie：`https://passport.bilibili.com/x/passport-login/web/cookie/refresh`
4. 确认更新：`https://passport.bilibili.com/x/passport-login/web/confirm/refresh`

### 4.7 WBI 签名（Web）

用于部分 Web 接口鉴权（如 `view`、`playurl`、首页推荐）。(`docs/misc/sign/wbi.md`)

流程：

1. 从 `nav` 或 `bili_ticket` 获取 `img_key`、`sub_key`。
2. 根据混排表生成 `mixin_key`，截取 32 位。
3. 参数排序、URL 编码（空格 `%20`，编码大写），拼接 `mixin_key`，MD5 得到 `w_rid`。
4. 向原参数追加 `wts` 与 `w_rid`。

### 4.8 APP 签名（App）

APP API 需要 `appkey/appsec`，按参数排序 + MD5 签名。(`docs/misc/sign/APP.md`)

- 适用：`app.bilibili.com` 下部分接口，如 `x/v2/feed/index`、`x/v2/feed/index/story`。
- `appkey/appsec` 需从 `docs/misc/sign/APPKey.md` 选择并配置为可替换项。

### 4.9 bili_ticket（可选，降低风控）

`bili_ticket` 可作为 Cookie 附加，降低风控概率。(`docs/misc/sign/bili_ticket.md`)

## 5. 请求头与设备标识

### 5.1 Web 端推荐 Header

- `User-Agent`：模拟常见浏览器。
- `Referer`：`https://www.bilibili.com/`（部分收藏/动作接口必需）。
- `Cookie`：包含 SESSDATA/bili_jct/buvid3 等。

### 5.2 App 端推荐 Header

- `User-Agent`：Android 端格式。
- `APP-KEY`、`Buvid`、`env`、`session_id`：参考 `docs/video/recommend.md` 示例。

### 5.3 设备标识与指纹

- App 端 BUVID 与 `fp_local/fp_remote` 生成算法参考 `docs/misc/device_identity.md`。
- `fp_*` 用于账户相关 REST API 或 gRPC Metadata。
- 当前版本暂不实现设备指纹；点赞/投币/收藏/收藏夹/历史/稍后再看不依赖设备指纹，优先完成这些功能。

### 5.4 gRPC Metadata（App 端播放/动态）

来源：`bilibili-API-collect` gRPC 文档（线上与本地 `grpc_api/readme.md` 一致）。

关键点：

- Host：`grpc.biliapi.net`（原生 gRPC）或 `app.bilibili.com`（Failover，速度更快）。
- 鉴权：Metadata `authorization: identify_v1 {access_key}`（登录态）。
- 必需 Metadata（简要）：`user-agent`、`x-bili-mid`、`x-bili-trace-id`、`x-bili-aurora-eid`、`buvid`，以及 `x-bili-*-bin` 的设备/网络/区域/实验信息。
- `x-bili-*-bin` 需按 proto 生成并作为二进制 metadata 发送。

## 6. 关键接口清单（按功能）

### 6.1 推荐/信息流（App 优先）

- App 短视频流：`https://app.bilibili.com/x/v2/feed/index`（APP 签名）
- 点击后短视频流：`https://app.bilibili.com/x/v2/feed/index/story`（APP 签名）
- Web 首页推荐：`https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd`（WBI + Cookie，兜底）

### 6.2 动态流

- gRPC 动态：`bilibili.app.dynamic.v2.DynAll`（App gRPC）
- 全部动态：`https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all`（Cookie）

### 6.3 视频详情（Web 兜底）

- 详情：`https://api.bilibili.com/x/web-interface/view` 或 `.../wbi/view`（WBI）
- 详情扩展：`https://api.bilibili.com/x/web-interface/view/detail`（WBI）

### 6.4 播放地址（App gRPC 优先）

- gRPC PlayViewUnite：`bilibili.app.playerunite.v1.PlayViewUnite`（适用于 UGC/PGC）
- gRPC PlayURL：`bilibili.app.playurl.v1.PlayURL`（UGC）
- Web 取流：`https://api.bilibili.com/x/player/wbi/playurl`（WBI + Cookie，兜底）
- 参数：`bvid/avid`、`cid`、`qn`、`fnval`、`fourk` 等。(`docs/video/videostream_url.md`)

### 6.5 点赞/投币/收藏（App 优先）

- 点赞（App）：`https://app.bilibili.com/x/v2/view/like`
- 投币（App）：`https://app.bilibili.com/x/v2/view/coin/add`
- 点赞：`https://api.bilibili.com/x/web-interface/archive/like`
- 投币：`https://api.bilibili.com/x/web-interface/coin/add`
- 收藏：`https://api.bilibili.com/medialist/gateway/coll/resource/deal` 或 `https://api.bilibili.com/x/v3/fav/resource/deal`
- POST 类均需要 `bili_jct` 作为 `csrf`。
 - 以上动作接口不依赖设备指纹，暂不接入 `fp_*`。

### 6.6 稍后再看与历史

- 添加稍后再看：`https://api.bilibili.com/x/v2/history/toview/add`
- 稍后再看列表：`https://api.bilibili.com/x/v2/history/toview`
- 历史记录：`https://api.bilibili.com/x/web-interface/history/cursor`

### 6.7 收藏夹管理

- 收藏夹增删改：`https://api.bilibili.com/x/v3/fav/folder/*`
- 收藏内容管理：`https://api.bilibili.com/x/v3/fav/resource/*`

## 7. 数据模型与映射建议

统一数据模型（示例）：

- `BiliItem`：`id`、`bvid`、`title`、`cover`、`duration`、`stat`、`owner`、`type`
- `BiliOwner`：`mid`、`name`、`face`
- `BiliStat`：`view`、`like`、`danmaku`、`reply`、`coin`、`favorite`
- `BiliPlayUrl`：`dash`/`durl`、`accept_quality`、`quality`

映射优先级：

1. `video/recommend` / `view` 等接口为主。
2. 动态流需做 `type` 分流，仅保留视频与图文摘要字段。

## 8. 缓存与存储

- `AccountStore(BILIBILI)`：Cookie、refresh_token、access_key、app_refresh_token、buvid、bili_ticket、wbi_key、appkey_profile。
- `wbi_key`：按天刷新或当签名失败时刷新。
- `bili_ticket`：TTL 3 天，定时刷新。
- 播放地址缓存 5~10 分钟，超时主动刷新（文档提示有效期约 120 分钟）。

## 9. 错误处理与降级

- HTTP 非 2xx：以网络错误处理，触发短退避重试。
- `code=-101`：登录失效，引导重新登录。
- `code=-111`：CSRF 失效，刷新 Cookie 后重试一次。
- `code=-3`/`code=-663`：AppKey 失效或被拒，切换备用 key 并降级到 Web 端。
- `code=429/频控`：指数退避 + 降级为仅展示详情/跳转浏览器。
- 动态/推荐失败：回退到缓存或空态。

## 10. 风控与限流策略（建议）

- 限流：同一账号单接口 QPS <= 1~2，批量请求间隔 >= 200ms。
- Header 统一：固定 `User-Agent` + 正常 `Referer`。
- 动作类接口必须带 `buvid3`、`bili_ticket`（可选）与 `csrf`。
- 避免频繁切换设备标识，减少登录态漂移。

## 11. 测试建议

- MockWebServer：模拟登录、推荐、播放、动作接口的正常与异常响应。
- 记录与脱敏响应样本，回归字段解析与容错策略。

## 12. 仍需澄清/待验证项

- APP 端 `access_key` 的刷新接口（当前策略为“每次打开刷新、失效重登”）。
- 线上环境的播放能力（DASH/MP4 解码能力与功耗）。

## 13. 参考实现（可选）

- 可参考 `PiliPalaX` 项目中的调用与风控处理方式（仅作思路参考，不直接复用代码）。
