> 历史记录。当前方案以 [RK BSP 构建方案](k7-rockchip-boot.md) 为准。

> 已被统一瑞芯微方案取代：当前配置与构建步骤见 [k7-rockchip-boot.md](k7-rockchip-boot.md)。以下内容仅作历史记录。

> 当前实验已切换为 DDR v1.09 / 1560 MHz + 瑞芯微源码 SPL。
> SPL 源码地址为 https://github.com/rockchip-linux/u-boot.git ，固定提交 `1c535d65b8509f388d09e49fb6961f49fda35a1d`。
> DDR 回读检查继续对齐可启动 v1.08 的 567 个共同参数；取消跨版本二进制 cmp。
> 上游 U-Boot 主程序及 TF-A 不变。本组合实机验证待完成。以下为历史记录。

> 当前启动链：官方 DDR v1.08 按四项频率参数生成 → 瑞芯微源码 SPL → 上游 U-Boot 2026.10 + TF-A 2.15.0。
> SPL 独立配方 `rk-spl-k7_2017.09.bb`，固定官方 next-dev 分支头提交 `1c535d65b8509f388d09e49fb6961f49fda35a1d`。
> IDB 的 FlashBoot 已改为该配方安装到 sysroot 的 SPL；上游自身生成的 SPL 不写入 IDB。
> 交叉编译、配方解析及 RK boot_merger 打包通过，完整 WIC 与实机验证待完成。
> 详见 `k7-rk-spl.md`。以下 DDR 实验为历史记录。

> 当前配置：从瑞芯微官方原始 v1.08 二进制按参数重新生成 DDR。
> 输入提交：`57296ad286255d98a3d409036a4b8560b6013b7d`，参数文件 `k7-ddr-v1.08.params`。
> LP4/LP4X F0、F2 均设为 1560 MHz，生成 `rk3576-ddr-v1.08-k7-1560.bin`。
> 已验证输出与可启动的厂商 v1.08 逐字节相同；构建增加 cmp 强制校验。
> SHA256：`e071cc6e0b7897e3cb88cc4b3d7d714625ae89d8859d72342d1124bcd56a662a`。
> v1.09 参数对齐实验由用户报告启动失败，暂不继续升级。重新生成的完整 WIC 尚待构建和实机验证。
> 以下 v1.13 内容为历史记录。

# K7 上游引导链迁移

## 基线与边界

本地已验证启动的厂商方案保存在 `baseline/k7-vendor-linux-boot`，提交
`0a1f895`。未推送。当前开发分支 `bringup/k7-upstream-boot` 的修改尚未提交，
属于待验证迁移方案，不能将基线的 Linux 启动结果沿用到此分支。

工作区另存了当时最新的 WIC：
`/home/ryan/Yocto/workspace/artifacts/k7-vendor-baseline-0a1f895/`。
这是本地最新构建产物的快照；用户板上烧录文件的身份仍以用户实际文件为准。

## 当前实验：v1.13 的共同参数全部对齐 K7 v1.08

在仅降低 F0 仍校验失败后，当前参数文件已进一步对齐频点、电气参数和日志开关。
相对原始 v1.13 共修改 16 项：包括 LP4/LP4X F0、F2、CA 驱动/终端阻抗，
LP4 CA Vref 与五个日志开关。自动回读并对照原始 K7 v1.08，确认全部 567 个共同
字段一致；v1.13 新增的 lp5_vdd2_rail、zq_check 保持原值 0。
v1.13 训练代码与格式保留，不能将参数一致理解为固件整体相同。
生成脚本已实际执行通过，配方会在每次生成时重复验证这些条件。
部署还包含 `.reference.txt` 回读与 manifest 中 reference_sha256 /
common_reference_fields_matched。构建后仍需实机验收；未提交、未推送。

下节是前一轮仅降低 F0 的实验记录，其“只修改两项”不描述当前配置。

## 当前 DDR 配置：官方 v1.13，板级 1560 MHz

用户确认 v1.08 / 1560 MHz 可以启动，v1.13 / 2112 MHz 在 SPL 校验 U-Boot 时失败。
当前改为在构建中配置普通 v1.13 固件的目标频率，以区分版本与频率影响。
不使用 full_space_test 测试固件。

