> 历史记录。当前方案以 [RK BSP 构建方案](k7-rockchip-boot.md) 为准。
> 清理说明：本文描述的旧版 U-Boot 配方及两个兼容补丁已移除，以下内容仅供历史追溯，不能按旧配方构建。

# K7 当前可启动方案

> 本文记录厂商可启动基线。当前迁移分支的 provider、打包和启动配置见 [上游迁移方案](k7-upstream-boot.md)。

## 验收状态与范围

用户已在 KICKPI K7（RK3576、8 GiB）烧录构建的 WIC，串口显示 U-Boot 加载内核、DTB 和校验通过的 initramfs，随后出现 `Starting systemd-udevd version 259.5` 和 `Welcome to OpenEmbedded nodistro.0!`。当前已进入 Linux/用户态，尚不代表所有外设、登录、根设备和稳定性测试完成。

当前组合是官方 Armbian K7 配套 SPL/U-Boot + Yocto 维护的 Linux 6.18 + Yocto rootfs。不是完整 Armbian 镜像，也不是全部上游最新版本。官方参考目录 `/home/ryan/Yocto/armbian-k7`，提交 `55ecd8e5fba9b45a1156db821d445eea0a112dbf`；构建不依赖该目录。来源与校验值见 [输入清单](k7-official-inputs.json)。

## 配套输入与来源

| 项目 | 当前唯一选择 | 官方依据 |
| --- | --- | --- |
| U-Boot/SPL 源码 | `https://github.com/radxa/u-boot.git`，分支 `next-dev-v2024.10` | `config/sources/families/rk35xx.conf` |
| U-Boot 固定提交 | `39cd993e5d6296635438e84f4576b3a9bf76f86e` | 沿用仓库此前记录的 Radxa 厂商基线；不是从官方历史构建日志恢复出的锁定值 |
| U-Boot 版本 | 配方版本 2017.09 | 厂商分支 Makefile 的 VERSION/PATCHLEVEL；分支名不代表主线 v2024.10 |
| rkbin | `https://github.com/armbian/rkbin.git`，固定 `452f49a987097d7ca675811e51a873b42ebd101f` | `extensions/rkbin-tools.sh` 指定 Armbian rkbin；固定提交用于复现，官方框架本身使用可移动 master |
| defconfig | 原样 `kickpi-k7-rk3576_defconfig` | `patch/u-boot/legacy/u-boot-radxa-rk35xx/defconfig/` |
| U-Boot DTS | 原样 `rk3576-kickpi-k7.dts`，继承厂商 SoC DTS/DTSI | 同目录 `dt/` overlay |
| 通用补丁 | `rk3566-Add-rk3566-to-soc-name.patch`，补充 QA 元数据头并刷新上下文，功能改动不变 | 当前活动 U-Boot patch 目录根补丁；没有添加其他板子的补丁 |
| DDR | 官方仓库随附 v1.08 文件 | `kickpi-k7.csc` 与 `packages/bsp/kickpi/rkbin-tools/rk35/` |
| Boost | Armbian rkbin 的 v1.02 | `rockchip64_common.inc` 中 RK3576 的 boot_merger 设置 |
| USB plug | Armbian rkbin 的 v1.03 | 同上；打包工具输入，不表示把 USB loader 当成 SD 镜像 |
| BL31 | Armbian rkbin 的 v1.04 | `rockchip64_common.inc` 的 RK3576 默认值，K7 板文件未覆盖 |

## 构建适配与启动链

BootROM → Boost/DDR → 厂商 SPL → BL31 → U-Boot → boot.scr → Linux/initramfs → Yocto 用户态。

