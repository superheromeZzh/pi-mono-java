# Windows 网络问题排查指南

本文档记录了 CampusClaw 在 Windows 上遇到的网络问题及解决方案，供后续开发参考。

## 问题一：Connect timed out（连接超时）

### 症状

Windows 上运行时报错：
```
Error: Request failed: Connect timed out
```
Mac 上相同代码正常。

### 根因

`-Djava.net.useSystemProxies=true` 让 Java 自动读取 Windows 注册表中的系统代理设置：
```
HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings
  ProxyEnable = 1
  ProxyServer = 127.0.0.1:7890
```

Clash、V2Ray 等代理工具在开启时会写入这些注册表项，但**关闭后往往不会清除**。Java 读到残留配置后，将所有流量路由到已关闭的代理，导致连接超时。

Mac 上没有残留的系统代理配置，所以不受影响。

### 解决方案

**移除 `java.net.useSystemProxies=true`**，改为默认直连。需要代理时通过 `--proxy` 参数显式指定：

```batch
campusclaw.bat --proxy http://127.0.0.1:7890 -m glm-5
```

### 教训

- `java.net.useSystemProxies=true` 在 Windows 上不可靠，不适合作为默认配置
- 代理应该是 opt-in（用户主动开启），而不是 opt-out（自动检测）
- 自动检测 Windows 注册表代理同样有残留风险，也不应该作为默认行为

---

## 问题二：企业内部 API 的 SSL 证书验证失败

### 症状

连接企业内部 LLM API 时报 SSL 相关错误（如 `PKIX path building failed`），需要手动跳过 SSL 验证才能使用。

### 根因

Java 默认使用 JDK 内置的 `cacerts` 证书库，该证书库只包含公共 CA 证书。企业内部 API 使用的是企业内部 CA 签发的证书，不在 `cacerts` 中。

而 Windows 系统证书库（供浏览器、系统应用使用）通常已经安装了企业内部 CA 证书，Java 默认不读取它。

### 解决方案

在 `campusclaw.bat` 启动参数中添加：
```
-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT
```

这让 Java 使用 Windows 系统证书库替代 JDK 内置的 `cacerts`。系统证书库包含：
- 所有主流公共 CA 证书（和 `cacerts` 基本一致）
- 企业通过组策略推送的内部 CA 证书

无需手动跳过 SSL 验证，也无需将证书导入 JDK 的 `cacerts`。

### 注意事项

- 此参数仅在 Windows 上有效（`WINDOWS-ROOT` 是 SunMSCAPI 提供的 KeyStore 类型）
- Mac 上对应的是 `-Djavax.net.ssl.trustStoreType=KeychainStore`，但通常不需要（Mac 的 JDK 默认行为已足够）
- `campusclaw.sh`（Mac/Linux）不需要添加此参数

---

## 最终的 campusclaw.bat 启动行

```batch
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Djavax.net.ssl.trustStoreType=WINDOWS-ROOT -jar "%JAR%" %ARGS%
```

| 参数 | 作用 |
|------|------|
| `-Dfile.encoding=UTF-8` | 控制台中文编码 |
| `-Dstdout.encoding=UTF-8` | 标准输出 UTF-8 |
| `-Dstderr.encoding=UTF-8` | 标准错误 UTF-8 |
| `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` | 使用 Windows 系统证书库 |

不再使用 `-Djava.net.useSystemProxies=true`。
