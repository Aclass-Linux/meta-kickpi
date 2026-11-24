# K7 厂商启动方案（K7_BOOT_STACK = "rockchip"）

## 选型原则

支持 RK3576 的组件优先使用 https://github.com/rockchip-linux/ 下的实现。Linux 跟踪指定分支并使用 AUTOREV；U-Boot 与 rkbin 默认固定当前调试使用的提交，见配方中的 SRCREV。每次交付记录实际构建的提交和固件哈希。

“最新”指选定 RK 分支的最新提交，不表示把 Linux 主线最新版本直接替换进 BSP。分支名属于兼容性选择；升级大版本分支仍需重新适配板级配置。

## 当前组件与来源

| 组件 | 仓库 / 分支 | 获取与构建方式 |
| --- | --- | --- |
| Linux | [rockchip-linux/kernel](https://github.com/rockchip-linux/kernel/tree/develop-6.12) / develop-6.12 | linux-k7-rockchip，从源码构建，基于 rockchip_linux_defconfig |
| U-Boot 与 SPL | [rockchip-linux/u-boot](https://github.com/rockchip-linux/u-boot/tree/v5) / v5 | u-boot-k7-rockchip，二者来自同一次源码检出，基于 rk3576_defconfig |
| DDR、Boost、USB plug | [rockchip-linux/rkbin](https://github.com/rockchip-linux/rkbin) / master | 从 RKBOOT/RK3576MINIALL_V5.ini 选择文件 |
| BL31 / TF-A | 同一 rkbin / master | 从 RKTRUST/RK3576TRUST.ini 选择 RK 预编译 ELF |
| BL32 / OP-TEE | 同一 rkbin / master | 从同一 TRUST.ini 选择，与启用 OP-TEE 的厂商 U-Boot 配套打包 |
| boot_merger、DDR 参数工具 | 同一 rkbin / master | 使用 RK 自带工具，不另找第三方替代 |
| 根文件系统、应用、WIC | 当前 Yocto/OE-Core 与产品 layer | 保持 Yocto 构建 |

截至本次检查，v5 基础版本为 U-Boot 2025.04；develop-6.12 的 Makefile 显示 6.12.69。rkbin 当前选中 DDR v1.13、Boost v1.03、BL31 v1.25、BL32 v1.12。这些是检查时的结果，不是配方中的固件文件名锁定。后续 master 更新时，以其配置和产物 manifest 为准。

配方 PV 的 `2025.04+git`、`6.12+git` 是软件包版本标识；源码选择由各配方的分支与 SRCREV 决定。

## 板级适配

U-Boot 使用 v5 自带 RK3576 配置，再应用 `files/rockchip/configure-k7-v5.sh`，选择 K7 DTS、SD 原始扇区 0x4000、extlinux/bootflow 启动，并默认关闭 SPL A/B 探测。当前只有一个 rootfs；以后引入 A/B 时再调整相关配置。K7 的 U-Boot DTS 与 Linux DTS 分别维护，继承各自源码版本的 SoC DTSI。

DDR 原始文件由 MINIALL_V5.ini 决定，使用同一次 rkbin 检出的 ddrbin_tool.py 生成 K7 参数版本。继续保持 LP4/LP4X 的 1560 MHz 配置，回读验证全部 K7 v1.08 共同参数。旧 v1.08 文件仅作为板级参数校验参考，不作为本轮烧录 DDR。生成文件统一命名 `rk3576-ddr-k7-1560.bin`；原始文件名、参数变化与 SHA256 记录在 JSON 中。

更高 DDR 固件版本不等于板子可以使用更高频率。参数校验不通过时终止构建，不能为了跟随最新文件跳过检查。

BL31 和 BL32 都从 rkbin 同一 TRUST.ini 读取。BL32 装载偏移取自该配置，再由 RK FIT 脚本转换为 DRAM 地址。本轮不再依赖单独构建的上游 TF-A 2.15.0。厂商 OP-TEE 配置与真实 BL32 一起使用，避免此前启用客户端却没有安全固件的组合。

## 构建流程

1. BitBake 获取配方固定的 U-Boot、rkbin 提交，以及 kernel/develop-6.12 的最新提交。
2. 读取 RK 的 MINIALL_V5.ini 和 TRUST.ini，归档输入来源与哈希，生成并回读校验 K7 DDR。
3. 从 RK 源码编译 U-Boot、SPL、设备树。使用 RK make_fit_atf.sh 和 Makefile 的 U_BOOT_ITS 入口构建包含 U-Boot、BL31、BL32、DTB 的 FIT。
4. 使用 RK boot_merger，以 MINIALL_V5.ini 为模板，把其中预编译 SPL 替换成本次源码编译 SPL，生成 IDB。
5. 编译 RK Linux Image、模块、K7 DTB；组装 Yocto rootfs 和 initramfs。
6. WIC 在 32 KiB 写 IDB、8 MiB 写 FIT，rootfs 从 16 MiB 开始。extlinux 加载 Image、DTB 和 gzip initramfs。

RK FIT 生成脚本的旧 `python` 解释器名称在任务私有 PATH 中映射到 Yocto 的 Python 3，不修改厂商源码。K7 U-Boot DTSI 显式包含 input.h，满足 RK 通用 DTSI 中 KEY_VOLUMEUP 的依赖。

## 操作

确认上一轮 BitBake 已退出，再运行：

```bash
cd /home/ryan/Yocto/workspace
source sources/openembedded-core/oe-init-build-env build-k7
bitbake kickpi-image-minimal
```

已有 build-k7 的代理设置继续生效。AUTOREV 获取分支最新提交可能在解析阶段访问网络；仅改地址为 HTTPS 不会代替代理配置。不要清空 tmp 或 sstate 作为常规升级步骤，也不要手工删除由 deploy manifest 管理的文件。

烧录 `build-k7/tmp/deploy/images/kickpi-k7/` 下本轮成功生成的 `kickpi-image-minimal-*.wic`，以文件时间和构建成功记录确认。正在构建或失败后留下的旧 WIC 不代表新方案。

## 产物与追溯

| 产物 | 内容 |
| --- | --- |
| k7-rockchip-idbloader.img | Boost、K7 DDR、源码 SPL |
| k7-rockchip-u-boot.itb | U-Boot、BL31、BL32、U-Boot DTB |
| k7-rockchip-spl.bin | 单独归档的源码 SPL |
| k7-rockchip-bl31.elf / k7-rockchip-bl32.bin | 本轮实际使用的安全固件 |
| k7-rockchip-validation.json | 每次构建的 FIT 六段哈希、固件入口、IDB 输入和分区边界检查 |
| k7-rockchip-components.json | U-Boot/rkbin 实际提交、原始固件路径与哈希 |
| k7-rockchip-ddr/ | DDR 输出、输入输出哈希、参数回读和参考对比 |
| k7-rockchip-linux-commit.txt | 实际内核提交 |
| k7-rockchip-linux.config | 实际构建的内核配置 |
| k7-rockchip-loader.ini | 实际 IDB 打包输入 |

分支继续变化时，同一条构建命令可能产生不同镜像。交付时应将这些记录与 WIC、串口日志共同保存；记录提交不等于在配方中锁定提交。

## 故障结论与验收边界

此前 SHA256 失败发生在 SPL 读取 U-Boot FIT payload 后。它不是 CRC 报错，也不能仅凭后续 `Unsupported Boot Device` 判断 SD 不受支持。DDR 降频没有稳定解决原组合的问题；用户确认换用 RK 启动链后问题解决，因此本项目改为 RK BSP 优先。

目前能确认的是启动链实现/组合差异与故障有关。此前新 DDR 的计时器适配、MMC/DMA/缓存等是排查线索，未完成对应主线提交的逐个定位，不能写成已证实的单一根因。

另一处 `optee api revision is too low` 是启用了 OP-TEE 支持但未打包 BL32 的组合错误，与 K7 型号无关。当前方案改用 RK 配套 BL31/BL32。

历史 next-dev 与上游内核组合的通过记录不等于本轮 v5 + develop-6.12 的硬件验收。当前编译/打包验证见 k7-rockchip-validation.json；完整 WIC、冷启动、内存压力、SD 重复读取、网络及外设仍应按本轮组合验收。板级 DTS 目前以串口和存储 bring-up 为范围，不能声称全部 K7 外设已适配。

旧 k7-upstream-boot.md、k7-rk-spl.md 以及对应 JSON 是历史实验记录，不能作为当前构建入口。已有可启动基线保存在本地 baseline/k7-vendor-linux-boot 分支；当前迁移方案按用户要求提交并推送，后续操作见 [新电脑复现](new-computer.md)。

## 本次本地验证记录

- RK v5 检出提交 `18e35f2e75dd2c97c704c8a52ee9bffeef239165`，U-Boot、SPL、K7 DTB 编译通过。
- rkbin 检出提交 `3e288fe814e059dd06833495f845cab04ac20a5c`，DDR 生成后 567 个共同参考参数全部匹配。
- FIT 六个镜像的 SHA256、U-Boot/BL32/DTB 与实际输入一致性检查通过；BL31 入口为 `0x40060000`，与 FIT 选中段一致。
- RK boot_merger 生成 IDB，通过当前 DDR/SPL 原始字节匹配及 8 MiB/16 MiB 边界检查。
- 历史离线解析及展开后的 shell 语法检查曾通过；当时使用本地测试替代远程 revision 查询，不代表当前配置已完成重建或硬件验证。
- 当前环境网络受限，完整 RK 内核构建、BitBake WIC 与实机验收尚未完成。已有下载日志不等于新内核构建成功。

已删除本轮不再使用的上游 TF-A 配方、next-dev 专用 OP-TEE 补丁和旧 RK defconfig。当前配方只从 files/rockchip 读取本地适配输入；files/upstream 下的内容为未选中的历史实验。

## 内核源码目录与许可证检查修复

`do_populate_lic` 曾报告共享内核目录中缺少 `COPYING`。实际源码已解包到 `${UNPACKDIR}/${BP}`，但 kernel.bbclass 默认将 `S` 设为 `${STAGING_KERNEL_DIR}`；两者不一致，且默认 S 与 staging 相等，使 `do_symlink_kernsrc` 未执行搬迁。

内核配方现在在 `inherit kernel` 后显式设置 `S = "${UNPACKDIR}/${BP}"`。由 kernel 类在 patch/configure 前搬迁源码并创建符号链接，许可证、配置和编译使用同一棵源码树。保留许可证校验，实际下载文件的 MD5 为 `6bc538ed5bd9a7fc9398086aedcd7e46`；LICENSE 同步采用 OE-Core 的 `GPL-2.0-with-Linux-syscall-note` 标识。

已验证配方展开路径、实际 COPYING 哈希，并在隔离目录执行 kernel 类原有搬迁函数，确认搬迁后 S 与共享目录均可读取该文件。正式 BitBake 任务仍需重跑。直接再次运行 `bitbake kickpi-image-minimal`；不需要清除下载缓存、tmp 或 sstate。

## Broadcom Wi-Fi：仅调整 Yocto 配方

`do_compile_kernelmodules` 曾在 bcmdhd 的 aiutils.c / siutils.c 报 `typedefs.h` 缺失。该文件实际存在。Linux 6.12 的 Kbuild 在分离构建时让 `src` 包含源码路径，而厂商 Makefile 又通过 `-I$(srctree)/$(BCMDHD_ROOT)` 拼接一次，形成重复路径。

此前的一行 Makefile 补丁修复了头文件搜索，但 `DHD_COMPILED` 仍嵌入绝对 BCMDHD_ROOT，导致打包阶段报 `contains reference to TMPDIR [buildpaths]`。现在已删除该补丁和 SRC_URI 引用，恢复官方驱动 Makefile。

当前仅在 linux-k7-rockchip_git.bb 中传入：

```bitbake
EXTRA_OEMAKE += "BCMDHD_ROOT=drivers/net/wireless/rockchip_wlan/rkwifi/bcmdhd"
```

GNU make 命令行变量覆盖厂商 Makefile 的普通赋值，使其头文件路径成为 `srctree/相对驱动目录`，版本字符串也只包含相对目录。同时解决头文件搜索和构建路径泄露，不关闭 Wi-Fi，不绕过 QA，不修改官方驱动源码。

验证：恢复官方 Makefile后，以原 Yocto 模块任务环境加上述 make 参数重新编译全部模块，退出码为 0；bcmdhd.ko 包含相对版本路径，二进制中不含 TMPDIR 字节序列，与 OE buildpaths 检查方式一致。配方解析通过。完整 do_package_qa 与 WIC 仍需正式构建重跑。

日志中的 `fatal: not a git repository` 来自 MPP Makefile 在构建目录查询 Git 版本；该表达式自带 unknown-version 回退，并非本次两个错误的原因。

直接再次运行 `bitbake kickpi-image-minimal`，让 BitBake 根据配方变更重新执行构建和打包，不需要清空缓存。编译通过不等于 Wi-Fi 实机功能已验收。

## 2026-11-16 完整构建结果

本机 cooker 日志确认 `kickpi-image-minimal` 全部 4727 个任务成功（4675 个复用）。产物为 `kickpi-image-minimal-kickpi-k7.rootfs-20261116024023.wic`。此前各节“完整构建仍需重跑”是当时记录，本条更新其构建状态；USB/eMMC 和外设实机验收仍需单独执行。本次另将已生成的 USB Loader 加入 do_deploy，文件名为 `k7-rockchip-loader.bin`。


## 厂商与上游 U-Boot 切换（学习方案）

### a. 选择方案

未显式选择时采用机器配置的默认值，可运行 `bitbake-getvar K7_BOOT_STACK` 查看生效值。

显式设置 `K7_BOOT_STACK = "rockchip"`，保留现有厂商 U-Boot v5 构建流程。
在构建目录的 `conf/local.conf` 中设置 `K7_BOOT_STACK = "mainline"`，即可选择
`u-boot-k7-mainline`，从固定的上游 U-Boot v2026.01 源码构建 SPL 和 U-Boot。
设置 `K7_BOOT_STACK = "oe"` 可选择 OE-Core 的 `u-boot` 配方，和 mainline 共用 K7 板级配置、DDR 和 BL31。
本开关不切换 Linux 内核；上游方案的 K7 设备树、启动和外设尚待硬件验证。
建议使用两个构建目录，避免混淆产物和串口日志。配置示例也位于模板中。

### b. 固件与任务关系

厂商方案继续由 `u-boot-k7-rockchip` 准备 DDR、BL31、OP-TEE 并打包。
上游方案由独立的 `k7-rkbin` 配方准备 DDR 和 BL31，U-Boot 的
`do_compile[depends]` 等待固件 `do_deploy`，再通过 `ROCKCHIP_TPL` 和 `BL31`
参数交给上游构建系统。两套使用相同 rkbin 提交和 K7 DDR 参数；上游方案
本轮不接入 OP-TEE。这不是整个启动链无二进制固件的方案。

### c. 镜像和 USB 烧录

WIC 按方案选择 `k7-rockchip-*`、`k7-mainline-*` 或 `k7-oe-*` 文件，保持 IDBLoader 在
32 KiB、FIT 在 8 MiB、rootfs 在 16 MiB 的布局。USB 导出仍只有
`usb-flash-k7/k7-rockchip-loader.bin` 和 `kickpi-k7-emmc.img` 两个固定名称。
两条上游路线共用 `k7-mainline-firmware/usb-loader.bin`。该 USB 下载 Loader 由 `k7-rkbin` 使用厂商 SPL/USBPLUG 打包，仅用于
烧录通信；写入磁盘镜像的 SPL 则由上游源码编译，不能将这两者混为一谈。

### d. 验证命令

初始化环境后，先运行 `bitbake -p`，再运行
`bitbake virtual/bootloader` 和 `bitbake kickpi-image-minimal`。
用 `bitbake -e virtual/bootloader` 检查 `PN`、`SRCREV` 和固件路径。
切换方案后应重新构建镜像，不可只替换 USB Loader 就认为存储上的启动链已切换。
构建成功后仍需冷启动串口、SD/eMMC、Linux 启动及实际外设验证。
