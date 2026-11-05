# KICKPI K7：从厂商 Armbian 到 Yocto 的操作手册

当前产品层已拆到同仓库 `layers/meta-product-k7`；当前构建配置和入口以
[产品层说明](product-layer.md)为准。本文历史步骤中的旧布局不作为当前配置模板。

> 当前内核已切换到 [Yocto Linux 6.18](k7-linux-yocto.md)，此文包含旧厂商 6.1 迁移历史。
> 当前已开始主线 U-Boot 迁移。请优先阅读 [K7 主线适配](k7-mainline-uboot.md)。
> 本文的厂商打包命令和补丁状态包含历史记录，不能用于主线镜像烧录。

## 当前交付状态

本分支是 Wrynose 移植开发版本，不是已通过烧录和硬件验收的产品发布。
目标为 K7（不是 K7C）、RK3576、AArch64、Linux 6.1。
保留当前拆分的 OE-Core、BitBake、meta-yocto，未切换传统 Poky 仓库。

厂商输入：`/home/ryan/Yocto/kickpi-armbian`，提交
`55ecd8e5fba9b45a1156db821d445eea0a112dbf`。
逐文件路径与 SHA256 见 `vendor-import.json`。复制的文件保留原版权头。

已接入：

- K7 内核 DTS 和三个摄像头 DTSI；不混入 K7C。
- 以厂商完整内核配置为基线，关闭 FT8756 选项；当前仅启用厂商 MAE0621A PHY 补丁。Sony 手柄及两项触摸屏补丁已移除，详见第 16 节。
- 厂商指定内核分支 rk-6.1-rkr5.1，固定提交 372ce582b94a3360a29727c3c49c88bb7509da22。
- 独立的 U-Boot K7 DTS 和 defconfig；厂商 next-dev-v2024.10 分支固定提交 39cd993e5d6296635438e84f4576b3a9bf76f86e。
- 复用 Rockchip BSP 打包任务和 GPT 镜像类；rkbin 已因缺失 RK3576 调整为候选固定版本，导入厂商 DDR v1.08，详见第 13 节。
- `kickpi-image-minimal` 产品配方，生成 rootfs 和 Rockchip 镜像。

尚需验证或继续移植：

- 厂商分支是移动分支，当前固定的是迁移时查询的 HEAD，并非厂商历史构建锁定清单。
- U-Boot 与 BSP 打包任务组合、rkbin 中 DDR/BL31 版本及启动分区布局尚需构建和真机核对。
- 厂商可选 LCD overlays、外置 Wi-Fi 驱动、蓝牙固件、4G、桌面和 GPU/NPU 用户态组件未完成移植。
- 未直接复制厂商 kickpi-hardware 服务：其调用 sudo、growpart、固定 mmcblk0、未打包的 quectel-CM 和 wifibt-init.sh，不能作为 Yocto 服务直接运行。
- Armbian 首次登录、apt、预置密码和 Debian 文件系统覆盖不属于本最小镜像。

## 1. 主机和源码准备

当前检测到 Fedora 45、glibc 2.44，BitBake 提示主机 glibc 比 uninative 2.43 新，已禁用 uninative。建议使用 Wrynose 官方支持的主机或构建容器；这不是通过缓存清理能解决的问题。主机软件要求：
https://docs.yoctoproject.org/6.0/ref-manual/system-requirements.html

下面命令均在 Bash 中执行。不要在已经初始化 QEMU 的终端继续。

```bash
bash
cd /home/ryan/Yocto/workspace-k7
repo status
```

当前迁移修改保存在 `sources/meta-kickpi` 的 `port/wrynose-armbian` 分支。
不要执行 `repo sync -d` 或强制重置，避免切回 manifest 的旧版 K7。
当前 manifest 仍指向旧提交；新电脑不能仅靠 repo sync 获得这些未提交修改。

## 2. 初始化构建环境

```bash
cd /home/ryan/Yocto/workspace-k7
source sources/openembedded-core/oe-init-build-env build-k7
```

当前工作区 build-k7 已配置 K7、所需 layers、共享缓存和串口 bring-up 的 Xorg 屏蔽规则。新建 build-k7 时再执行一次：

```bash
bitbake-layers add-layer \
  ../sources/meta-openembedded/meta-oe \
  ../sources/meta-rockchip \
  ../sources/meta-kickpi
```

`build-k7/conf/local.conf` 应包含以下设置（编辑原值，不要每次重复追加）：

```bitbake
MACHINE = "kickpi-k7"
DL_DIR = "/home/ryan/Yocto/shared-cache/downloads"
SSTATE_DIR = "/home/ryan/Yocto/shared-cache/sstate-cache"
BB_HASHSERVE_DB_DIR = "${SSTATE_DIR}"
```

最小 bring-up 默认使用 OE-Core 的 nodistro。不要为了使用 meta-poky 就直接更换发行版；
以后更换 DISTRO 或 init system 应使用独立 build 目录验证。
不要复制 QEMU 的 IMAGE_FSTYPES 或 QB_DEFAULT_FSTYPE。
不需要清空 QEMU 缓存，共享 downloads/sstate 即可，禁止共用 tmp。

## 3. 解析和核对实际选项

