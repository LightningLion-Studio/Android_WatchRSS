# 真机 CI 工作流说明

## 目标

仓库提供了一条 `Android Real Device` workflow，用于在持续在线的真实 Android 设备上跑完整 debug 测试闭环。流程固定为：

1. `checkout`
2. `setup-java`
3. `setup-gradle`
4. `bootstrap_env.sh`
5. `:app:testDebugUnitTest`
6. `:app:lintDebug`
7. `:app:assembleDebug`
8. `:app:assembleDebugAndroidTest`
9. `device_preflight.sh`
10. `device_smoke.sh`
11. `:app:connectedDebugAndroidTest`
12. `collect_artifacts.sh`
13. 上传 `artifacts/`

这条流程面向 `GitHub Actions + self-hosted runner + 专用真机`，目标不是模拟器兼容，而是真机回归闭环。

当前仓库的 instrumentation 已启用：
- `Android Test Orchestrator`
- `clearPackageData=true`
- `animationsDisabled=true`

也就是说 `connectedDebugAndroidTest` 会以隔离进程方式执行每条用例，优先保证真机稳定性而不是最短执行时间。

## Runner 要求

- Runner 标签必须同时包含：
  - `self-hosted`
  - `linux`
  - `android-real-device`
- Runner 主机必须预装：
  - JDK 17
  - Android SDK
  - `platform-tools`
  - `platforms;android-36`
  - `build-tools;36.1.0`
- Runner 用户必须能直接执行：
  - `adb`
  - `bash`
  - `./gradlew`
- Workflow 会把 `GRADLE_USER_HOME` 放到工作区内的 `.gradle-ci/`，避免污染 runner 用户目录。

## 真机准备要求

- 设备必须持续在线，并且只服务这条 CI 流程。
- 首次连接必须完成 adb 授权。
- 若 runner 可能看到多台设备，必须配置 `ANDROID_SERIAL`。
- 建议关闭锁屏密码，至少保证：
  - 可通过 `input keyevent KEYCODE_WAKEUP` 唤醒
  - 可通过 `wm dismiss-keyguard` 或 `input keyevent 82` 解锁
- 不要在 CI 执行期间人工操作设备，否则会干扰 `smoke` 和 `connectedDebugAndroidTest`。

## 仓库变量与 workflow 输入

### 仓库变量

- `ANDROID_SERIAL`
  - 推荐配置为 GitHub repository variable
  - 当 runner 只有一台真机时可以不配，但仍建议显式配置

### Workflow 输入

- `device_serial`
  - 可选
  - 用于覆盖 `ANDROID_SERIAL`
- `instrumentation_class`
  - 可选
  - 用于只跑某个 `androidTest` 类或包
  - 实际透传为 `-Pandroid.testInstrumentationRunnerArguments.class=...`

## 脚本职责

### `scripts/ci/bootstrap_env.sh`

- 校验 `ANDROID_SDK_ROOT` 或 `ANDROID_HOME`
- 生成 `local.properties`
- 记录 Java、adb、Gradle、runner 环境信息到 `artifacts/bootstrap/`

### `scripts/ci/device_preflight.sh`

- 启动 adb server，并在未发现可用设备时重启一次 adb server
- 记录 `adb devices -l`
- 校验是否存在 `unauthorized` 或 `offline` 设备
- 自动解析目标 serial，必要时写入 `selected-serial.txt`
- 采集设备属性、电量、分辨率、窗口栈、Activity 栈等诊断信息
- 唤醒并尝试解锁设备

### `scripts/ci/device_smoke.sh`

- 清空目标应用与测试包数据
- 安装 `debug` 和 `debugAndroidTest` APK
- 启动 `MainActivity`
- 校验应用进程是否存在、顶层窗口是否进入应用
- 抓取截图与 smoke logcat
- 若发现应用自身的 `FATAL EXCEPTION`、`AndroidRuntime`、`ANR`、进程死亡或 fatal signal，立即失败

### `scripts/ci/collect_artifacts.sh`

- 在 `always()` 阶段执行，不依赖前序步骤成功
- 采集最终 `adb devices -l`、设备状态、最终 logcat
- 归档 unit test、lint、connectedAndroidTest 报告
- 若 instrumentation 阶段 logcat 里存在应用自身 fatal，再把 workflow 标红

## 产物结构

Workflow 最终上传整个 `artifacts/` 目录，常用内容如下：

- `artifacts/bootstrap/`
  - `environment.txt`
  - `java-version.txt`
  - `adb-version.txt`
  - `gradle-version.txt`
- `artifacts/device/`
  - `adb-devices.txt`
  - `selected-serial.txt`
  - `device-summary.txt`
  - `getprop.txt`
  - `battery.txt`
  - `wm-size.txt`
  - `wm-density.txt`
  - `activities-preflight.txt`
  - `top-activity-smoke.txt`
  - `smoke.png`
- `artifacts/logs/`
  - `logcat-smoke.txt`
  - `logcat-instrumentation.txt`
- `artifacts/gradle/`
  - `unit-test-report/`
  - `unit-test-results/`
  - `lint/`
  - `android-tests-report/`
  - `android-tests-results/`

## 手动触发方式

1. 打开仓库的 `Actions`
2. 选择 `Android Real Device`
3. 点击 `Run workflow`
4. 按需填写：
  - `device_serial`
  - `instrumentation_class`

如果不填 `device_serial`，workflow 会优先使用仓库变量 `ANDROID_SERIAL`，否则尝试自动选择唯一处于 `device` 状态的真机。

## 常见故障排查

### 找不到设备

- 查看 `artifacts/device/adb-devices.txt`
- 确认 runner 上手动执行 `adb devices -l` 能看到设备
- 若同时连了多台设备，请设置 `ANDROID_SERIAL`
- `device_preflight.sh` 已经会重启一次 adb server；重启后仍无设备就会直接失败

### 设备是 `unauthorized` 或 `offline`

- 重新插拔 USB 或重连 adb
- 在设备上确认 adb 授权弹窗
- 再次触发 workflow
- 相关状态会保存在 `artifacts/device/adb-devices.txt`

### 应用启动即崩

- 查看 `artifacts/logs/logcat-smoke.txt`
- 查看 `artifacts/device/top-activity-smoke.txt`
- 查看 `artifacts/device/windows-smoke.txt`
- `device_smoke.sh` 会在检测到应用自身 fatal 后直接中断，不会继续跑 instrumentation

### `connectedDebugAndroidTest` 找不到设备

- 先看 `device_preflight.sh` 是否成功生成 `selected-serial.txt`
- 再看 workflow 日志中 `ANDROID_SERIAL` 是否已被后续步骤继承
- 如果 runner 还有其他任务会抢占 adb server，需要把这台 runner 专用于这条 workflow

### instrumentation 运行很慢

- 当前是 orchestrator 模式，每条用例会独立拉起 instrumentation
- 这会比共享单进程测试更慢，但能显著降低真机上的状态污染和偶发进程崩溃
- 如果只想排查某一组用例，优先使用 `instrumentation_class`

### Gradle 找不到 SDK

- 检查 runner 环境变量：
  - `ANDROID_SDK_ROOT`
  - `ANDROID_HOME`
- `bootstrap_env.sh` 会把 SDK 路径写入 `local.properties`
- 若 SDK 目录本身不存在，workflow 会在 bootstrap 阶段直接失败
