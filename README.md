# 腕上RSS - 可穿戴设备的哔哩哔哩与抖音与RSS阅读器
![腕上RSS](readme_icon.png)

## 📌 项目概述
### 项目背景
随着可穿戴智能手表的普及，用户对轻量化、场景化的内容消费需求日益增长。传统手机端的B站、抖音及RSS阅读工具操作成本高，无法适配手表“碎片化、便捷化”的使用场景。本项目旨在打造一款专为可穿戴手表定制的“腕上RSS”应用，聚合B站、抖音轻量化内容浏览与通用RSS订阅功能，让用户在抬手间即可获取核心信息。

### 项目定位
- **核心目标**：轻量化、高效化的腕上内容消费入口
- **目标用户**：可穿戴设备用户、内容爱好者、碎片化阅读需求人群
- **产品slogan**：腕间方寸，尽览万象

### 腕上RRS会上架应用商店吗？
- [x] 会上架应用商店

---

## 🎯 核心功能规划
### 1. 内容聚合模块
| 功能子模块 | 核心能力 | 手表端适配要点 |
|------------|----------|----------------|
| B站轻量化浏览 | 1. 关注UP主动态预览<br>2. 短视频/专栏文字摘要<br>3. 一键收藏（同步手机端） | 适配手表小屏，仅展示文字+缩略图，无视频播放（避免功耗/体验问题） |
| 抖音轻量化浏览 | 1. 关注博主动态摘要<br>2. 热门短视频文字+封面预览<br>3. 点赞/收藏（极简操作） | 滑动切换内容，单次仅加载1条内容，降低内存占用 |
| 通用RSS阅读器 | 1. 自定义RSS源订阅<br>2. 内容标题+摘要展示<br>3. 已读/未读标记<br>4. 分类管理订阅源 | 支持手动输入/扫码添加RSS地址，适配手表输入法 |

---

## 🔧 技术选型（RSS）
- RSS 解析：RSS-Parser（`com.prof18.rssparser:rssparser`），Kotlin Multiplatform，支持 RSS/Atom/RDF。
- 版本以 `gradle/libs.versions.toml` 为准（当前 6.0.10）。
- UI：Jetpack Compose（主流程已迁移，去除 RecyclerView/Adapter 依赖）。

## 🛠️ 本地构建配置
- `gradle.properties` 为本地环境文件，不纳入 Git 版本控制（用于放置各机器独有路径配置）。
- 首次拉取代码后请执行：
```bash
cp gradle.properties.example gradle.properties
```
- 如需配置本机路径（如 `org.gradle.java.home`、临时目录等），请仅修改本地 `gradle.properties`。

## 🧾 日志上传器资源更新
- 本地日志上传页面来自仓库外的 `../Loger_key/LogUploader/LogUploader`，私钥也保留在 `../Loger_key`，不要放进 Android 仓库。
- 复用现有私钥并重新构建、同步到 `app/src/main/assets/log_upload/`：
```bash
scripts/update_log_upload_assets.sh
```
- 如果需要轮换日志加密密钥，执行：
```bash
scripts/update_log_upload_assets.sh --rotate-key
```
- 脚本会生成/更新 `../Loger_key/rsa_pub_pkcs1.pem`，同步 `private_key.pem` / `rsa_pub_pkcs1.pem` 到 LogUploader 根目录，同步私钥到 `../log-decrypter/public/private_key.pem`，把公钥写进 `src/config/publicKey.js`，运行 `npm run build`，再把 `dist` 产物同步进 Android assets 并修正 Vite 资源路径为相对路径。

## 📦 Profileable Release 打包
- 执行 `scripts/build_profileable_release.sh` 可构建 `profileableRelease`，并将 APK、`mapping.txt`、`output-metadata.json` 收集到 `app/build/outputs/dist/profileableRelease/`。
- 如需自定义输出目录，可执行：
```bash
scripts/build_profileable_release.sh --dist-dir /tmp/watchrss-profileable
```
