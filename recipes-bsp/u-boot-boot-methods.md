# K7 U-Boot 构建方案对比（四路线）

本文放在 recipes-bsp/ 下，供修改 U-Boot provider 前查阅。
对比对象是"同一块板（KICKPI K7 / RK3576）让 U-Boot 进镜像"的四种工程路线；
不是四个 U-Boot 源码版本评测。逐方案给出：机制、现状、优劣、实现要点。

当前已接入的四条路线共用以下板级约定：

- RK3576 启动链 = BootROM → idbloader.img（TPL+SPL）→ u-boot.itb（FIT）→ 内核；
- TPL 是 rkbin 里的闭源 DDR 训练固件，需用 `k7-ddr-1560.params` 改写；
- BL31（TF-A）使用 rkbin 提供的预编译固件；
- 磁盘布局契约（唯一定义处 conf/machine/kickpi-k7.conf）：
  IDB 32 KiB 起，FIT 8 MiB 起，rootfs 16 MiB 起，两条尺寸上限由此推导。

---

## 方案总览

| # | 路线 | U-Boot 源 | 厂商流程编排方 | 本层现状 |
| - | --- | --- | --- | --- |
| ① | 自研配方，全套自编排 | 厂商 v5 树 | 自己（boot_merger + INI sed） | **已实现**，`u-boot-k7-rockchip_git.bb` |
| ② | 自研配方 + require OE .inc | 上游源码树 | 自己（binman 由上游 make 驱动） | **已实现**，`u-boot-k7-mainline_git.bb`，备用 |
| ③ | bbappend 附加到 OE-Core 官方配方 | 上游（随 OE-Core 版本走） | OE-Core（u-boot.inc） | 已接入，`K7_BOOT_STACK = "oe"`，待构建和上板验证 |
| ④ | bbappend 附加到 JeffyCN meta-rockchip | JeffyCN 镜像 | JeffyCN（make.sh + rkbin 集成） | 可选，需启用 JeffyCN 层 |

选择器：`conf/machine/kickpi-k7.conf` 的 `K7_BOOT_STACK`
（当前 "rockchip"→①，"mainline"→②，"oe"→③，"jeffy"→④）。部署产物用 `k7-${K7_BOOT_STACK}-` 前缀隔离，
WIC 按前缀选取 IDBLoader 和 FIT。Linux 内核仍由 `linux-k7-rockchip` 提供。

### 在当前工作区核对选择与产物

在已初始化的构建环境中，执行以下命令。省略 `K7_BOOT_STACK` 时采用机器配置的默认值，可用 `bitbake-getvar K7_BOOT_STACK` 核对；
学习上游路线时，在对应构建目录的 `conf/local.conf` 设置 `K7_BOOT_STACK = "mainline"`。

```bash
bitbake-getvar -r kickpi-image-minimal K7_BOOT_PROVIDER  # 查看镜像选中的配方
bitbake virtual/bootloader -c deploy                    # 构建并部署启动组件
bitbake kickpi-image-minimal                            # 生成 WIC 和 USB 烧录文件
```

WIC 把所选路线的 `*-idbloader.img` 和 `*-u-boot.itb` 写入固定偏移。
镜像任务另在 `tmp/deploy/images/kickpi-k7/usb-flash-k7/` 导出两个固定文件：
`k7-rockchip-loader.bin` 供 RKDevTool 建立下载连接，`kickpi-k7-emmc.img` 是写入
eMMC 的整盘镜像。四条路线使用相同的导出名，应从同一次镜像构建成对取用。

---

## 方案 ①：自研配方，厂商流程

配方：`recipes-bsp/u-boot/u-boot-k7-rockchip_git.bb`

机制：自己写全套任务。do_configure 里调 prepare-k7-firmware.py 用 rkbin 的
ddrbin_tool.py 把 k7-ddr-1560.params 写进原始 DDR bin；do_compile 里 sed 改写
RKBOOT/RK3576MINIALL_V5.ini（FlashData=改写后 DDR，FlashBoot=自编译 SPL），
跑厂商 boot_merger 合成 idbloader，再用 make_fit_atf.sh 组 FIT（含 BL31+OP-TEE）。

优点：
- 当前固定的 rkbin 选出 DDR v1.13，v1.08 用作 K7 参数回读对照。具体构建与硬件验证记录见 [K7 厂商启动方案](../docs/k7-rockchip-boot.md)；
- 版本完全自锁（SRCREV 双仓库固定），可复现性最强；
- FIT 含厂商 OP-TEE（BL32），最贴近厂商交付形态。

