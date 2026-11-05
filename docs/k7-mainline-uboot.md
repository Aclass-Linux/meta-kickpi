# K7 主线 U-Boot 适配与验证

本文件介绍主线 U-Boot。Linux 当前已切换到 [Yocto 6.18 内核](k7-linux-yocto.md)；下文厂商 6.1 内容仅作切换前记录。旧 k7-yocto-build.md 中关于厂商 make.sh、uboot.img、
boot.img、parameter/update.img 的内容仅供历史对照，不用于本方案烧录。

## 目标和输入

基线为官方稳定标签 v2026.10，以官方 generic-rk3576 配置为起点，参考固定厂商
Armbian 提交中的 K7 DTS、defconfig、DDR 选择和 Linux 6.1 设备树。
不是把旧 U-Boot 的 defconfig 直接复制到主线。

| 内容 | 本次设计 |
| --- | --- |
| Yocto provider | u-boot-k7-mainline，独立配方，保留旧 provider 作为对照 |
| U-Boot DTS | 自有 rk3576-kickpi-k7.dts，使用主线 RK3576 绑定和 SPL DTSI |
| 串口 | UART0，主线基线 0x2ad40000，1500000 baud |
| eMMC | 8-bit，限制 52MHz，不启用 HS200/HS400 |
| SD | 4-bit，限制 50MHz，不启用 UHS 电压切换 |
| DDR | 厂商指定 LPDDR4/LPDDR5 v1.08 二进制 |
| BL31 | 固定 rkbin 提交中的 RK3576 v1.25，属于待真机验证组合 |
| 合并 loader | 主线 binman 生成 u-boot-rockchip.bin |
| Linux | 继续使用厂商 6.1 Image 和厂商 K7 DTB |
| 内核启动 | extlinux，根分区 PARTUUID，ext4 |

当前 DTS 是最小 bring-up 配置。沿用早期固件设置的电源轨，没有复制其他开发板的 PMIC
电压配置，也未完成 K7 的 PMIC 管理适配。冷启动能否稳定访问 SD/eMMC 必须真机验证，
不能以编译成功代替。主线 bootloader 的设备树与传给 Linux 的厂商设备树分别维护。

U-Boot 网络启动、显示、触摸、充电动画、Wi-Fi、UFS、NVMe、SPI 启动、环境持久化和
安全启动未作为本轮目标。Linux 网口驱动仍在，不能将“U-Boot 无网络”理解成 Linux 无网络。

## 文件与启动布局

- recipes-bsp/u-boot/u-boot-k7-mainline_2026.10.bb：主线源码、固件依赖、构建和部署。
- recipes-bsp/u-boot/files/mainline/：K7 主线 DTS、SPL DTSI、defconfig。
- conf/machine/kickpi-k7.conf：选择新 provider，内核只生成普通 Image。
- layers/meta-product-k7/wic/kickpi-k7-mainline.wks.in：GPT + 32KiB 处的合并 loader + 16MiB 对齐的 ext4 根分区。
- layers/meta-product-k7/recipes-core/images/kickpi-image-minimal.bb：独立 WIC/extlinux 组装，不继承厂商 rockchip-image。

合并镜像内的 SPL/FIT 偏移由主线 binman 决定，不能再把厂商 uboot.img 写到旧分区偏移。
根分区内存放 /boot/Image、/boot/rk3576-kickpi-k7.dtb、/boot/extlinux/extlinux.conf。
指定 root=PARTUUID=614e0000-0000-4b53-8000-1d28000054a9 与 WKS 分区 UUID 一致。
这是分区 UUID，不是 ext4 文件系统 UUID。

内核 console=ttyFIQ0,1500000 沿用厂商 Linux 的控制台；U-Boot 自身使用 UART0 驱动。

## 构建操作

```bash
bash
cd /home/ryan/Yocto/workspace-k7
source sources/openembedded-core/oe-init-build-env build-k7
bitbake -e virtual/bootloader > k7-mainline-uboot.env
rg '^(PN|PV|SRCREV_uboot|SRCREV_rkbin|UBOOT_MACHINE|UBOOT_BINARY)=' k7-mainline-uboot.env
bitbake virtual/bootloader
bitbake virtual/kernel
bitbake kickpi-image-minimal
```