```bash
bitbake-layers show-layers
bitbake -p
bitbake -e virtual/kernel > k7-kernel.env
rg '^(MACHINE|TARGET_ARCH|PN|PV|SRCREV|SRC_URI|KBUILD_DEFCONFIG|KERNEL_DEVICETREE)=' k7-kernel.env
bitbake -e virtual/bootloader > k7-uboot.env
rg '^(PN|SRCREV|SRCREV_rkbin|SRC_URI|UBOOT_MACHINE)=' k7-uboot.env
```

确认 MACHINE=kickpi-k7、TARGET_ARCH=aarch64、内核为 linux-rockchip 6.1、
DTB 为 rockchip/rk3576-kickpi-k7.dtb，U-Boot 使用 kickpi-k7-rk3576_defconfig。
解析通过只能证明元数据可用，不代表补丁、编译或启动通过。

## 4. 分阶段验证内核和 U-Boot

```bash
bitbake virtual/kernel -c patch
bitbake virtual/kernel -c configure
bitbake virtual/kernel
bitbake virtual/bootloader -c patch
bitbake virtual/bootloader
```

任何一步失败，先解决错误再继续。不要使用 `BB_DANGLINGAPPENDS_WARNONLY`、
忽略补丁失败或修改兼容声明来跳过实际问题。

任务日志位于 tmp/work 下对应配方的 temp/log.do_*，例如：

```bash
find tmp/work -path '*/temp/log.do_*' -type f
```

若 DDR 检查失败，核对 rkbin 固定版本是否含 v1.08；不允许自动换成其他 DDR 文件。
若内核补丁已被上游合并，应核对补丁内容后删除对应 SRC_URI，记录原因。

## 5. 完整镜像构建

```bash
bitbake kickpi-image-minimal
ls -lh tmp/deploy/images/kickpi-k7/
```

镜像类应生成 ext4、wic 和 Rockchip 打包产物，实际文件以部署目录为准。
不能把 rootfs.ext4 当作包含启动链的整盘镜像。
核对必须存在 K7 DTB、Image、idblock.img、uboot.img 和 boot.img；
BSP 镜像类会跳过不存在的可选分区，因此“生成 wic”不等于启动链完整。

```bash
find tmp/deploy/images/kickpi-k7 -maxdepth 1 -type f -o -type l
wic ls tmp/deploy/images/kickpi-k7/kickpi-image-minimal-kickpi-k7.rootfs.wic
```

如果链接文件名不同，使用上一步列出的实际 .wic 路径。

## 6. 真机启动与验收

首次使用可恢复的测试 SD 卡，先核对生成的 GPT 布局是否与 U-Boot 启动参数一致，
串口波特率按机器配置为 1500000。不要先覆盖板载 eMMC。
具体刷写工具、设备路径和 Rockchip Loader 下载流程必须依据板卡的恢复方式确定。
本文不预设 `/dev/sdX` 或提供会误覆盖主机磁盘的自动命令。

收集完整串口输出：DDR → SPL → U-Boot → Kernel → rootfs → 登录。
如果停在 DDR/SPL，先查固件和启动链，重做 rootfs 无法修复它。
进入系统后检查：

```bash
uname -a
uname -m
cat /proc/device-tree/model
cat /proc/cmdline
mount
ip link
dmesg | grep -Ei 'error|fail|mmc|ethernet|firmware'
```

然后分别验收网口、SD/eMMC、USB、Wi-Fi、蓝牙、音频、显示和摄像头。
外设验收尚未通过前，不将该镜像标记为完整厂商功能迁移完成。

## 7. SDK

```bash
bitbake kickpi-image-minimal -c populate_sdk
ls -lh tmp/deploy/sdk/
```

## 8. 固化与同步给新电脑

构建和真机验收后，在 meta-kickpi 仓库检查并提交本次源码、配方和文档，推送开发分支。
再将 manifest 的 boards/kickpi-k7.xml 中 revision 更新为真实提交 SHA，
upstream 改为真实分支，并按实际验证状态更新迁移 annotation。
随后运行 manifest 的 ci/check.sh 并提交推送 manifest。
不要在 K7 尚未验证时移除待迁移标记或写入 releases。

发布完成后，新电脑才执行：

```bash
mkdir -p ~/Yocto/workspace-k7
cd ~/Yocto/workspace-k7
repo init -u https://github.com/Aclass-Linux/SDK-Yocto.git \
  -b main -m products/kickpi-k7/integration.xml
repo sync -c -j4
python3 .repo/manifests/ci/verify-checkout.py --entry products/kickpi-k7/integration.xml
source sources/openembedded-core/oe-init-build-env build-k7
```

然后从第 2 节配置 layers 和 local.conf，按第 3～7 节操作。

## 本次本地验证记录（2026-11-04）

- `git diff --check` 通过，11 个厂商导入文件 SHA256 核对通过。
- `bitbake -p` 通过：2025 个配方，无解析错误。
- 内核、U-Boot 的 `bitbake -e` 通过，确认目标 aarch64、K7 DTB 和固定源码提交。
- `bitbake -n kickpi-image-minimal` 通过：4836 个任务的依赖预演通过。
- 未执行完整源码编译、镜像烧录和真机启动。上述 dry-run 不证明补丁可应用、DDR 文件存在或镜像可启动。