- `u-boot-k7-official_2017.09.bb` 从固定源码编译 SPL/U-Boot，使用官方 K7 DTS/defconfig。DDR、Boost、BL31 和 boot_merger 使用官方预编译文件；boot_merger 要求 x86_64 主机。
- 官方 RK3566 补丁只刷新上下文及补充 Yocto QA 头，实际新增表项不变。
- `0001-fit-require-bl31-python3.patch` 使用 Yocto 的 Python 3 执行兼容的 BL31 解包脚本，并传播失败。recipe 检查 FIT 必须包含 ATF 节点及正确配置引用。这是必要构建适配，不是调试代码。
- 官方目标 `spl/u-boot-spl.bin u-boot.dtb u-boot.itb` 生成引导文件，再按官方 MINIALL 参数由 boot_merger 生成 IDB。
- Linux 使用 OE-Core linux-yocto 配方及本层 `files/yocto-k7/` 板级 DTS/defconfig，没有为本次排错改动内核驱动。板级 DTS 是精简适配，不能删掉后假设通用 RK3576 DTS 已支持完整 K7。
- 无 K7DIAG 插桩、强制 FIFO 或 SPL 25 MHz 实验设置。Linux 中的 SD 50 MHz/eMMC 52 MHz 是当前板级配置，保留以维持已启动基线。

## SD 与 /boot 布局

| 位置 | 内容 |
| --- | --- |
| LBA64，即 32 KiB | `idbloader.img` |
| LBA16384，即 8 MiB | `u-boot.itb` |
| 16 MiB 对齐的 GPT 第 1 分区 | Yocto ext4 根文件系统 |

两个裸写偏移来自官方 `write_uboot_platform()`。根分区布局由产品 WIC 生成；不能把 Android `update.img` 或旧 `uboot.img` 写入这些位置。根分区 PARTUUID 沿用 `614e0000-0000-4b53-8000-1d28000054a9`。

根分区文件：

```text
/boot/Image
/boot/dtb/rockchip/rk3576-kickpi-k7.dtb
/boot/boot.cmd
/boot/boot.scr
/boot/armbianEnv.txt
/boot/uInitrd
```

`boot.cmd` 从官方 `config/bootscripts/boot-rk3576.cmd` 原样复制，按照其说明用 `mkimage -C none -A arm -T script` 编译。DTB 路径同时满足官方 defconfig 的 `ROCKCHIP_EARLY_DISTRO_DTB_PATH`，这不仅影响 Linux 启动，也可能被厂商 U-Boot 提前读取。

官方脚本会加载 `uInitrd`，因此新增最小 `kickpi-initramfs` 配方，使用 OE-Core initramfs framework、udev 和 rootfs 模块挂载 PARTUUID 所指根分区。它由 Yocto 构建后封装为 gzip legacy ramdisk，没有引入 Armbian 的 Ubuntu 用户空间。当前 Linux 构建配置已确认启用 INITRD/GZIP、内建 SD 控制器和 ext4；用户反馈的串口日志已显示 Linux 和 OpenEmbedded 用户态启动；具体根设备仍需登录后核对。

`armbianEnv.txt` 是产品配置接口：指定根 PARTUUID、DTB、ext4 和 `ttyS0,1500000`。官方脚本默认串口为 ttyS2，设置 `console=none` 并通过 `extraargs` 传入当前 Yocto 内核的 ttyS0，可避免改写官方脚本。它不修改 SPL/U-Boot 自身的串口配置。

## 构建与交付

```sh
cd /home/ryan/Yocto/workspace
source sources/openembedded-core/oe-init-build-env build-k7
bitbake kickpi-image-minimal
# 如需 SDK：
bitbake kickpi-image-minimal -c populate_sdk
```

确认启用 BSP 与产品两个 layer，见 [产品层说明](product-layer.md)。产物在 `build-k7/tmp/deploy/images/kickpi-k7/`，烧录本次生成的 `kickpi-image-minimal-kickpi-k7.rootfs.wic` 到整张 SD 卡。串口为 1500000、8N1、无流控。