- 原始固件与官方 `tools/ddrbin_tool.py` 都来自配方固定的 rkbin revision。
- 参数文件：`recipes-bsp/u-boot/files/upstream/k7-ddr-1560.params`。
- 生成和校验：同目录 `prepare-k7-ddr.py`，由 Yocto 的 native Python 执行。
- 每次 do_configure 都从原始文件复制生成，不覆盖下载源码。
- 只修改 `lp4_freq`、`lp4x_freq`：2112 → 1560 MHz。
- F1/F2/F3 保持 528/1068/1560 MHz，LPDDR5 及所有其他参数保持原值。
- 使用官方 `--ver_edit ' '` 保留原始版本信息，避免引入构建时间。
- 自动回读全部参数；若出现额外变化、频点高于 F0 或原文件被改变，构建失败。
- 同一个生成文件同时用于 ROCKCHIP_TPL、boot_merger 的 DDR Path1 和 FlashData。

部署目录额外保存 `rk3576-ddr-v1.13-k7-1560.bin`、同名前后参数 `.before.txt` /
`.after.txt`、包含输入/工具/参数/输出 SHA-256 的 `.json`、原始参数文件。
生成结果是板级配置版，启动日志仍保留官方 v1.13 标识，需结合频率与 manifest 识别。

本地验证：重复生成逐字节一致，仅改变原始二进制 4 字节；参数回读符合预期；
boot_merger 打包成功；配方解析通过。见 [DDR 验证记录](k7-ddr-1560-validation.json)。
这些检查不代替完整 WIC 构建或实机验收。下方 v1.13 / 2112 MHz 的历史输入记录保留供追溯。

当前重建命令：

```sh
cd /home/ryan/Yocto/workspace
source sources/openembedded-core/oe-init-build-env build-k7
bitbake kickpi-image-minimal
```

无需清缓存。新参数进入任务签名，自动重新生成并打包。
串口应显示 `fwver: v1.13`、`LPDDR4X, 1560MHz`、最后 `change to F0: 1560MHz`，
随后 FIT 各节点通过校验并进入 Linux。上板结果尚待验证。
建议至少验证冷启动/热重启、多轮内存压力测试及实际使用温度范围；记录失败次数与日志，
在验收前将其视作候选配置。若 Linux 后续启用 DDR 动态调频，还需确认不会升至未验证频率。

更改前最新 WIC 已另存到工作区 `artifacts/k7-ddr-v1.08-1560-baseline/` 并附 SHA256SUMS；
实际是否与板上所烧文件相同，应以用户烧录记录为准。v1.08 原文件仍保留在 files/official，
不需要再使用 files/upstream 中的副本。本次修改未提交、未推送。

## 来源与版本

| 组件 | 选用来源/版本 | 构建方式 |
| --- | --- | --- |
| BootROM | RK3576 芯片内部 | 不可由软件升级 |
| Boost | Rockchip rkbin v1.03 | 官方二进制 |
| DDR | Rockchip rkbin LP4 2112 / LP5 2736 v1.13 | 官方二进制 |
| IDB 打包 | Rockchip rkbin `tools/boot_merger` | 官方工具，x86_64 主机 |
| SPL / U-Boot | U-Boot v2026.10 | 从源码交叉编译 |
| BL31 | Trusted Firmware-A v2.15.0，`PLAT=rk3576` | 从源码交叉编译 |
| FIT | U-Boot 自身的 binman | 包含 BL31、U-Boot、设备树 |
| Linux / rootfs | 现有 linux-yocto 6.18 / Yocto | 本次不升级 |

固定 revision：

- U-Boot：`5508406582f6f7120dce0c4b3059819a41788db2`。
- TF-A：`da738d5eae93af342fdc4995dd3c05acb4c9d757`。
- Rockchip rkbin：`3e288fe814e059dd06833495f845cab04ac20a5c`。

U-Boot/TF-A 候选发布版已核对官方 tag；rkbin 固定到本地官方源缓存中的 revision，
其 RK3576MINIALL.ini 与在线官方文件所列的 Boost/DDR 版本一致。
当前终端无法联网刷新仓库，不能声称已穷尽所有新 tag 或这是 K7 已验证的最新组合。
DDR v1.13 的 LPDDR4 标称频率高于基线 v1.08 的 1560 MHz，需单独验收训练和稳定性。

官方来源：

