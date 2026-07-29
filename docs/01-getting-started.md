# 入门指南

## 环境变量

本机 `~/.claude/.env` 文件中已配置 `LogFox-Cli` token：

```powershell
$env:LOGFOX_TOKEN = (Get-Content "$HOME\.claude\.env" | Select-String "LogFox-Cli").ToString().Split('=')[1]
```

---

## 构建命令

```powershell
./gradlew :app:assembleDebug --quiet          # 构建 Debug APK
./gradlew testDebugUnitTest --quiet            # 运行单元测试
./gradlew verifyRoborazziDebug --quiet         # 快照测试（CI 使用）
```

---

## 构建要求

| 属性 | 说明                                              |
| ---- | ------------------------------------------------- |
| JDK  | 21 (Temurin)                                      |
| 产物 | `app/build/outputs/apk/debug/LogFox-unknown-debug.apk` |

---

## 常见问题

### Windows AIDL 编译编码错误

AGP 9.2 + JDK 21 在 Windows 上编译 AIDL 时遇到非 ASCII 字符会报错：

```text
java.nio.charset.MalformedInputException: Input length = 1
```

**解决方案**：

1. 使用 Android Studio 构建
2. 或临时删除 `feature/terminals/impl/src/main/res/values-*` 下的非英文 strings.xml

### 依赖缺失

```text
Unresolved reference: ContentNegotiation
```

**解决方案**：检查并添加必要的依赖。

### Ktor API 变更

```text
embeddedServer 返回类型不匹配
```

**解决方案**：改用 `Any` 存储 server。

### 类型推断失败

```text
Cannot infer type
```

**解决方案**：显式指定泛型。

### Hilt 绑定缺失

```text
Dagger/MissingBinding
```

**解决方案**：创建 `@Binds` 或 `@Provides`。
