# KICKPI K7 产品 SDK

此 SDK 在 x86_64 Linux 上使用，目标是运行配套产品镜像的 KICKPI K7（AArch64）。Windows 开发机在 WSL2 的 x86_64 Linux 环境中执行以下命令。

## 校验与安装

在新开的 Bash 终端中进入解包目录，执行以下命令。安装器会提示输入安装目录，建议选择 Linux 文件系统下的用户可写路径，例如 `$HOME/k7-sdk/<发布版本>`，并将 `$HOME` 展开为实际路径后输入。

```bash
sha256sum -c SHA256SUMS && ./install.sh
```

`release.json` 记录发布版本、对应镜像和目标架构。`manifests/host.manifest` 列出开发主机工具，`manifests/target.manifest` 列出目标 sysroot 软件包。

## 交叉编译与上板验证

每次开启新终端都要加载 SDK 环境。将下方路径中的版本替换为实际安装版本。

```bash
source "$HOME/k7-sdk/0.1.0-rc.1"/environment-setup-*
$CC -Wall -Wextra examples/hello.c -o k7-sdk-hello
file k7-sdk-hello
```

`CC` 由 SDK 设置，内含编译器和目标 sysroot 参数。`file` 应显示 `ARM aarch64`。把 `k7-sdk-hello` 复制到运行配套产品镜像的 K7，执行 `./k7-sdk-hello`，预期输出 `Hello from KICKPI K7 SDK`。
