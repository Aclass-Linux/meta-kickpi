# 新电脑复现 K7 Rockchip BSP

## 范围与版本

本仓库同时包含 BSP 和产品 layer，不需要拷贝旧电脑的 tmp 或绝对路径配置。
要复现本文的厂商 v5 组合，在构建目录的 `conf/local.conf` 中选择 `K7_BOOT_STACK = "rockchip"`。
若采用机器默认方案，用 `bitbake-getvar K7_BOOT_STACK` 检查生效值。

`build-host-sources.json` 记录当前成功构建使用的 OE-Core、BitBake、meta-openembedded 提交，初始化脚本检出这些配套提交，避免三个构建基础仓库版本不匹配。
RK 内核 develop-6.12 使用 AUTOREV；U-Boot v5 与 rkbin master 在配方中固定当前调试使用的提交。
SPL 默认关闭 A/B，直接运行 `bitbake kickpi-image-minimal`，无需额外的 `-R` 配置。
因此这套步骤复现构建环境和方案，不能保证以后生成逐字节相同的镜像。要追溯本轮固件，保存 deploy 下的 components、Linux commit、DDR 参数记录和镜像校验和。

## 1. 准备 Linux 主机

当前构建主机为 Ubuntu 26.04.1 LTS x86_64。RK boot_merger 是 x86_64 工具，不能直接在 ARM 主机运行。使用普通用户构建；建议预留至少 150 GB 可用空间，并根据内存调整并发。

Ubuntu/Debian 安装基础依赖（其他发行版需要替换包名）：

```bash
sudo apt update
sudo apt install build-essential git python3 python3-pip python3-pexpect \
  gawk wget diffstat unzip texinfo chrpath socat cpio xz-utils zstd \
  debianutils file locales libacl1 libssl-dev libncurses-dev \
  bison flex bc lz4 iputils-ping rsync
sudo locale-gen en_US.UTF-8
export LANG=en_US.UTF-8
```

其他主机版本请依据当前 OE-Core 的 sanity 检查补齐依赖，不关闭检查来掩盖错误。

## 2. 克隆并准备配套仓库

```bash
mkdir -p ~/Yocto/workspace/sources
cd ~/Yocto/workspace/sources
git clone --branch bringup/k7-upstream-boot https://github.com/Aclass-Linux/meta-kickpi.git
python3 meta-kickpi/scripts/prepare-build-host.py
```

脚本将三个基础仓库放到 meta-kickpi 同级目录，并检出记录的提交。已有仓库存在未提交改动时脚本会停止；它会将干净仓库切换到记录提交，勿对正在使用的其他工作区随意运行。

目录应为：

```text
workspace/sources/bitbake
workspace/sources/openembedded-core
workspace/sources/meta-openembedded
workspace/sources/meta-kickpi
```

## 3. 初始化与构建

```bash
cd ~/Yocto/workspace
export TEMPLATECONF="$PWD/sources/meta-kickpi/layers/meta-product-k7/conf/templates/k7-upstream"
source sources/openembedded-core/oe-init-build-env build-k7
unset TEMPLATECONF
bitbake-layers show-layers
bitbake kickpi-image-minimal
```

模板已选择 kickpi-k7、BSP 与产品 layer；不要复制旧电脑的 bblayers.conf。
开发镜像允许 root 空密码，正式产品应另行配置账户。
内存较小时可在 build-k7/conf/local.conf 添加：

```bitbake
BB_NUMBER_THREADS = "4"
PARALLEL_MAKE = "-j4"
```

按需迁移旧 build-k7/downloads 和 sstate-cache 加速，tmp 不必迁移。

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
cd ~/Yocto/workspace
cd build-k7/tmp/deploy/images/kickpi-k7/usb-flash-k7
sha256sum k7-rockchip-loader.bin kickpi-k7-emmc.img
```

WIC 改扩展名为 img，内容仍是完整磁盘镜像，包含 IDB、U-Boot FIT 和根文件系统。loader.bin 是供 USB 下载的临时 Loader，不能用单独 SPL 或 idbloader.img 代替。

将 usb-flash-k7 复制到 Windows，安装板厂提供的 DriverAssistant 和 RKDevTool。烧录会覆盖 eMMC，请先备份所需数据。

1. 拔掉 SD 卡，关闭板子电源，用数据线连接板子的 USB OTG 烧录口与电脑。
2. 按住 RECOVER 再上电，RKDevTool 显示 LOADER 设备后松开；空 eMMC 也可能显示 MASKROM。
3. 在“下载镜像”页选择 eMMC，使用“按地址写入”方式。Loader 专用行选择 k7-rockchip-loader.bin；系统镜像行地址设为 `0x00000000`，选择 kickpi-k7-emmc.img，取消其他分区行。保留工具 Loader 专用行的原生类型设置，不将其当成普通磁盘数据写入。
4. 点击执行并等待成功，再断电重启。完整 WIC 不走“升级固件/update.img”入口。不同 RKDevTool 版本布局可能不同；若没有 Loader 与原始镜像下载入口，先按该版本说明导入对应下载配置。
5. 串口使用 1500000、8N1、无流控，检查 DDR、SPL、U-Boot、Linux 日志。进入系统后执行 `findmnt -no SOURCE /` 与 `lsblk` 核对根设备；MMC 对应 eMMC、SD 对应 SD 卡，可查看 `/sys/block/mmcblk*/device/type`。

当前记录的本机镜像构建已全部成功，USB/eMMC 烧录和板上外设仍需要按实际交付组合验收。