## 9. 必须先理解的 Yocto 基础

### 9.1 谁决定什么

| 概念 | 本项目实例 | 负责什么 |
| --- | --- | --- |
| manifest | products/kickpi-k7/integration.xml | 获取哪些 Git 仓库、固定哪些提交 |
| layer | meta-kickpi | 集中保存板卡和产品定制 |
| recipe | kickpi-image-minimal.bb | 定义一个软件或镜像的构建方式 |
| bbappend | linux-rockchip_6.1.bbappend | 扩展另一个 layer 中已有的配方 |
| MACHINE | kickpi-k7 | CPU、设备树、内核/启动器选择和硬件特性 |
| DISTRO | 当前为 nodistro | init、libc、系统功能和发行策略 |
| image | kickpi-image-minimal | 把指定软件包组装成根文件系统和镜像 |
| SDKMACHINE | 通常 x86_64 | SDK 工具本身运行在哪种电脑上 |
| TARGET_ARCH | aarch64 | 最终程序在哪种 CPU 上运行 |

BUILD_SYS=x86_64-linux、SDKMACHINE=x86_64 不代表编错架构。
检查最终架构应看 TARGET_ARCH、目标 ELF 和板上 uname -m。
MACHINE_FEATURES 声明 wifi 不会自动提供可用 Wi-Fi 驱动、固件和服务。

### 9.2 任务、源码目录和缓存

一般任务顺序是 fetch → unpack → patch → configure → compile → install → package。
内核还包括 kernel_checkout、kernel_metadata、kernel_configme 等专用任务，
不能凭一般顺序把任务插入 do_patch 与 kernel_metadata 之间：本基线 metadata 在 patch 前。
本项目内核的 kickpi_sources 在 kernel_checkout 后、kernel_metadata 前；
U-Boot 的 kickpi_sources 在 patch 后、configure 前。

- WORKDIR：配方工作的总目录。
- UNPACKDIR：下载内容解包、file:// 输入文件所在目录。
- S：实际源码目录，内核通常另有共享源码树。
- B：构建输出目录，可以与 S 不同。
- D：do_install 的临时安装根目录，不是板上根目录。
- DEPLOY_DIR_IMAGE：最终内核、DTB、启动器及镜像部署目录。
- downloads：源文件缓存；sstate-cache：可复用任务结果；tmp：本次构建中间产物。

不要手改 tmp 内的文件作为长期修复；修改源 layer 或 devtool 工作树，最后保存补丁。
配方签名会追踪大多数输入变化，无需每次 cleanall。定位强制重跑任务时才使用 `-f`。

### 9.3 覆盖语法和排错

`VAR:kickpi-k7` 是该机器的值；`:append`/`:prepend`/`:remove` 分别追加、前置、移除。
`=` 设置值，`?=` 仅在未定义时设置，`??=` 提供弱默认值。
不能只读某一个文件猜最终值，用 `bitbake -e 配方` 查看展开值及来源。
Python 任务不能直接追加 shell 语句；本项目为文件安装增加了独立 shell 任务。

## 10. SDK 的配置、使用和验收

### 10.1 先选标准 SDK

标准 SDK 包含交叉编译器、主机工具、目标头文件/库、sysroot 和环境初始化文件。
应用团队一般先用标准 SDK。eSDK 另提供 devtool 和元数据工作流，更适合需修改配方的团队，
其大小、离线源缓存和更新服务器都需要另外设计。不要把“内核 SDK”与用户态 SDK 混为一谈：
编译外部内核模块还需要匹配内核版本、配置、符号及构建产物。

SDK 必须与发给板卡的镜像配套。AArch64 相同仍不足以保证 ABI、glibc、C++ ABI 和共享库一致。
当前 nodistro 生成的工具链名称可能是 aarch64-oe-linux，不能照抄 aarch64-poky-linux。

### 10.2 配置归属与示例

SDKMACHINE 属于主机选择，可放 local.conf。发布时把选择记录到构建配置。
产品 SDK 的包集合和版本策略应进入产品 layer，不依赖个人 local.conf。
以下是可选示例，不是本次已经启用的配置；先用默认 SDK 验证最小程序，再按真实依赖添加：

```bitbake
# local.conf：SDK 安装和运行的主机是 x86_64 Linux。
SDKMACHINE = "x86_64"
```

```bitbake
# 产品镜像配方或专用 .inc 中，待版本发布流程确定后使用。
SDK_VERSION = "1.0.0"
SDKIMAGE_FEATURES = "dev-pkgs dbg-pkgs"
# 需要 CMake/Ninja 的应用团队可启用主机工具。
TOOLCHAIN_HOST_TASK:append = " nativesdk-cmake nativesdk-ninja"
# 按实际应用依赖增加开发包，例如 zlib。
TOOLCHAIN_TARGET_TASK:append = " zlib-dev"
```

不要用 `TOOLCHAIN_HOST_TASK = ...` 覆盖默认交叉工具链包组。
SDKIMAGE_FEATURES 不会让镜像自动装入调试包；IMAGE_FEATURES 与它作用对象不同。
SDK 内加入某个库，也不会自动让运行镜像具有该库：实际业务配方应声明 DEPENDS 和运行依赖，
通过 IMAGE_INSTALL 引入应用，正式镜像和 SDK 同源构建。

