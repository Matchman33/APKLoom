# 构建与命令行指南

手机上的操作步骤见 [使用指南](README.md#手机上使用)。以下命令在仓库根目录执行。

## 配置环境

安装 Git、完整的 JDK 21 和 Android SDK Command-line Tools，并设置路径：

```powershell
$env:JAVA_HOME = "<JDK 21 安装目录>"
$env:ANDROID_HOME = "<Android SDK 目录>"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

git submodule update --init --recursive
.\gradlew.bat --version
```

确认输出中的 `Daemon JVM` 为 21。安装 SDK 组件并接受许可证：

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" `
  "platforms;android-37.0" "build-tools;37.0.0" `
  "ndk;29.0.13846066" "cmake;3.31.6"
```

也可在 Android Studio SDK Manager 中安装相同版本。若不设置 `ANDROID_HOME`，在根目录和 `core/` 下分别创建 `local.properties`，写入同一个 SDK 路径，例如 `sdk.dir=D:/Sdk`。

## 构建与测试

构建本地测试版并运行单元测试：

```powershell
.\gradlew.bat -PstandaloneWrapper=true -PallowDebugSigning=true `
  :wrapper-manager:collectReleaseArtifacts `
  :wrapper-manager:testDebugUnitTest :wrapper-patch:test :patch-loader:testDebugUnitTest
```

macOS/Linux 将 `.\gradlew.bat` 换为 `./gradlew`，使用对应的环境变量语法；首次执行前运行 `chmod +x gradlew`。

| 产物 | 路径 |
| --- | --- |
| 管理器 APK | `wrapper-manager/build/outputs/apk/release/wrapper-manager-release.apk` |
| CLI JAR | `out/wrapper/apkloom-cli.jar` |
| 测试版产物与校验文件 | `out/releases/<版本>-local/` |

版本取自 `gradle.properties`。产物目录包含 `SHA256SUMS.txt` 和 `BUILD.txt`。

## 正式签名

在 `gradle.properties` 中设置 `apkLoomVersionName`，并递增 `apkLoomVersionCode`。配置以下环境变量后构建：

| 环境变量 | 内容 |
| --- | --- |
| `ANDROID_STORE_FILE` | 签名密钥文件路径 |
| `ANDROID_STORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名 |
| `ANDROID_KEY_PASSWORD` | 密钥密码 |

```powershell
.\gradlew.bat -PstandaloneWrapper=true :wrapper-manager:collectReleaseArtifacts
```

正式产物位于 `out/releases/<版本>/`。更新已安装的管理器时应使用相同签名；签名密钥保存在仓库外。

GitHub Actions 构建正式版本时，配置 `KEY_STORE`（Base64 密钥文件）、`KEY_STORE_PASSWORD`、`ALIAS`、`KEY_PASSWORD` 四项 Secrets，并推送与版本名一致的 `v<版本>` 标签。构建产物从 Actions 的 Artifacts 下载。

## 命令行用法

```powershell
# 基本封装
java -jar out/wrapper/apkloom-cli.jar example.apk -o output

# 允许 HTTP，并在首次打开时提示悬浮窗授权
java -jar out/wrapper/apkloom-cli.jar example.apk -o output-options --http allow --request-overlay

# 修改包名并启用原签名兼容
java -jar out/wrapper/apkloom-cli.jar example.apk -o output-renamed -p example.wrapped --signature-compat

# 查看全部参数
java -jar out/wrapper/apkloom-cli.jar --help
```

| 参数 | 用法 |
| --- | --- |
| `-o` / `--output` | 必填，输出目录；保留输入 APK 文件名，不覆盖已有文件 |
| `-p` / `--package` | 指定生成应用的包名，默认保持原包名 |
| `--http` | `original` 保持原设置（默认）、`allow` 允许、`block` 禁止明文 HTTP |
| `--request-overlay` | 添加悬浮窗权限并在首次打开时提示授权 |
| `--signature-compat` | 启用应用内原签名兼容，不改变 APK 实际签名 |
| `--keystore` / `--store-type` / `--alias` | 指定生成 APK 的签名密钥、密钥库类型（默认 BKS）和别名 |
| `--store-password-env` / `--key-password-env` | 保存密码的环境变量名；不指定密钥密码时使用密钥库密码 |

每次处理一个完整 APK。HTTP 和悬浮窗选项的限制见 [使用指南](README.md#手机上使用)；Gadget 参数见 [Gadget 使用](gadget/README.md)。
