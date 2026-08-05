# 朗读语音（TTS）多源接入方案

本文档描述 WatchRSS Android 端的多源 TTS 架构：保留本地系统 TTS，新增应用默认语音（后端代理），并支持用户自带 API Key（BYOK）接入 MiniMax、Azure、豆包等第三方语音服务。

## 1. 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                       ReadAloudController                    │
│              （调度分段、控制播放、音量限制）                  │
└───────────────────────┬─────────────────────────────────────┘
                        │ 通过 TtsEngineFactory 创建
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                        TtsEngine 抽象                        │
│ 统一接口：prepare / speak(segment, rate, listener) / stop    │
└───────┬───────────────┬───────────────┬───────────────────────┘
        │               │               │
        ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐
│ LocalTtsEngine │ │BackendTtsEngine│ │ MiniMaxTtsEngine         │
│ （系统 TTS）   │ │（应用默认语音） │ │ AzureTtsEngine           │
│                │ │ 后端 /tts/...  │ │ DoubaoTtsEngine          │
└──────────────┘ └──────────────┘ └──────────────────────────┘
```

- `TtsEngine`：朗读引擎抽象，屏蔽本地/云端差异。
- `TtsEngineFactory`：根据 `SettingsRepository` 中保存的 `tts_engine` 创建对应实现。
- `ExoPlayerTtsAudioPlayer`：云端引擎返回的音频统一由 ExoPlayer 播放。

## 2. 引擎类型

| 引擎标识 | 名称 | 是否需要 API Key | 说明 |
|---|---|---|---|
| `local` | 本地 TTS | 否 | 使用 Android 系统 TextToSpeech |
| `backend_default` | 应用默认语音 | 否（使用账号 Token） | 走后端代理 `/api/v1/tts/default-model/speech` |
| `minimax` | MiniMax | 是 | 调用 MiniMax `v1/t2a_v2` |
| `azure` | Azure | 是 | 调用 Azure Cognitive Services TTS |
| `doubao` | 豆包 | 是 | 调用火山引擎/豆包 `api/v1/tts` |

默认音色/模型：

| 引擎 | 默认模型 | 默认音色 |
|---|---|---|
| MiniMax | `speech-2.8-hd` | `male-qn-qingse` |
| Azure | `zh-CN-XiaoxiaoNeural` | `zh-CN-XiaoxiaoNeural` |
| 豆包 | `zh_female_wanwanxin_moon_bigtts` | `zh_female_wanwanxin_moon_bigtts` |

## 3. Android 端实现

### 3.1 数据存储

`SettingsRepository`（DataStore）保存以下 TTS 配置：

- `tts_engine`：当前引擎标识。
- `tts_model`：模型（云端引擎使用）。
- `tts_voice_id`：音色 ID（云端引擎使用）。
- `tts_speed`：语速，范围 `0.5 ~ 2.0`，默认 `1.0`。
- `tts_base_url`：自定义 Base URL（可选）。

BYOK 的 API Key 保存在 `TtsApiKeyStore`（EncryptedSharedPreferences），按引擎隔离：

```
tts_api_key_minimax
tts_api_key_azure
tts_api_key_doubao
```

### 3.2 设置页

- `TtsSettingsViewModel`：维护 TTS 状态，提供切换引擎、调节语速、连通性测试等方法。
- `TtsSettingsScreen`：手表端设置界面，包含引擎选择、语速调节、API Key 状态、连接测试、手机扫码配置入口。
- `TtsSettingsActivity`：承载设置页的 Activity，已在 `AndroidManifest.xml` 注册。

设置入口位于 `SettingsScreen` → "朗读语音源"。

### 3.3 连通性测试

`TtsSettingsViewModel.runTest()` 根据当前引擎执行不同测试：

- 本地 TTS：直接返回成功。
- 应用默认语音：调用后端 `/api/v1/tts/default-model/speech`，校验是否能拿到音频数据。
- MiniMax/Azure/豆包：按各厂商接口发起短文本合成请求，校验响应。

## 4. 后端接口

应用默认语音走后端代理，避免在手表端暴露 MiniMax API Key：

```
POST /api/v1/tts/default-model/speech
Authorization: Bearer <watch_device_token>
Content-Type: application/json

{
  "text": "要朗读的文本",
  "speed": 1.0,
  "format": "mp3"
}
```

返回：

```json
{
  "audio": "<hex encoded mp3 audio>"
}
```

后端实现位于 `/Users/shulk/Documents/WatchRSS_Backend/src/tts/`。

## 5. 手机扫码配置

手表屏幕输入长字符串体验差，因此提供二维码配置能力。手表端启动 `ServerActivity` 的 `TTS_CONFIG` 模式，手机端扫码后通过局域网 HTTP 读写配置。

### 5.1 能力标识

`PhoneConnectionAbility.TTS_CONFIG`

- `wireCode`: `b8f4d9e2-1c7a-4e5b-9a3f-6d2e8c1b5f74`
- `acousticCode`: `t`

### 5.2 端点

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/getTtsConfig` | 读取当前 TTS 配置，API Key 脱敏返回 |
| POST | `/setTtsConfig` | 写入 TTS 配置（含明文 API Key） |

### 5.3 数据格式

`/getTtsConfig` 返回示例：

```json
{
  "success": true,
  "data": {
    "engine": "minimax",
    "model": "speech-2.8-hd",
    "voiceId": "male-qn-qingse",
    "speed": 1.0,
    "baseUrl": "https://api.minimaxi.com",
    "apiKey": "****abcd"
  }
}
```

`/setTtsConfig` 请求示例：

```json
{
  "engine": "minimax",
  "model": "speech-2.8-hd",
  "voiceId": "male-qn-qingse",
  "speed": 1.0,
  "baseUrl": "https://api.minimaxi.com",
  "apiKey": "sk-..."
}
```

### 5.4 默认模型特殊处理

- 当 `engine` 为 `backend_default`（应用默认语音）时，**不保存 API Key**，`baseUrl`、`model`、`voiceId` 也置空，手表端会从登录账号中读取后端地址。
- 当 `engine` 为 `local`（本地 TTS）时，同样不保存 API Key，只保存引擎和语速。
- 仅当 `engine` 为 MiniMax/Azure/豆包等 BYOK 引擎时，才要求并保存 API Key 与自定义 Base URL。

## 6. 流程示例

### 6.1 首次选择第三方语音

1. 用户进入 `设置 → 朗读语音源`。
2. 点击"当前引擎"，选择 MiniMax。
3. 点击"手机扫码配置语音"，手表显示二维码。
4. 手机端扫码后调用 `/getTtsConfig` 读取当前配置，再调用 `/setTtsConfig` 写入音色、模型和 API Key。
5. 手表端保存配置，返回设置页。
6. 用户点击"测试连接"验证连通性。

### 6.2 切换回应用默认语音

1. 用户在引擎选择中切换到"应用默认语音"。
2. `TtsSettingsViewModel` 自动使用登录账号的后端地址。
3. 手表端清空 model、voiceId、baseUrl 和 API Key。
4. 朗读时 `BackendTtsEngine` 使用账号 Token 调用后端默认语音接口。

## 7. 注意事项

- 后端默认语音需要用户已登录且 `backendBaseUrl`、`watchDeviceToken` 有效。
- 本地 TTS 的可用性取决于系统是否安装了中文 TTS 引擎。
- 云端引擎在手表端使用 `OkHttp` 同步请求 + `ExoPlayer` 播放，语速通过 ExoPlayer 的 `setPlaybackSpeed` 实现。
- API Key 仅存储在手表本地加密存储中，不会上传到我们自己的后端（后端默认语音除外）。