```bash
bitbake -e kickpi-image-minimal > k7-image.env
rg '^(SDKMACHINE|SDK_VERSION|SDKIMAGE_FEATURES|TOOLCHAIN_HOST_TASK|TOOLCHAIN_TARGET_TASK|SDK_NAME|TOOLCHAIN_OUTPUTNAME)=' k7-image.env
bitbake -n kickpi-image-minimal -c populate_sdk
bitbake kickpi-image-minimal -c populate_sdk
ls -lh tmp/deploy/sdk/
```

### 10.3 安装和最小交叉编译实验

在新的终端使用 SDK，不与 oe-init-build-env 混用。先在部署目录选定实际安装器文件，
然后执行 `bash 实际安装器.sh -d "$HOME/opt/k7-sdk/版本"`。
安装完成后：

```bash
find "$HOME/opt/k7-sdk" -maxdepth 3 -name 'environment-setup-*'
# source 上一步实际列出的、与本镜像相符的文件；不要同时 source 多个 SDK。
```

加载该文件后：

```bash
printf 'CC=%s\nSYSROOT=%s\n' "$CC" "$SDKTARGETSYSROOT"
$CC -dumpmachine
mkdir -p "$HOME/k7-sdk-smoke"
cd "$HOME/k7-sdk-smoke"
cat > hello.c <<'SRC'
#include <stdio.h>
int main(void) { puts("K7 SDK OK"); return 0; }
SRC
$CC $CFLAGS hello.c $LDFLAGS -o hello-k7
file hello-k7
$READELF -h hello-k7
$READELF -l hello-k7
```

验收：ELF64、Machine 为 AArch64；动态加载器与板上路径一致。
`$CC` 不加整体双引号，因为 SDK 的 CC 通常包含编译选项；路径变量则加双引号。
不要在 x86 主机直接运行 hello-k7，也不要用主机 ldd 分析目标 ELF。
将程序通过已经验证的 SSH、串口传输或可移动介质复制到板上，执行：

```bash
chmod +x ./hello-k7
./hello-k7
```

应输出 `K7 SDK OK`，无缺失库或 GLIBC 版本错误。最小镜像不保证装有 SSH 服务。
如需远程调试，再为开发镜像引入 gdbserver 和 SSH，并单独管理开发凭据。

### 10.4 CMake、eSDK 和内核模块

安装了 nativesdk-cmake 后，source SDK 环境，检查 `command -v cmake` 以及
`OE_CMAKE_TOOLCHAIN_FILE` 的实际值，再配置项目：

```bash
cmake -S . -B build-k7 -DCMAKE_TOOLCHAIN_FILE="$OE_CMAKE_TOOLCHAIN_FILE"
cmake --build build-k7
```

只在 CMake 项目目录使用该命令。确认环境变量存在，不要退回主机编译器。
eSDK 使用 `bitbake kickpi-image-minimal -c populate_sdk_ext`，生成后还要验证安装、devtool、
离线开发和重新生成镜像；当前尚未构建或验收 eSDK。
外部内核模块优先写 Yocto 配方并 `inherit module`，由构建系统使用匹配的内核产物。
需要向外部团队交付内核开发包时，再验证 `kernel-devsrc` 的包内容与模块构建流程；
不要承诺只添加一个包就能在任意主机编译出匹配模块。

SDK 发布同时保存安装器 SHA256、主机支持范围、manifest、镜像版本和目标包清单。
用 `sha256sum tmp/deploy/sdk/实际安装器.sh` 记录校验值；在另一台干净电脑重做安装和 hello 验收。

## 11. U-Boot 启动链设计与迁移

### 11.1 各阶段的责任

概念上的启动链为 BootROM → DDR/早期加载阶段 → SPL/可信固件 → U-Boot → Linux → init。
具体加载关系由厂商 SPL、rkbin 和镜像打包实现决定，不是所有平台都按同一线性顺序执行。

| 组件 | 作用 | 本项目需要保留的证据 |
| --- | --- | --- |
| BootROM | 芯片固化代码，选择介质、加载早期程序 | SoC 启动介质和恢复方式 |
| DDR 初始化二进制 | 初始化 DRAM | K7 DDR 类型、频率、文件版本、来源和校验值 |
| SPL / loader | 建立早期环境、加载后续组件 | 源码提交、配置、串口日志、打包方式 |
| BL31 | ARM 可信固件 EL3 运行时 | rkbin 固定版本及实际打包的文件 |
| BL32 | 可选可信 OS | 只有实际选用时才纳入版本和验证 |
| U-Boot proper | 选择启动项、加载内核/DTB、传递启动参数 | defconfig、环境、镜像格式、分区协议 |
| Linux DTB | 描述内核使用的硬件 | K7 DTS 和引用文件、实际加载路径 |

Linux 和 U-Boot 的 DTS 属于不同源码树，不能因为名字相同就互相替换。
`uboot.img`、`u-boot.itb`、`idblock.img`、`idbloader.img` 也不是可任意改名互换的文件。
先核对文件内容、打包工具和读取偏移，再决定烧写位置。

### 11.2 当前策略和不能混用的两种路线