代价：
- 任务编排全自维护，OE-Core 任何机制改进都不沾边；
- CVE 补丁全靠自己判断与回移；
- 厂商 boot_merger 要求 x86_64 构建主机。

---

## 方案 ②：自研配方 + 复用 OE 构建逻辑（当前备用）

配方：`recipes-bsp/u-boot/u-boot-k7-mainline_git.bb`

机制：`require u-boot-common.inc + u-boot.inc`，构建/部署骨架借 OE-Core，
自己只做四件事：锁 PV/SRCREV、装板级文件（do_configure:prepend）、喂固件
（do_compile[depends] += "k7-rkbin:do_deploy"，EXTRA_OEMAKE 传 ROCKCHIP_TPL/BL31
给上游 binman）、部署校验（do_compile:append 的两条尺寸上限 + do_deploy:append）。

与 ① 的结构差异：idbloader 由 binman 合成而非 boot_merger；FIT 不含 OP-TEE
（binman tee-os 槽位留空）；B 为树外构建（① 因厂商 FIT 脚本必须 B=S）。

独立的 [固件配方](rkbin/k7-rkbin_git.bb) 同时生成 USB 下载 Loader，因此上游路线也需要 x86_64 构建主机。当前固定上游 v2026.01，K7 冷启动和外设仍待板上验证。

优点：
- 上游 U-Boot 教学与验证路线，减少厂商分叉；
- 版本仍自锁，验证过的组合不会静默变化。

代价：
- require 的是 .inc 不是 u-boot_版本.bb，CVE 补丁清单须手动同步
  （当前把 OE-Core files 路径手动加进 FILESEXTRAPATHS 即为此）；
- 配方里须理解并维护 OE 变量覆盖规则，注释负担重。

---

## 方案 ③：扩展 OE-Core 的 u-boot 配方

在对应构建目录的 `conf/local.conf` 设置 `K7_BOOT_STACK = "oe"`，镜像将选择
OE-Core 的 `u-boot`。本层 [bbappend](u-boot/u-boot_%25.bbappend) 按机器和方案加载
[板级片段](u-boot/k7-upstream-board.inc)。方案 ② 也引用该片段。

| 对照项 | ② mainline | ③ oe |
| --- | --- | --- |
| 版本和补丁 | 本层指定 v2026.01、SRCREV 和补丁 | 由检出的 OE-Core 配方提供 |
| 板级输入 | K7 defconfig、两份设备树文件、k7-rkbin 的 DDR 和 BL31 | 与 ② 共用 |
| 部署 | `k7-mainline-*` | `k7-oe-*` |

在两个构建目录分别选择 mainline 和 oe，运行 `bitbake -e virtual/bootloader`，
对照 PN、PV、SRCREV、SRC_URI 和 UBOOT_MACHINE。然后各运行
`bitbake kickpi-image-minimal`，核对 IDB、FIT 和 USB 烧录目录。相同提交和板级输入
不保证产物哈希相同，构建时间戳等信息也可能影响二进制。升级 OE-Core 后应重新核对
补丁、构建和 SD/eMMC 冷启动。

## 方案 ④：扩展 JeffyCN meta-rockchip

在 `bblayers.conf` 添加 `BBLAYERS += "${WORKSPACE_SOURCES}/meta-rockchip"`，
并在 `local.conf` 设置 `K7_BOOT_STACK = "jeffy"`。使用 JeffyCN 层而非同名社区层。
[JeffyCN 扩展](u-boot/u-boot-rockchip.bbappend) 与 OE-Core 扩展位于同一目录，
由 `conf/layer.conf` 的动态扫描规则收录。K7 适配固定 U-Boot `1c535d65` 和 rkbin `3e288fe8`，这对提交已通过交叉编译及打包检查。
原配方的固定提交缺少 RK3576 所需的 defconfig 和 Loader INI。
WIC 使用 `k7-jeffy-*`，USB 烧录文件仍在 `usb-flash-k7/`。

---

## 决策速查

| 项目诉求 | 推荐 |
| --- | --- |
| 沿用当前固定源码和固件的厂商启动流程 | ① |
| 上游学习 / 减少厂商分叉 | ②，或迁移至 ③ |
| 跟踪 OE-Core 选用的版本和补丁 | ③（升级后重新验证） |

当前四条路线都使用 rkbin DDR/BL31 和 K7 板级参数，
DDR 升级（如 v1.09）不因换路线而变简单；任何路线换 U-Boot/固件版本后，
都需重走"构建 → 尺寸校验 → 冷启串口 → 存储/外设实测"的硬件验证闭环
（AGENTS.md：build 与硬件结果分开报告）。
