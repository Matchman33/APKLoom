# Frida Gadget 使用

准备与目标应用架构匹配的官方 Gadget `.so` 文件，支持 ARM64 和 x86_64。仓库不附带 Gadget 二进制。

## 手机配置

1. 在 APK Loom 中选择目标 APK，开启“启用 Frida Gadget”。
2. 点击“选择本地 Gadget”，选择解压后的 `.so` 文件。
3. 选择“监听”或“脚本”模式，按下文配置。
4. 点击“生成 APK”，安装后启动生成的应用。

Gadget 只加入本次生成的 APK，并在应用主进程加载。文件最大 128 MiB；脚本必须是 UTF-8 JavaScript，最大 16 MiB。

## 监听模式

默认地址为 `127.0.0.1`，端口为 `27043`。开启“启动时等待客户端连接”时，应用会等待 Frida 连接；需要直接启动应用时关闭该选项。

手机连接 ADB 后，在安装了 Frida 命令行工具的电脑上执行：

```powershell
adb forward tcp:27043 tcp:27043
frida -H 127.0.0.1:27043 Gadget
```

若修改了监听端口，同步修改上述命令。端口已被占用时请选择其他端口。

## 脚本模式

选择“脚本”后，点击“选择本地脚本”，选中 JavaScript 文件。脚本会随应用启动执行；可从仓库的 [脚本示例](examples/script/libscript.so) 开始。示例虽使用 `.so` 扩展名，内容是 JavaScript。

## 命令行

监听模式：

```powershell
java -jar out/wrapper/apkloom-cli.jar example.apk -o output-listen `
  --gadget C:\path\to\frida-gadget.so --gadget-mode listen `
  --gadget-address 127.0.0.1 --gadget-port 27043
```

加入 `--gadget-resume` 可在加载后直接启动应用。脚本模式：

```powershell
java -jar out/wrapper/apkloom-cli.jar example.apk -o output-script `
  --gadget C:\path\to\frida-gadget.so --gadget-mode script `
  --gadget-script C:\path\to\hook.js
```

[返回使用指南](../README.md)