本次先选厂商 U-Boot 源码和 K7 defconfig，以降低硬件初始化差异；
继续复用 meta-rockchip 的构建包装与 GPT 镜像类。这是待验证的适配组合，不是厂商原构建的等价复现。
厂商使用 `spl-blobs`，并用 boot_merger 处理 RK3576 DDR/SPL；
BSP 使用自己的 make.sh 及 idblock/loader 打包路径。两者必须通过产物和串口日志对照。

路线 A：沿用 BSP 的 boot.img、uboot.img、GPT 与 parameter 协议，核对厂商 U-Boot 是否支持。
路线 B：完整保留厂商启动脚本、其分区和 loader 打包协议，用独立 Yocto 任务复现。
不能使用 A 的分区表、B 的偏移和第三套 root= 参数拼成一个镜像。
当前尚未完成此组合的实际启动验证，因此也没有确定可量产的 SD/eMMC 烧写布局。

不建议迁移初期同时切主线 U-Boot、主线内核和启动镜像协议。
后续若切主线，应单独开分支，先核对 RK3576/K7 上游支持再设计。
官方 Rockchip 文档讲的是相应上游版本，不能直接当作本厂商分支的烧写手册。

### 11.3 按顺序迁移和验证

1. 固定 U-Boot、rkbin、厂商框架和内核提交，保留它们的许可证。
2. 对比厂商 K7 defconfig 与 BSP 原 rk3576_defconfig，解释 DDR、串口、MMC、FIT/AVB 等关键差异。
3. 导入 U-Boot K7 DTS，检查 rk3576.dtsi、rk3576-u-boot.dtsi 和绑定头文件是否存在。
4. 检查继承的 BSP 补丁是否适用于厂商分支；已合并补丁应剔除，而非忽略失败。
5. 运行 patch、configure、compile，记录实际 .config 与日志。
6. 核对 make.sh、Python 工具和 rkbin 布局；构建出来再比对厂商 loader 的打包协议。
7. 验证分区、启动命令、rootfs UUID 和内核参数，最后使用测试介质启动。

```bash
bitbake -e virtual/bootloader > k7-uboot.env
rg '^(S|B|UNPACKDIR|SRC_URI|SRCREV|SRCREV_rkbin|UBOOT_MACHINE|UBOOT_BINARY|RK_UBOOT_SPL)=' k7-uboot.env
bitbake virtual/bootloader -c patch
bitbake virtual/bootloader -c configure
bitbake virtual/bootloader
```

从展开值取得真实 S/B 路径后，检查 B/.config、S/make.sh、S/arch/arm/dts、
UNPACKDIR/rkbin/RKBOOT/RK3576MINIALL.ini 和实际 DDR 文件。
不要依赖内核版本或目录名猜路径。BSP 配方的 PV 不一定对应换源后的真实 U-Boot 版本，
以源码 Makefile、编译输出和板上 version 为证据；发布前应修正版本标识。

### 11.4 环境变量、分区和 rootfs 的契约

上电时中断自动启动，在 U-Boot 中先做只读检查：

```text
version
printenv bootcmd bootargs boot_targets
mmc list
```

根据 mmc list 选择实际测试卡，再用该版本支持的 `part list mmc N` 检查分区。
不要先 saveenv：旧环境可能覆盖新默认值，错误保存会增加排错变量。
若需要恢复默认环境，也应先备份 printenv 并确认恢复方案，再执行写操作。

必须核对：

- bootcmd 是走 boot_android、FIT、boot.scr 还是 extlinux，相关命令是否编入。
- 内核加载的 DTB 是 K7，而不是 EVB；早期 U-Boot DTB 与 Linux DTB 是否各自正确。
- boot.img 是否包含它需要的内核、DTB、ramdisk，U-Boot 能否识别该格式。
- root= 使用 PARTUUID、UUID 还是设备名；PARTUUID 与文件系统 UUID 含义不同。
- 根分区名称和 UUID 与 WKS、parameter、实际 GPT 相符。
- console= 的设备名和波特率正确，rootwait/文件系统驱动满足根分区挂载。
- 环境保存区域、引导器区域与 GPT/文件系统不重叠。

新板优先收集默认启动失败现场，再改一个变量重试；不要同时改 root=、DTB 和 loader。

### 11.5 验收与量产扩展

先验证冷启动、热重启、断电恢复、SD/eMMC 启动和恢复模式，再讨论 A/B、签名和防回滚。
生产设计还需定义：启动介质优先级、环境冗余、升级原子性、失败重试次数、回滚确认点、
签名密钥存放和轮换、烧录恢复工具。当前工程没有实现这些机制，不能仅打开 FIT/AVB 配置就宣称安全启动完成。

## 12. 未完成内容的实施与验证矩阵

下面“待验证”不是“已通过”。先解决启动链和基础镜像，再按硬件功能逐项增加配方。
没有硬件时可做源码、依赖和编译验证，但不能签署真机验收。

