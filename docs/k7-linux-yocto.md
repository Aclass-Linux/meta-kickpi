# K7：Yocto 维护内核 6.18 的切换与验证

这是当前 Linux 构建入口。旧厂商 Linux 6.1 文档仅作硬件和迁移历史参考。

## 当前选型

- 构建基线：现有 OE-Core Wrynose 固定提交。
- 内核配方：OE-Core 的 linux-yocto_6.18.bb，版本 6.18.52。
- 内核分支：v6.18/standard/base，固定提交 364c4b44ab2dcc7dfff16076522b62827bcb0e7d。
- yocto-kernel-cache：yocto-6.18，固定提交 2184786cc3deed04926e1cca6c320ed9314da9da。
- 不把 revision 改成移动 HEAD，不继续下载 armbian/linux-rockchip 作为当前内核。
- U-Boot 保持已编译的官方 2026.10，自有 K7 配置和固定 DDR/BL31 组合。

linux-yocto 是 Yocto 维护的 Linux 树，不等同于未经任何修改的 Linus 主线。
本次保留 OE-Core 选定的源码和安全修复基线，只为 K7 增加兼容条件、配置与设备树。

## 已修改的配置边界

1. kickpi-k7.conf 直接使用 OE-Core 的 CPU tune，不再 require 厂商 rk3576.inc。
2. PREFERRED_PROVIDER_virtual/kernel=linux-yocto，PREFERRED_VERSION_linux-yocto=6.18%。
3. meta-kickpi 仅依赖 core；build-k7 的 BBLAYERS 不再加入 JeffyCN/meta-rockchip。
4. linux-libc-headers 恢复 OE-Core 版本，不再强制厂商 6.1-custom 头文件。
5. 两个旧厂商 .bbappend 移到 docs/legacy/vendor-bsp/*.disabled，避免缺少原配方时 dangling append。
6. 旧 DTS、defconfig 和 PHY 补丁副本暂留作参考，当前 SRC_URI 不引用它们。
7. 串口从厂商 ttyFIQ0 改为上游 UART0 的 ttyS0；getty 与 extlinux 同步修改，波特率 1500000。
8. 当前 manifest 仍同步厂商 BSP 源码，但它不在活动 BBLAYERS 中；待本轮提交发布后再调整 manifest。

## 新设备树与内核配置

输入在 recipes-kernel/linux/files/yocto-k7/，不复用厂商 6.1 的绑定和全量配置。

- DTS 使用此 6.18 树自带的 rk3576.dtsi。
- 基础串口和存储拓扑参考厂商 K7 及已经接入的主线 U-Boot K7 DTS。
- eMMC 8-bit/52MHz，SD 4-bit/50MHz；不启用 UHS/HS200/HS400 电压切换。
- SD 明确使用 K7 的 CLK/CMD/CD/DATA 引脚，不套用 generic 额外的 PWREN 引脚。
- 内核配置覆盖 ARM64/RK3576、PSCI、GIC、时钟/引脚/复位/电源域、UART、MMC、GPT、ext4，以及 systemd 基础功能。
- UART、根分区存储和 ext4 必须内建（=y），本方案不依赖 initramfs 提供这些驱动。
- 暂不开启 CPU DVFS，不随意套用其他板子的 PMIC 电压配置。

PMIC/供电仍是首轮真机风险点：最小设备树沿用早期固件已经建立的电源轨，不代表
K7 电源管理和休眠已完成。需要按原理图核对 RK806、SD/eMMC 电源，以及冷启动行为。

当前没有迁移显示、摄像头、音频、Wi-Fi、蓝牙和 NPU；未启用 K7 的 GMAC/Maxio 适配。
此 Yocto 固定内核源码没有 Maxio PHY 专用驱动，不能把旧 6.1 补丁未经审核直接带到 6.18。
因此首轮通过标准是串口/存储/用户态，网口应在后续单独移植和验收。

## 操作步骤

```bash
bash
cd /home/ryan/Yocto/workspace-k7
source sources/openembedded-core/oe-init-build-env build-k7
bitbake-layers show-layers
bitbake -e virtual/kernel > k7-linux-yocto.env
rg '^(PN|PV|KMACHINE|KBRANCH|SRCREV_machine|SRCREV_meta|KERNEL_DEVICETREE|SERIAL_CONSOLES)=' k7-linux-yocto.env
bitbake virtual/kernel -c configure
bitbake virtual/kernel
bitbake kickpi-image-minimal
```

切换内核会重新配置和编译相关依赖，这是正常现象。不需要清空下载和 sstate 缓存。
本轮复用了本机 QEMU 已下载的相同官方内核 Git 对象，未修改 QEMU 的配置和源码。
构建目录仍为 build-k7；若需要保留旧分支的独立构建状态，应使用另一个 build 目录。

## 检查项

从 bitbake -e 输出获得实际 B 路径，再检查 .config。必须确认：

```text
CONFIG_ARCH_ROCKCHIP=y
CONFIG_CLK_RK3576=y
CONFIG_PINCTRL_ROCKCHIP=y
CONFIG_SERIAL_8250_CONSOLE=y
CONFIG_SERIAL_8250_DW=y
CONFIG_MMC_DW_ROCKCHIP=y
CONFIG_MMC_SDHCI_OF_DWCMSHC=y
CONFIG_EXT4_FS=y
CONFIG_EFI_PARTITION=y
CONFIG_DEVTMPFS=y
```

镜像仍使用主线 U-Boot 的 GPT/WIC/extlinux 布局，根分区 PARTUUID 与 WKS 保持一致。
boot/extlinux/extlinux.conf 应为 earlycon console=ttyS0,1500000，而非 ttyFIQ0。
检查部署目录的内核版本和文件时间，不能误用残留的厂商 6.1 Image/DTB。

首次启动使用可恢复的测试 SD 卡，记录 DDR → SPL → U-Boot → Linux 6.18 → rootfs → getty 全部输出。
板上执行 uname -a、cat /proc/device-tree/model、cat /proc/cmdline、mount，确认真实版本及根分区。
后续再增加 PMIC、网口及其他外设，不能将无外设的启动成功等同于完整 K7 支持。

## SDK 和发布

内核切换后重新构建镜像和配套 SDK，不能沿用旧内核开发包：

```bash
bitbake kickpi-image-minimal -c populate_sdk
```

用户态应用 ABI 取决于配套 libc/库集合；外部内核模块必须使用当前 6.18 的配置、符号和产物。
当前修改仍是本地开发状态。提交并发布 layer，再更新 manifest 固定 SHA，才能在其他电脑复现。

## 本轮验证

以下以实际结果续写，配置成功、内核编译成功、WIC 生成和真机启动分别记录。

2026-11-05：实际执行 bitbake virtual/kernel -c configure 成功，874 项任务检查通过。
已核对 UART、RK3576 时钟/引脚、MMC 和 ext4 为内建。尚未执行完整内核编译。
后续正在讨论 BSP 应使用社区 meta-rockchip 还是完全自维护；此项架构选择与内核源码选择独立。

2026-11-05 后续结果：用户完成内核与 kickpi-image-minimal 构建。实际部署目录存在 Linux
6.18.52 Image、K7 DTB、模块、ext4 与 WIC。
直接读取 WIC 验证 GPT 头/分区数组 CRC、32KiB 处 loader、16MiB 根分区起点、
PARTUUID 与 extlinux 一致；从 WIC 根分区提取的 Image/DTB 与部署文件 SHA256 一致。
验证摘要在 docs/validation/2026-11-05/image/wic-check.json。
发现镜像 root 密码状态为 locked，需要在烧录前选择开发登录策略。尚未修改账户配置，尚未真机启动。

### 本地串口开发登录配置

build-k7/conf/local.conf 已加入：

```bitbake
# Local serial development only; use password authentication for release images.
EXTRA_IMAGE_FEATURES:append = " empty-root-password allow-empty-password"
```

该设置用于测试 SD 卡的串口 bring-up，输入 root 后无需密码。
它不是产品镜像默认策略，没有加入产品配方；发布镜像应去掉这些开发特性并配置认证。
修改后执行 bitbake kickpi-image-minimal，烧录新生成的 WIC，旧 SD 镜像不会自动变化。

配置后实际重建成功：4742 项任务检查通过，4728 项复用。从新 WIC 提取根分区确认
root 密码字段为空；GPT、loader、PARTUUID、Image/DTB 一致性检查仍通过。
验证记录见 docs/validation/2026-11-05/image/wic-check-serial-login.json。尚未真机验证登录。

### 标准 SDK 构建

2026-11-05：标准 SDK 安装器已生成，包含 GCC 15.3.0 AArch64 交叉工具链。
安装器大小、SHA256 和包清单名称见 validation/2026-11-05/sdk/build-report.json。
本记录仅确认构建产物；安装、Hello World 交叉编译与板上执行尚未验收。