不要继续执行旧厂商 make.sh。已有旧 deploy 文件可能仍保留，但新 WIC 只引用新合并 loader。
镜像生成后用 wic ls、sgdisk -p 检查实际布局，再检查 /boot 文件和 extlinux 配置。
SDK 从相同镜像配方执行 populate_sdk，用户态 ABI 没有因为更换 bootloader 自动改变。

## 真机验收

先使用可恢复的测试 SD 卡，确认 K7 的 BootROM 启动介质选择和 MaskROM 恢复方式。
这里不假定主机磁盘设备名，也不自动写入 eMMC。

1. 观察 DDR 固件输出、SPL 和 U-Boot banner，确认版本为 2026.10。
2. 在 U-Boot 中执行 version、mmc list、dm tree，确认 eMMC/SD 枚举。
3. 核对测试卡分区后，用对应 mmc N:P 查看 /boot/extlinux/extlinux.conf。
4. 执行 bootflow scan -lb，确认找到 K7 启动项；bootflow scan -b 会尝试启动。
5. 确认加载的是厂商 Linux K7 DTB，而不是 bootloader 的最小 DTB。
6. 保存完整串口日志，确认内核挂载指定 PARTUUID，进入用户态。
7. 测试冷启动、重启、SD 拔插和基本文件读写。

失败定位：没有 DDR 输出先查介质/loader；有 DDR 无 SPL 查 DDR/SPL/BL31；有 U-Boot 无 MMC
查板级 pinctrl/供电；有 MMC 无启动项查分区/文件/配置；有内核无 rootfs 查 PARTUUID 和存储驱动。
PMIC、电压切换、HS200/HS400 和其他外设应在这条基础链路通过后逐项增加。

## 官方依据

- https://docs.u-boot.org/en/latest/develop/release_cycle.html
- 官方 v2026.10 源码的 configs/generic-rk3576_defconfig、arch/arm/dts/rk3576-generic.dts。
- 官方源码 doc/board/rockchip/rockchip.rst 中 RK3576 的 BL31/ROCKCHIP_TPL 构建接口。

## 验证记录

以本文件末尾实际命令结果为准。源码能配置、DTS 能编译、loader 能打包、整盘镜像能生成、
真机能启动是不同验收阶段，不能互相代替。


### 2026-11-05 实测结果

- 官方 Git 标签 v2026.10 固定提交为 5508406582f6f7120dce0c4b3059819a41788db2。
  初次 GitHub 归档输入被 Yocto QA 拒绝，已改为固定 Git 提交；未关闭 QA。
- 最终 `bitbake virtual/bootloader` 成功：1181 项任务检查通过。
  编译器为 Yocto GCC 15.3.0，没有应用旧版取消 -Werror 的补丁或旧路径映射补丁。
- 最终 DTS 已核对 K7 的 SD 引脚配置，去掉 generic 默认额外 PWREN 引脚；未继承他板 PMIC 参数。
- 配置包含 BOOTMETH_EXTLINUX、CMD_BOOTFLOW、CMD_BOOTI、CMD_EXT4，环境为 ENV_IS_NOWHERE。
- fdtget 检查产物 model=KICKPI K7，compatible=kickpi,k7 rockchip,rk3576。
- 生成 u-boot-rockchip.bin（约 9MiB）、idbloader.img、u-boot.itb 和 u-boot.dtb。
  二进制核对 IDB 位于合并镜像起始，FIT 位于 0x7f8000；写入磁盘 32KiB 后，FIT 在磁盘 8MiB。
  合并镜像末端早于根分区 16MiB 起点，具体长度和 SHA256 见 build-report.json。
- `bitbake -e kickpi-image-minimal` 确认 ext4/wic、正确 WKS 路径、关闭厂商 kernel boot.img 生成。
- `bitbake -n kickpi-image-minimal` 的 4742 项任务依赖预演通过；不代表实际 WIC 已生成或分区内容已验证。
- 本轮未完成厂商 Linux 6.1 的完整编译、实际 WIC 组装、SDK 和真机启动验收。
- 主机仍有 glibc 2.44 比 uninative 2.43 新的提示；建议用支持的构建环境做发布验收。

构建日志、binman map 和产物 SHA256 记录在 docs/validation/2026-11-05/mainline/。
改动尚未提交推送，manifest 仍指向旧 K7 layer，不能用另一台机器的 repo sync 获得本轮修改。
旧 provider 仅作为参考保留，它在恢复 Werror 后存在历史编译阻塞，不作为已经验证的回退版本。