| 项目 | 当前状态 | 后续实施与验证 | 通过条件/证据 |
| --- | --- | --- | --- |
| 内核四个厂商补丁 | 已接入，未实测应用 | 执行 kernel patch/configure/compile，核对实际 .config | 无补丁拒绝，Image/DTB 成功部署 |
| 内核 K7 DTB | 文件已导入 | 在固定内核源码编译 DTB，反编译核对 model/compatible | 无致命 dtc 错误，板上 model 正确 |
| U-Boot BSP 补丁 | 已发现并剔除重复 Python3 补丁 | 剩余两项已在固定源码顺序应用，后续仍需 Yocto patch 任务 | patch 日志和输入 SHA 一致 |
| U-Boot defconfig | 独立源码测试通过 | 用 Yocto configure/compile 再验证 | .config 和产物匹配 K7 |
| U-Boot DTS | 独立 cpp/dtc 测试通过，有警告 | 处理或评估 dtc 警告，实际 U-Boot 构建 | DTB、串口、MMC 正常 |
| DDR / rkbin | 原固定提交缺 RK3576，已更换候选并导入厂商 DDR | 检查每个 ini 引用文件、SHA、许可证、boot_merger 输出 | loader 可启动、DDR 容量/频率正确、冷启动稳定 |
| BL31 | 使用候选 rkbin，未执行打包 | 核对 RKTRUST 配置引用与真实文件，检查打包日志 | 无丢失文件，EL3/PSCI 正常 |
| GPT / WIC | 沿用 BSP 布局，未生成实镜像 | wic ls、sgdisk -p 实际镜像，与 bootcmd/root= 对照 | 各区域不重叠、根分区定位一致 |
| SD/eMMC | 待真机 | 分别启动、读写临时文件、重启后核对 | 无 MMC 错误和文件损坏 |
| 网口 | 待真机 | ip link、协商速率、DHCP/静态网络、持续传输 | MAC 稳定，重启正常，传输无异常 |
| Wi-Fi | 未完成驱动/固件配套 | 识别实际芯片，匹配内核配置与固件包；检查 dmesg、iw、连接和重连 | 无 firmware missing，可稳定连接 |
| 蓝牙 | 未完成 UART/固件/服务配套 | 核对 K7 UART、rfkill、电源时序，按驱动决定 hciattach/serdev | hci 设备存在，可扫描、配对及重连 |
| 4G | 未移植 quectel-CM 等 | 先确认模块型号和 USB 驱动，再选择 ModemManager 或专用配方 | SIM/APN/拨号/断线恢复正确 |
| 音频 | 未迁移 UCM 和命名规则 | alsa-utils 做播放/录音，分别核对 codec/HDMI/DP | 路由正确，无爆音和错误声卡映射 |
| 显示/触摸 | 未迁移 LCD overlays | 一种面板一个验证配置，核对供电、背光、触摸坐标 | 显示稳定，触摸方向及休眠恢复正确 |
| 摄像头 | DTSI 已导入，驱动/用户态未验收 | 核对传感器供电、I2C、media graph、ISP 用户态版本 | v4l2/media 节点正确，可持续采集 |
| GPU/NPU | 未迁移用户态组件 | 检查驱动 ABI、库许可证和固件版本，单独打包 | 实际加速运行，不能以软件渲染通过代替 |
| 板级 systemd 服务 | 未迁移厂商服务 | 拆分 Wi-Fi、蓝牙、4G 等服务，准确声明依赖；移除 sudo 和固定磁盘扩容 | 失败可诊断，无破坏性分区操作 |
| SDK | 默认变量核对通过，依赖预演失败 | 解决 AUTOREV 网络解析，再构建、安装、编译 hello 并板上运行 | 安装器、ELF、板上输出和版本全部留档 |
| eSDK | 未实现和验证交付流程 | 实测 populate_sdk_ext、devtool、离线使用 | 干净主机可复现开发流程 |
| 外部内核模块 | 未实现产品配方 | inherit module，使用匹配内核编译并板上加载 | vermagic 匹配，加载/卸载及功能正常 |
| A/B、签名、防回滚 | 未设计实施 | 独立设计启动确认、失败回滚和密钥流程，故障注入 | 断电/坏包测试通过，密钥不进源码仓库 |

### 12.1 每次验证留什么

每项记录日期、操作者、板卡版本/内存、输入 manifest 和未提交差异、命令、退出码、
完整日志、产物 SHA256、结论及剩余问题。失败也保存，不用新日志覆盖旧日志。
软件版本与硬件版本变化后，重新评估相关测试，不能沿用不对应的旧结论。

建议在工作区 `artifacts/kickpi-k7/验证批次/` 保存构建日志和产物清单；
板卡层 docs 保存简要结论和方法，大型镜像和 SDK 存制品库。

### 12.2 发布前的门槛

至少完成：干净工作区构建镜像、内核和启动链冷启动、基本存储/网络、SDK 干净主机安装及
hello 板上运行、来源/许可证审查、manifest 固定提交和恢复流程。
产品承诺的外设必须逐项验收。SDK 和镜像同版本发布，不能只保存成功截图。

## 13. 第二轮实际验证及发现（2026-11-04）

这一节更新前面的初次 dry-run 记录。前面的通过记录对应更早的代码状态，不能当作本轮修改后的完整构建结果。

1. 默认 SDK 展开值已核对：SDKMACHINE=x86_64、SDK_VERSION=nodistro.0，
   SDKIMAGE_FEATURES 包含 dev-pkgs/dbg-pkgs/src-pkgs，默认交叉工具链包组存在。
   尚未启用示例中的 CMake/Ninja/zlib 定制。