登录后保存 `uname -a`、`cat /proc/cmdline`、`findmnt /`（若镜像未安装 findmnt，读取 `/proc/mounts`）以及完整 dmesg，确认实际根分区及驱动状态。保留本次通过启动的 WIC 和哈希，作为后续迁移对照。

## 当前告警与已确认原因

| 现象 | 分析与边界 |
| --- | --- |
| `Unknown command kaslrseed` | 官方脚本已说明该命令非所有板支持；本次继续进入内核，无需为了消除提示改上游启动脚本。 |
| `No misc partition` | 当前 WIC 只有根分区，没有 Android misc 分区；本次不阻止启动。 |
| logo/FDT 保留内存失败 | 厂商显示启动逻辑与精简上游风格 DT 的 logo 节点不匹配。当前不启用 DRM，且继续进入内核；不代表将来显示功能正常。 |
| power-domain device link 失败 | 当前内核 `drivers/base/core.c` 的 fw_devlink 路径中 `device_link_add` 返回空，supplier 显示为 soc。属于固件节点依赖/设备链接问题，不能直接等同电源域硬件损坏。需结合完整 DT、deferred probe 和域状态判断是否影响具体外设。未加 `fw_devlink=off` 掩盖问题。 |
| `Failed to find module autofs4` | 已核对本地最终内核配置：`# CONFIG_AUTOFS_FS is not set`。systemd 请求的自动挂载支持未编译；普通 ext4 根启动已继续，不表示 automount 功能可用。本次不改配置。 |

此前 U-Boot 的 SMC 异常已找到直接构建证据：Python 2 不存在使 BL31 解包失败，旧脚本仍生成了引用 atf-1 却不包含 ATF 的 FIT。时钟驱动发出 SCMI SMC 时异常。适配后已能进入 Linux；这是构建缺陷，不是已证实的 DDR 或 BL31 版本问题。

## 上游迁移分析（本次不切换）

旧主线 SPL 读取同一 U-Boot 数据范围，连续两次 SHA256 不同；小块 ATF 校验通过。主机镜像/回读的历史检查通过。这更指向板上读入、缓存一致性、DMA 描述符/缓冲区管理、内存布局或时序路径的问题，而非固定坏镜像或单纯版本号不匹配；尚不能区分其中哪一个。强制 FIFO 后大块读失败，也不能证明 DMA 就是唯一原因。

官方引导组合能启动，说明硬件和 SD 至少能在这套初始化与驱动组合下工作。它同时更换了 DDR/SPL、设备树、驱动、缓存配置、加载地址及 FIT 打包方式，因此不是控制单一变量的实验，不能把成功归功于某一项。当前用户态前的 Linux 告警属于后续阶段，与此前 SPL SHA256 错误分开处理。

下一步应分阶段迁移：

1. 固定并保留本次成功的源码 SHA、配置、固件和 WIC；先补齐根设备确认及必要外设验收。
2. 保持官方引导链，单独评估目标上游 Linux 版本与 K7 板级支持，核对 PMIC/供电、引脚、MMC、电源域依赖；当前 linux-yocto 已是上游衍生内核，但不是 kernel.org 最新原版。
3. 保持已验证 Linux/rootfs，单独迁移上游 SPL/U-Boot。逐项比较 K7 DTS、DWMMC DMA/缓存路径、加载内存区域与打包方式；先确认镜像结构正确，再验证板上重复读取一致性。
4. 上游版本和支持状态以迁移当日官方仓库为准，锁定明确 tag/commit，不能仅替换版本号或使用移动 HEAD。DDR/BL31 等二进制依赖与开源 SPL/U-Boot 分别记录，不能承诺整个启动链都能由上游源码编译。

本次只整理已启动方案和清理无用参考材料，没有切换任何源码版本、固件或板级参数。原始镜像、SD 回读及 build/downloads/sstate 未删除；仓库内过程文档、旧验证输出和未使用的厂商内核副本已移除。不提交 Git。
