# KICKPI K7 Hello World

用产品 SDK 在 WSL 上交叉编译一个 C 程序，然后在 K7 上运行。

## 加载 SDK 并编译

在新的 Bash 终端执行。下面使用工作区中已安装的 SDK 路径。若你安装到了其他位置，请替换 `sdk_dir`。

```bash
cd /home/ryan/Yocto/workspace
sdk_dir="$PWD/build-k7/sdk/kickpi-k7"
source "$sdk_dir"/environment-setup-cortexa72-cortexa53-oe-linux
output_dir="$PWD/build-k7/examples/hello-world"
mkdir -p "$output_dir"
cd sources/meta-kickpi/examples/hello-world
# CC 由 SDK 设置，包含交叉编译器和目标 sysroot 参数。
$CC -Wall -Wextra hello.c -o "$output_dir/hello-world"
file "$output_dir/hello-world"
```

`file` 应显示 ELF 64 位、ARM aarch64。

## 在 K7 上运行

如果 K7 开启了 SSH，将下面的示例地址和用户名替换为板子的实际地址和登录用户。

```bash
scp "$output_dir/hello-world" root@192.0.2.10:/tmp/hello-world
```

在 K7 的串口终端或 SSH 会话中执行。

```sh
/tmp/hello-world
```

预期输出为 `Hello, world! From KICKPI K7.`。