2. 执行 SDK 依赖预演时，meta-rockchip 的 `xserver-xorg_%.bbappend` 使用 AUTOREV，
   解析阶段触发对 `https://github.com/JeffyCN/xorg-xserver` 的 ls-remote，长时间无响应。
   已终止该查询，预演以失败结束；这不是 SDK 构建通过。
   即使最小镜像不安装 Xorg，解析其他配方也可能触发它。
   正式解决应选取并验证该源码的固定 SHA，在自有 layer 覆盖；不能只固定 manifest 就宣称所有源码可复现。
3. 固定 U-Boot 源码已下载做离线核对。BSP 的 Python3/dtoc 补丁检测为已存在，已从 K7 的输入中剔除。
   其余两项补丁按顺序实际应用成功，未使用反向应用或忽略错误。
4. 导入 K7 defconfig 后，执行独立源码 make defconfig 成功，确认 RK3576、K7 DT 名、SPL_ATF 和 boot_android 配置。
   主机 GCC 发出旧 Kconfig 代码警告；这不是 U-Boot 全量交叉编译通过。
5. K7 U-Boot DTS 的 cpp 预处理和 dtc 编译成功，生成 DTB，存在 alias 等警告，仍需评估。
   没有据此宣称 Linux DTS 已编译或启动成功。
6. GitHub 树接口核对显示旧 rkbin 提交完全没有 RK3576 文件，原检查路径确实不可用。
   K7 现改用 rockchip-linux/rkbin 固定提交 `3e288fe814e059dd06833495f845cab04ac20a5c`，
   并从厂商框架导入 DDR v1.08。候选 RK3576MINIALL.ini 已获取，引用了新版本 USB plug、boost、SPL；
   尚未下载并核对整套固件，也未验证该组合与厂商 BL31 v1.04 的等价性。
   因此这是修复缺失依赖的候选组合，仍需整链验证，不能标记为厂商原版复现。
7. 未执行完整 SDK 安装、交叉编译 hello、内核补丁应用/编译、loader 打包、烧录和真机验收。

8. 本轮 U-Boot 元数据检查通过：确认只保留两个 BSP 补丁、厂商源码和新 rkbin 提交，所有本地输入存在；
   为避开已知 Xorg AUTOREV 网络阻塞，仅该次检查使用 /tmp 下临时 BBMASK 排除了 Xorg 配方。
   正式 local.conf 没有加入此屏蔽，故该检查不等于正式全量解析或 SDK 预演通过。
9. 12 个厂商输入文件 SHA256 核对通过，文档代码围栏及 git diff --check 通过。

独立源码验证的日志归档于 `docs/validation/2026-11-04/`。
临时源码在 `/tmp/k7-source-audit`，可能被系统清理，不是正式依赖来源。
对二进制 DDR 的再分发授权仍应结合厂商授权和上游许可证审查；文件来源校验不等同于授权结论。

## 14. 官方参考

