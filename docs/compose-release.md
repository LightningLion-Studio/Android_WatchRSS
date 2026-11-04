# Compose Release 构建

独立 Compose 包名为 `com.lightningstudio.watchrss.compose`，与主包并行安装。
覆盖安装必须使用现有 Compose 包的签名；签名配置放在本机 `keystore.properties`，不得提交密钥或密码。
现有 Gradle release signing 配置读取 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`。

```sh
./gradlew -Pwatchrss.applicationId=com.lightningstudio.watchrss.compose :app:assembleRelease
```

默认不传参数时仍构建主包 `com.lightningstudio.watchrss`。
发布独立包前，核对 APK 的包名、版本代码、签名指纹及非 debuggable 标志。
Release 使用 R8 代码优化和资源压缩，产物位于 `app/build/outputs/apk/release/app-release.apk`。

## 旧手机版同步

手机版 1.3.0-1 的常规合并同步先在持久连接中同步账号，再同步资料。
手表仅在同一连接账号同步成功后兼容未携带账号字段的旧请求。
新连接或账号同步失败后必须重新完成账号同步，不能继承上一连接的授权。
显式账号字段始终接受匹配检查，清空资料库也执行同一账号检查。
单项操作、单向覆盖和断线自动恢复不能假定具有该账号握手。
