# 新电脑复现 K7 Rockchip BSP

## 范围与版本

本仓库同时包含 BSP 和产品 layer，不需要拷贝旧电脑的 tmp 或绝对路径配置。
要复现本文的厂商 v5 组合，在构建目录的 `conf/local.conf` 中选择 `K7_BOOT_STACK = "rockchip"`。
若采用机器默认方案，用 `bitbake-getvar K7_BOOT_STACK` 检查生效值。

`build-host-sources.json` 固定 Scarthgap 的 OE-Core、BitBake 2.8、meta-openembedded 和提供 Poky 的 meta-yocto 版本。
开发构建默认跟踪 RK 内核 develop-6.12。U-Boot v5 与 rkbin 在配方中固定提交。发布时通过 `K7_KERNEL_SRCREV` 固定已验收的内核提交。
SPL 默认关闭 A/B，直接运行 `bitbake kickpi-image-minimal`，无需额外的 `-R` 配置。
因此这套步骤复现构建环境和方案，不能保证以后生成逐字节相同的镜像。要追溯本轮固件，保存 deploy 下的 components、Linux commit、DDR 参数记录和镜像校验和。

## 1. 准备 Linux 主机

推荐使用 Ubuntu 24.04 LTS x86_64。Ubuntu 26.04 的 WSL 2 已用于本项目的产品镜像构建，Scarthgap 5.0.21 未验证该主机环境。RK boot_merger 需要 x86_64 构建主机。用普通用户构建，预留至少 150 GB 可用空间，并根据内存调整并发。 [Scarthgap 主机要求](https://docs.yoctoproject.org/5.0.21/ref-manual/system-requirements.html#supported-linux-distributions)

Ubuntu/Debian 安装基础依赖（其他发行版需要替换包名）：

```bash
sudo apt update
sudo apt install build-essential git python3 python3-pip python3-pexpect \
  gawk wget diffstat unzip texinfo chrpath socat cpio xz-utils zstd \
  debianutils file locales libacl1 libssl-dev libncurses-dev \
  python3-git python3-jinja2 python3-subunit \
  bison flex bc lz4 iputils-ping rsync
sudo locale-gen en_US.UTF-8
export LANG=en_US.UTF-8
```

其他主机版本请依据当前 OE-Core 的 sanity 检查补齐依赖，不关闭检查来掩盖错误。

## 2. 克隆并准备配套仓库

```bash
mkdir -p ~/Yocto/workspace-scarthgap/sources
cd ~/Yocto/workspace-scarthgap/sources
git clone --branch k7-scarthgap-local https://github.com/Aclass-Linux/meta-kickpi.git
python3 meta-kickpi/scripts/prepare-build-host.py
```

脚本将四个基础仓库放到 meta-kickpi 同级目录，并检出记录的提交。已有仓库存在未提交改动时脚本会停止；它会将干净仓库切换到记录提交，勿对正在使用的其他工作区随意运行。

目录应为：

```text
workspace-scarthgap/sources/bitbake
workspace-scarthgap/sources/openembedded-core
workspace-scarthgap/sources/meta-openembedded
workspace-scarthgap/sources/meta-yocto
workspace-scarthgap/sources/meta-kickpi
```

## 3. 初始化与构建

```bash
cd ~/Yocto/workspace-scarthgap
export PATH="$PWD/sources/bitbake/bin:$PATH"
export TEMPLATECONF="$PWD/sources/meta-kickpi/layers/meta-product-k7/conf/templates/k7-upstream"
source sources/openembedded-core/oe-init-build-env build-k7
unset TEMPLATECONF
bitbake-layers show-layers
bitbake -p
bitbake kickpi-image-product
bitbake kickpi-image-product -c populate_sdk
```

模板选择 kickpi-k7、Poky、systemd、IPK 和 K7 两个层。SD/eMMC 板级验证可单独构建 `kickpi-image-minimal`。
BSP 验证模板允许 root 空密码。产品镜像的学习用配置与发布覆盖方式见 [K7 产品层说明](product-layer.md#发布配置)。
内存较小时可在 build-k7/conf/local.conf 添加：

```bitbake
BB_NUMBER_THREADS = "4"
PARALLEL_MAKE = "-j4"
```

按需迁移旧 build-k7/downloads 和 sstate-cache 加速，tmp 不必迁移。

## 可选的 iceoryx2 验证

Scarthgap 的 repo 清单会将 `meta-iceoryx2` 和提供 `cargo_bin` 的 `meta-rust-bin`
同步到 `sources/`。通过手工克隆初始化的工作区可从工作区根目录运行
`python3 sources/meta-kickpi/scripts/prepare-build-host.py --with-iceoryx2`
获取相同的固定版本。官方 iceoryx2 C 配方为 0.10.0，Rust 层固定在提供 Rust 1.91.1 的提交。
在已初始化的构建环境中启用这两个层，再单独编译配方。

```bash
bitbake-layers add-layer ../sources/meta-rust-bin
bitbake-layers add-layer ../sources/meta-iceoryx2
bitbake iceoryx2-c
```

库编译、镜像和 SDK 装包以及上板通信待验证。

## 4. 需要代理时

在新电脑自行启动可用的 HTTP 代理。端口以实际设置为准，127.0.0.1 指构建所在主机或容器自身。在初始化构建之前执行：

```bash
export http_proxy=http://127.0.0.1:7897
export https_proxy=http://127.0.0.1:7897
export HTTP_PROXY="$http_proxy"
export HTTPS_PROXY="$https_proxy"
export BB_ENV_PASSTHROUGH_ADDITIONS="${BB_ENV_PASSTHROUGH_ADDITIONS:+$BB_ENV_PASSTHROUGH_ADDITIONS }http_proxy https_proxy HTTP_PROXY HTTPS_PROXY"
```

也可在 local.conf 使用普通 BitBake 变量赋值设置代理。不要在配方里动态探测代理或注入时间；不要提交个人代理认证信息。HTTPS 仓库地址不等于自动经过代理。

## 5. 产物与 USB 烧录

只有 BitBake 最终报告全部任务成功，才使用本轮产物：

```bash
cd ~/Yocto/workspace-scarthgap
cd build-k7/tmp/deploy/images/kickpi-k7/usb-flash-k7-product
sha256sum k7-rockchip-loader.bin kickpi-k7-emmc.img
```

WIC 改扩展名为 img，内容仍是完整磁盘镜像，包含 IDB、U-Boot FIT 和根文件系统。loader.bin 是供 USB 下载的临时 Loader，不能用单独 SPL 或 idbloader.img 代替。

将 usb-flash-k7-product 复制到 Windows，安装板厂提供的 DriverAssistant 和 RKDevTool。烧录会覆盖 eMMC，请先备份所需数据。

1. 拔掉 SD 卡，关闭板子电源，用数据线连接板子的 USB OTG 烧录口与电脑。
2. 按住 RECOVER 再上电，RKDevTool 显示 LOADER 设备后松开；空 eMMC 也可能显示 MASKROM。
3. 在“下载镜像”页选择 eMMC，使用“按地址写入”方式。Loader 专用行选择 k7-rockchip-loader.bin；系统镜像行地址设为 `0x00000000`，选择 kickpi-k7-emmc.img，取消其他分区行。保留工具 Loader 专用行的原生类型设置，不将其当成普通磁盘数据写入。
4. 点击执行并等待成功，再断电重启。完整 WIC 不走“升级固件/update.img”入口。不同 RKDevTool 版本布局可能不同；若没有 Loader 与原始镜像下载入口，先按该版本说明导入对应下载配置。
5. 串口使用 1500000、8N1、无流控，检查 DDR、SPL、U-Boot、Linux 日志。进入系统后执行 `findmnt -no SOURCE /` 与 `lsblk` 核对根设备；MMC 对应 eMMC、SD 对应 SD 卡，可查看 `/sys/block/mmcblk*/device/type`。

产品镜像已完成 Scarthgap 组盘。SDK 安装器输出到 `build-k7/tmp/deploy/sdk/`。SDK 安装和交叉编译、USB/eMMC 烧录及板上外设仍待验收。