- [U-Boot v2026.10](https://github.com/u-boot/u-boot/releases/tag/v2026.10)
- [TF-A v2.15.0](https://github.com/ARM-software/arm-trusted-firmware/releases/tag/v2.15.0)
- [TF-A Rockchip 支持](https://trustedfirmware-a.readthedocs.io/en/latest/plat/rockchip.html)
- [Rockchip rkbin](https://github.com/rockchip-linux/rkbin)
- [Rockchip RK3576MINIALL.ini](https://github.com/rockchip-linux/rkbin/blob/master/RKBOOT/RK3576MINIALL.ini)

## 构建和写盘关系

按用户要求保留瑞芯微 `boot_merger`。从固定 rkbin revision 取原始 INI，仅替换
`FlashBoot` 为本地编译的 SPL，修改两个输出路径；Boost、DDR、USB plug、地址、
校验和对齐参数保持该官方 INI 的定义。USB plug 用于工具的 loader 打包输入，
不是 Linux 启动过程中需要运行的用户态组件。

TF-A recipe 将 `bl31.elf` 安装到 recipe sysroot，U-Boot 通过构建依赖消费它。
不从 deploy 目录挑选旧 BL31，也不再依赖厂商 Python2 FIT 脚本。
U-Boot 构建完成后检查 FIT 中的 BL31 节点和 firmware 引用。

WIC 使用独立名字：

| 位置 | 内容 |
| --- | --- |
| LBA 64 / 32 KiB | `k7-upstream-idbloader.img`，boot_merger 生成 |
| LBA 16384 / 8 MiB | `k7-upstream-u-boot.itb`，上游 binman 生成 |
| 16 MiB 对齐 | GPT ext4 rootfs，PARTUUID 保持基线值 |

U-Boot 仍会自动生成 `u-boot-rockchip.bin`，但它不是本方案的写盘输入，
不要用它覆盖 WIC 中的 boot_merger IDB。
Linux 使用 `/boot/extlinux/extlinux.conf` 加载 Image、原始 gzip initramfs、DTB，
取代厂商 boot.scr / armbianEnv / uInitrd。串口仍为 ttyS0、1500000。

驱动保持上游源码，只加入 K7 DTS/defconfig：UART0 M0、SD 引脚、eMMC/SD 的保守
频率及标准 extlinux 启动。未加入 K7DIAG、强制 FIFO、25 MHz 实验或绕过哈希。
这套最小 DTS 仍有板级供电描述不完整的限制，不能称为完整外设支持。

## 本地构建

在可运行 BitBake 且能访问官方源码的主机终端执行：

```sh
cd /home/ryan/Yocto/workspace
source sources/openembedded-core/oe-init-build-env build-k7
bitbake trusted-firmware-a-k7
bitbake u-boot-k7-upstream
bitbake kickpi-image-minimal
```

不用清空 downloads、sstate 或整个 tmp。新 provider、SRCREV 与配方任务依赖会触发重建。
最终烧录新生成的 `kickpi-image-minimal-kickpi-k7.rootfs.wic`，检查时间与 SHA-256，
不要误用 artifacts 中保存的基线镜像。

## 验证状态与验收

已通过 5 个新/相关配方的离线 BitBake 解析、展开后的 shell 语法检查，以及上游
U-Boot 本体、K7 DTB 和 SPL 的离线编译（已有 Yocto GCC 15.3 sysroot）。
官方 boot_merger v1.38 已用新 SPL + Boost v1.03 + DDR v1.13 实际打包成功，IDB 为
200704 字节。未用旧 BL31 冒充源码编译结果，没有生成可烧录的完整新镜像。
结果及产物哈希见 [验证记录](k7-upstream-validation.json)，输入版本和固件哈希见
[来源清单](k7-upstream-inputs.json)。

当前沙箱完整 BitBake 启动在 Unix
socket bind 处被拒绝；GitHub DNS 也不可用，TF-A 尚未下载和编译，尚未生成新 WIC。
TF-A license 当前引用通用 BSD-3-Clause 文本；首次获取源码后应核对实际构建文件许可，
并将校验绑定到源码的许可证文件。

板上按阶段记录：DDR 训练 → SPL → FIT 校验 → TF-A BL31 → U-Boot → extlinux → Linux
→ rootfs。再检查内存容量、CPU 启动、SD 重复读取、重启、eMMC 与供电相关外设。
之前上游 SPL 大块读卡哈希不稳定尚未定位，采用新固件不能直接宣布解决。

回退时先保存当前迁移修改，再切换 `baseline/k7-vendor-linux-boot` 重建，或使用已保存的
基线 WIC。Git 分支只管理 meta-kickpi 内容，外层构建配置、其他层 revision 与产物不会
随分支自动回退。