- [Yocto 6.0 SDK 概念](https://docs.yoctoproject.org/6.0/sdk-manual/intro.html)
- [Yocto 6.0 SDK 交叉编译操作](https://docs.yoctoproject.org/6.0/sdk-manual/working-projects.html)
- [U-Boot 上游 Rockchip 文档](https://docs.u-boot.org/en/latest/board/rockchip/rockchip.html)

版本说明：SDK 操作以本仓库 Wrynose 6.0 为基线；U-Boot 链接为上游 latest 文档，
硬件支持、命令和打包方式应再对照本项目固定的厂商源码，不能跨版本直接套用。


## 15. Goodix 补丁上下文修复（2026-11-05）

问题：Linux 6.1 do_patch 在 002-panel-simple-gt9xx-fix-tp-power-from-panel-power.patch 失败。
固定内核的 goodix.h 已增加 timer/workqueue 头文件和结构成员，goodix_ts_remove 已增加
无 IRQ 时的清理逻辑；厂商补丁仍匹配旧版上下文。补丁并非已经整体合并。

处理：保持内核固定提交，刷新该补丁四个文件的 diff，保留新源码的定时器、工作队列和
IRQ 清理，只迁入原补丁的 panel notifier 改动。保留原作者，并记录适用基线及 Upstream-Status。
vendor-import.json 同时记录厂商原始校验值 vendor_sha256 和本地刷新后的 sha256。
不需要删除下载或 sstate 缓存，也不需要跳过补丁。

该修复只验证补丁应用，尚不能说明触摸屏电源时序、模块链接和休眠唤醒功能已验收。
继续构建时使用当前 build-k7：

```bash
cd /home/ryan/Yocto/workspace-k7
source sources/openembedded-core/oe-init-build-env build-k7
bitbake virtual/kernel -c patch
bitbake virtual/kernel -c configure
bitbake virtual/kernel
```

验证结果：本次 `bitbake virtual/kernel -c patch` 实际执行成功，退出码 0，
包括该补丁在内的完整补丁序列通过；290 项任务检查通过（282 项复用）。
任务日志见 `docs/validation/2026-11-05/linux-do-patch.log`（从仓库根目录定位）。
尚未执行本轮 configure/compile。


## 16. 串口 bring-up 阶段裁剪（2026-11-05）

本节是当前状态，覆盖第 15 节及此前“四个厂商补丁”的历史记录。

| 内容 | 处理 | 原因 |
| --- | --- | --- |
| 001-hid-sony.patch | 移除配方引用并删除 Yocto 副本 | Sony DS4 手柄与串口启动无关 |
| 002-panel-simple-gt9xx-fix-tp-power-from-panel-power.patch | 移除配方引用并删除 Yocto 副本 | 当前不使用屏幕及触摸电源联动 |
| 003-touchscreen-ft8756-add-ft8756-driver.patch | 移除配方引用并删除 Yocto 副本 | 当前不使用 FT8756，同时关闭 defconfig 中的选项 |
| 004-add-PHY-for-MAE0621A-Q2C.patch | 保留 | 支持网口 PHY，基础网络可能依赖它 |
| BSP 根文件系统挂载补丁 | 保留 | 涉及启动和根文件系统挂载 |
| BSP 两项 DRM 显示补丁 | 暂时沿用 BSP | 当前不是必需功能，但尚未单独裁剪和验证，不将其当作显示验收通过 |
| 摄像头 DTSI | 保留 | K7 DTS 直接引用，不应仅删除文件造成 DT 编译失败 |
| DDR、BL31、U-Boot 板级配置 | 保留 | 属于基础启动链 |

厂商原始目录 kickpi-armbian 未修改；未来恢复可选驱动时从固定厂商提交重新评估。
被移除文件的来源、校验值与原因保存在 vendor-import.json 的 excluded_files 中。
当前仍使用较完整的厂商 defconfig，这次不是最小内核裁剪；已有 Sony HID 等基础驱动
可能仍能编译，只是不再应用厂商附加补丁。显示、音频、摄像头、Wi-Fi 等进一步裁剪应
通过独立配置实验和硬件验收进行，不能只删 MACHINE_FEATURES 就认为驱动已被关闭。

修改 SRC_URI 后正常运行 bitbake 即会重新计算任务输入，无需删除 downloads/sstate。
后续依次验证 patch、configure、内核和镜像；移除补丁不代表整板功能已通过验收。

本轮实际验证：裁剪后的 `bitbake virtual/kernel -c patch` 成功，退出码 0；
290 项任务检查通过（281 项复用）。日志为 docs/validation/2026-11-05/linux-do-patch-pruned.log。
活动导入文件校验值、已删除补丁引用检查和 git diff --check 均通过。
本轮没有执行 configure、compile 或真机验证。


## 17. 第二轮补丁精简（2026-11-05）

本节覆盖前文保留“根文件系统兜底/两项 DRM/U-Boot 取消 Werror”的历史状态。

- 关闭 K7 内核的 BSP auto-patch，当前内核仅应用显式选择的 MAE0621A PHY 补丁。
  不修改上游 meta-rockchip，也不影响其他 MACHINE。
- 不再应用根文件系统类型兜底补丁：产品 RK_ROOTFS_TYPE 明确设为 ext4，厂商配置中 CONFIG_EXT4_FS=y。
  实际启动仍必须核对 root=、rootfstype=ext4、分区 UUID 和存储驱动，不能把“生成 ext4”当作启动参数已验证。
- 不再应用两项 DRM 显示补丁：串口阶段无需 cursor update 和 overlay plane 注册顺序定制。
- 移除 U-Boot 取消 -Werror 的补丁及 Yocto 副本，恢复厂商原有严格编译行为。
  若旧源码被新编译器诊断为错误，应针对具体警告修复，不自动重新关闭 Werror。
- 路径映射补丁仍保留：只有一条编译选项，处理 __FILE__ 的绝对路径，属于构建质量定制，非硬件必需。
  基于构建路径归一化的作用暂时保留；未来若不需要这项功能可以单独去掉并验证。
- 网口 PHY 补丁保留：对固定内核 372ce582b94a 的原始 Git 树检查，drivers/net/phy 中没有
  maxio 驱动、MAE0621 名称或 0x7b744411/0x7b744412 匹配。不能因 MAC/stmmac 支持就认为对应 PHY 已有专用支持。
  网上有新驱动提交讨论不等于当前 6.1 固定源码已集成；若未来回移上游驱动，应逐项比较 PHY 型号、时钟和恢复逻辑。

当前预期补丁集合：内核 1 项（Maxio PHY）；U-Boot 1 项（__FILE__ 路径映射）。


本轮验证结果：

- 内核 do_patch 成功，任务日志确认仅有 `(1/1) 004-add-PHY-for-MAE0621A-Q2C.patch`。
- 恢复 -Werror 后 U-Boot patch/configure 通过，但实际 compile 失败。
  GCC 15.3 报 drivers/power/charge_animation.c:791 的 soc、current 可能未初始化
  （-Werror=maybe-uninitialized）。未恢复关闭 Werror 的补丁，未通过其他参数掩盖警告。
- 因此当前 U-Boot 重构建有明确阻塞。后续需要对充电动画代码路径作修复，或在确认产品
  不使用该功能后单独裁剪；不能把之前的 U-Boot 构建成功结论沿用到此版本。
- 内核 configure/compile、ext4 启动参数和真机启动未在本轮验证。
