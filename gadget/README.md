# Frida Gadget 使用

准备与目标应用架构匹配的 Gadget `.so` 文件，支持 ARM64 和 x86_64。仓库不附带 Gadget 二进制。

## 手机配置

1. 在 APK Loom 中选择目标 APK，开启“启用 Frida Gadget”。
2. 点击“选择本地 Gadget”，选择解压后的 `.so` 文件。
3. 选择“监听”“脚本”或“自定义”模式，按下文配置。
4. 点击“生成 APK”，安装后启动生成的应用。

Gadget 只加入本次生成的 APK，并在应用主进程加载。文件最大 128 MiB；脚本最大 16 MiB、不能为空，按原始字节保存，不限制编码。

## 监听模式

默认地址为 `127.0.0.1`，端口为 `27043`。开启“启动时等待客户端连接”时，应用会等待 Frida 连接；需要直接启动应用时关闭该选项。

手机连接 ADB 后，在安装了 Frida 命令行工具的电脑上执行：

```powershell
adb forward tcp:27043 tcp:27043
frida -H 127.0.0.1:27043 Gadget
```

若修改了监听端口，同步修改上述命令。端口已被占用时请选择其他端口。

## 脚本模式

选择“脚本”后，点击“选择本地脚本”，选中 JavaScript 或所选 Gadget 支持的加密脚本。脚本交由 Gadget 加载；加密脚本需使用能解密该格式的 Gadget。可从仓库的 [脚本示例](examples/script/libscript.so) 开始，示例内容是 JavaScript。

## 自定义配置

选择“自定义”，粘贴 JSON 或点击“导入配置文件”。也可用监听／脚本预设替换编辑框内容，再自行修改；监听预设使用当前填写的地址、端口和等待选项。

- 配置须为 UTF-8 JSON 对象，最大 1 MiB，支持二改 Gadget 的额外字段和交互类型。
- 打包时原样保留配置，不改写字段和路径；具体字段由所选 Gadget 解释。
- 如需附带脚本，开启“附带脚本文件”并选择文件。脚本放在 Gadget 同目录的 `libscript.so`，请在 JSON 中按需引用。
- Gadget 和配置分别保存为 `libnpatch-gadget.so`、`libnpatch-gadget.config.so`。二改 Gadget 需支持此命名；配置引用的其他文件需自行准备。

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

自定义配置：

```powershell
java -jar out/wrapper/apkloom-cli.jar example.apk -o output-custom `
  --gadget C:\path\to\custom-gadget.so --gadget-mode custom `
  --gadget-config C:\path\to\config.json
```

按需追加 `--gadget-script C:\path\to\hook.js` 附带脚本。`--gadget-config` 仅用于 `custom` 模式。

[返回使用指南](../README.md)
