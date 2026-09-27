# APK Loom

无需 Root 的 APK 封装工具，提供 Android 管理器和命令行工具。

## 目标与特点

将单个 APK 生成为可安装、独立运行的应用，按需配置网络访问、悬浮窗权限和调试功能。

- 从本地 APK 或已安装应用生成安装包。
- 保留原图标、应用名称和完整原包，支持修改包名及输出文件名。
- 支持 HTTP 访问策略、悬浮窗授权引导和可选原签名兼容。
- 支持按次添加 Frida Gadget，选择监听或脚本模式。
- 生成后可导出、安装、打开或分享，运行时无需管理器常驻。

## 使用要求

- Android 9 及以上，ARM64 或 x86_64 设备及应用。
- 输入需为完整的单个 APK；不支持分包、32 位应用、sharedUserId 或隔离进程应用。
- 命令行工具需要 JDK 21。

## 手机上使用

1. 安装 APK Loom 管理器，点击“选择 APK”或“已安装应用”。
2. 确认包名和文件名，按需调整下表中的选项。
3. 点击“生成 APK”，等待任务完成。
4. 点击“导出”保存文件，或点击“安装”；系统要求时允许安装未知应用。
5. 安装后点击“打开”，也可通过桌面图标启动。

| 选项 | 用法 |
| --- | --- |
| 包名 | 默认保留原包名；需要与原应用共存时设置不同包名，部分应用可能不兼容 |
| 原签名兼容 | 默认关闭；需要兼容应用内签名检查时开启，不改变安装包的实际签名 |
| HTTP 访问 | 默认保持原设置；可选择允许或禁止明文 HTTP，HTTPS 证书校验保持不变 |
| 申请悬浮窗权限 | 默认关闭；开启后首次启动提示前往系统设置授权，可跳过，之后不重复提示 |
| Frida Gadget | 默认关闭；开启后选择本地 Gadget 文件，详见 [Gadget 使用](gadget/README.md) |

同包名但签名不同的 APK 不能直接覆盖安装。请使用匹配的签名，或尝试修改包名。
HTTP 选项适用于遵循 Android 网络策略的请求，不能拦截所有原生网络请求。关闭悬浮窗选项不会移除原应用已有的权限。

导出时若系统文件选择器不可用，文件保存到 `Download/ApkLoom`。修改选项后需重新生成 APK；需要保留的结果请先导出。

## 构建

准备 JDK 21、Android SDK Platform 37.0、Build Tools 37.0.0、NDK 29.0.13846066、CMake 3.31.6，并设置 `JAVA_HOME`、`ANDROID_HOME`。

在仓库根目录执行：

```powershell
git submodule update --init --recursive
.\gradlew.bat -PstandaloneWrapper=true -PallowDebugSigning=true :wrapper-manager:collectReleaseArtifacts
```

产物位于 `out/releases/<版本>-local/`，包括管理器 APK、CLI JAR 和校验文件。`-local` 为本机 Debug 签名的测试版。
完整环境配置、测试命令和正式签名见 [构建与命令行指南](WRAPPER.md)。

## 命令行

```powershell
java -jar out/wrapper/apkloom-cli.jar example.apk -o output
java -jar out/wrapper/apkloom-cli.jar example.apk -o output-options --http allow --request-overlay
```

输出保留输入文件名，目标文件不能已存在。更多参数见 [命令行用法](WRAPPER.md#命令行用法)。

## 许可证与致谢

基于 NPatch，遵循 [GNU GPL v3](LICENSE)。感谢 NPatch、LSPosed、Xpatch 和 Apkzlib 项目。
