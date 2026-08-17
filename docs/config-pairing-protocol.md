# 手机配置配对协议 v1

`watchrss-config-pairing-v1` 用于手表临时 HTTP 服务中的 LLM/TTS 配置读写。它不把配对密钥
或 API Key 放入 HTTP URL、请求日志或明文正文。

采用此协议的 `LLM_SUMMARY_CONFIG` / `TTS_CONFIG` ability version 为 `0.0.2`；旧的 `0.0.1`
明文客户端必须提示升级，不能回退到未认证请求。

## 二维码

手表每次打开配置二维码页面生成 32 个随机字节，编码为无填充 Base64URL：

```text
http://192.168.1.20:34567/#watchrss_pair=<base64url>&protocol=watchrss-config-pairing-v1
```

`#` 后的 fragment 只由扫码客户端读取，不会随 HTTP 请求发送。退出页面或第一次有效写入被接受后，
该会话失效；客户端不得持久化配对密钥。

## 请求证明

每个配置请求生成新的 16-byte 随机 nonce，并以无填充 Base64URL 编码：

```text
X-WatchRSS-Pairing-Nonce: <nonce>
X-WatchRSS-Pairing-Auth: <proof>
```

`proof` 为 HMAC-SHA256 的无填充 Base64URL 编码。HMAC key 是二维码中 Base64URL 解码后的
32 个原始字节，消息为以下 UTF-8 字符串：

```text
UPPERCASE_HTTP_METHOD + "\n" +
REQUEST_PATH + "\n" +
NONCE + "\n" +
base64url_no_padding(SHA-256(exact_utf8_http_body))
```

GET 请求的正文是空字符串。服务端以常量时间比较 proof，并拒绝已经接受过的 nonce。

## 配置加密信封

加密 key 为：

```text
SHA-256(utf8("watchrss-config-encryption-v1") || 0x00 || raw_pairing_secret)
```

使用 `AES/GCM/NoPadding`、12-byte 随机 IV、128-bit tag。AAD 是：

```text
UPPERCASE_HTTP_METHOD + "\n" + REQUEST_PATH
```

线上 JSON 正文/响应格式：

```json
{
  "protocol": "watchrss-config-pairing-v1",
  "iv": "<base64url-no-padding>",
  "ciphertext": "<ciphertext-and-gcm-tag-base64url-no-padding>"
}
```

- `POST /setLLMSummaryConfig` 与 `POST /setTtsConfig` 的业务 JSON 必须放入加密信封。
- `GET /getLLMSummaryConfig` 与 `GET /getTtsConfig` 必须带请求证明，成功响应也是加密信封。
- POST 的 HMAC 正文摘要针对“线上加密信封原文”，不是解密后的业务 JSON。
- 所有配置响应带 `Cache-Control: no-store`。

## 安全边界

应用层加密隐藏配置内容并阻止无密钥设备伪造或修改请求，但底层仍是局域网 HTTP。旁路设备仍可
观察手表 IP、端口、端点访问时序和密文长度，也可能通过抢先重放原样请求造成拒绝服务。二维码被
他人拍摄，或手机/手表端点已经被攻陷时不在保护范围内。用户只应扫描手表现场显示的最新二维码。
