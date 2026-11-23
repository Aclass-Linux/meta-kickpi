# K7 U-Boot 构建方案对比（四路线）

本文放在 recipes-bsp/ 下，供修改 U-Boot provider 前查阅。
对比对象是"同一块板（KICKPI K7 / RK3576）让 U-Boot 进镜像"的四种工程路线；
不是四个 U-Boot 源码版本评测。逐方案给出：机制、现状、优劣、实现要点。

当前已实现的两条路线共用以下板级约定：

- RK3576 启动链 = BootROM → idbloader.img（TPL+SPL）→ u-boot.itb（FIT）→ 内核；
- TPL 是 rkbin 里的闭源 DDR 训练固件，需用 `k7-ddr-1560.params` 改写；
- BL31（TF-A）使用 rkbin 提供的预编译固件；
- 磁盘布局契约（唯一定义处 conf/machine/kickpi-k7.conf）：
  IDB 32 KiB 起，FIT 8 MiB 起，rootfs 16 MiB 起，两条尺寸上限由此推导。

---

## 方案总览

| # | 路线 | U-Boot 源 | 厂商流程编排方 | 本层现状 |
| - | --- | --- | --- | --- |
| ① | 自研配方，全套自编排 | 厂商 v5 树 | 自己（boot_merger + INI sed） | **已实现**，`u-boot-k7-rockchip_git.bb`，当前默认 |
| ② | 自研配方 + require OE .inc | 上游源码树 | 自己（binman 由上游 make 驱动） | **已实现**，`u-boot-k7-mainline_git.bb`，备用 |
| ③ | bbappend 附加到 OE-Core 官方配方 | 上游（随 OE-Core 版本走） | OE-Core（u-boot.inc） | 未实现，可行（见下） |
| ④ | bbappend 附加到 JeffyCN meta-rockchip | 厂商树（JeffyCN 镜像） | JeffyCN（make.sh + rkbin 集成） | 未实现，不建议（见下） |

选择器：`conf/machine/kickpi-k7.conf` 的 `K7_BOOT_STACK`
（当前 "rockchip"→①，"mainline"→②）。部署产物用 `k7-${K7_BOOT_STACK}-` 前缀隔离，
WIC 按前缀选取 IDBLoader 和 FIT。Linux 内核仍由 `linux-k7-rockchip` 提供。

### 在当前工作区核对选择与产物

在已初始化的构建环境中，执行以下命令。省略 `K7_BOOT_STACK` 时使用厂商路线；
学习上游路线时，在对应构建目录的 `conf/local.conf` 设置 `K7_BOOT_STACK = "mainline"`。

```bash
bitbake-getvar -r kickpi-image-minimal K7_BOOT_PROVIDER  # 查看镜像选中的配方
bitbake virtual/bootloader -c deploy                    # 构建并部署启动组件
bitbake kickpi-image-minimal                            # 生成 WIC 和 USB 烧录文件
```

WIC 把所选路线的 `*-idbloader.img` 和 `*-u-boot.itb` 写入固定偏移。
镜像任务另在 `tmp/deploy/images/kickpi-k7/usb-flash-k7/` 导出两个固定文件：
`k7-rockchip-loader.bin` 供 RKDevTool 建立下载连接，`kickpi-k7-emmc.img` 是写入
eMMC 的整盘镜像。两条路线使用相同的导出名，应从同一次镜像构建成对取用。

---

## 方案 ①：自研配方，厂商流程（当前默认）

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

## 方案 ③：bbappend 附加到 OE-Core u-boot 配方（未实现，可行）

机制：直接消费 OE-Core 的 `u-boot_%.bb`（含其 SRC_URI/SRCREV/构建/部署/CVE
补丁全套），本层只写一个 `u-boot_%.bbappend` 补板级差异。与 ② 的唯一实质区别：
**版本选择权交给 OE-Core**——它升 U-Boot 版本，K7 自动跟随。

实现要点（全部素材已存在于 files/mainline/，可与 ② 共用）：

```bitbake
# recipes-bsp/u-boot/u-boot_%.bbappend
FILESEXTRAPATHS:prepend := "${THISDIR}/files/mainline:"

SRC_URI:append:kickpi-k7 = " file://kickpi-k7-rk3576_defconfig \
    file://rk3576-kickpi-k7.dts file://rk3576-kickpi-k7-u-boot.dtsi"

UBOOT_MACHINE:kickpi-k7  = "kickpi-k7-rk3576_defconfig"
UBOOT_MAKE_TARGET:kickpi-k7 = "all"
UBOOT_BINARY:kickpi-k7   = "u-boot.itb"
UBOOT_SUFFIX:kickpi-k7   = "itb"
UBOOT_INITIAL_ENV:kickpi-k7 = ""

do_compile[depends] += "${@'k7-rkbin:do_deploy' if d.getVar('MACHINE') == 'kickpi-k7' else ''}"
EXTRA_OEMAKE:append:kickpi-k7 = \
    " ROCKCHIP_TPL=${DEPLOY_DIR_IMAGE}/k7-mainline-firmware/rk3576-ddr-k7-1560.bin \
      BL31=${DEPLOY_DIR_IMAGE}/k7-mainline-firmware/bl31.elf"

do_configure:prepend:kickpi-k7() {
    install -m 0644 ${UNPACKDIR}/kickpi-k7-rk3576_defconfig ${S}/configs/
    install -m 0644 ${UNPACKDIR}/rk3576-kickpi-k7.dts ${S}/arch/arm/dts/
    install -m 0644 ${UNPACKDIR}/rk3576-kickpi-k7-u-boot.dtsi ${S}/arch/arm/dts/
}

# idbloader 部署与尺寸校验与 ② 完全同构，从 ② 的 do_deploy:append/
# do_compile:append 原样搬入；k7-rkbin 及其文件名契约原样复用。
```

注意：u-boot.inc 不认识 RK 的 idbloader，`do_deploy:append` 仍需自部署；
u-boot.inc 已自带 addtask deploy（u-boot.inc:471），bbappend 无需重复声明。

优点：
- CVE 补丁自动继承，构建机制随 OE-Core 改进；
- 板层只剩"K7 是什么"，U-Boot 构建机制零维护，审计面最小；
- 可复用 ② 的板级输入，迁移后仍需验证任务依赖、部署命名和冷启动。

代价/风险：
- **版本漂移无闸门**：OE-Core 升版本即自动换 U-Boot，DDR/SPL 交棒须重新
  硬件验证（参照 docs/k7-ddr-v108-v109-analysis.md 的教训：参数逐项匹配
  也不保证可启动）；
- 若要保留闸门，可加 `python __anonymous` 检查 PV 白名单，超出版本即
  bbfatal 提示先做硬件回归——有此闸门后 ③ 的可复现性接近 ②。

结论：上游赛道里结构最优，适合教学与长期跟踪上游；做第三方案
的候选。要接入选择器，还需在机器配置中新增 `oe → u-boot` 映射，并让 WIC 和 USB 导出使用与部署文件一致的前缀。当前选择器仅接受 `rockchip` 和 `mainline`。

---

## 方案 ④：bbappend 附加到 JeffyCN meta-rockchip（未实现，不建议）

机制：把 JeffyCN meta-rockchip（github.com/JeffyCN/meta-rockchip）加入
BBLAYERS，append 其 `u-boot-rockchip`（厂商流程：make.sh 打包 + rkbin 集成），
本层补 K7 的 defconfig/dts。本质是"把 ① 外包给第三方 layer"，不是新机制。

调研依据（docs/meta-rockchip-comparison.md，2026-11-05，固定提交
f2fa9fd14571b855bb59a51da11616904a65f36d）：JeffyCN 提供 rk3576.inc 与
RK3576 EVB machine，是两条 meta-rockchip 里唯一有 RK3576 支持的。

优点：
- 可复用已有的 RK3576 EVB 配置和厂商打包流程，K7 的 DDR 参数、设备树及镜像布局仍需适配；
- 跟随 JeffyCN 的 Rockchip 生态持续更新。

代价/风险（对照调研文档实证）：
- 当前参考检出中的 `u-boot-rockchip.bb` 分别固定了 U-Boot 和 rkbin 的提交。若产品同时启用使用 AUTOREV 的图形配方，需另外记录其实际源码版本；
- 该层依赖 meta-openembedded/meta-oe（当前 K7 模板已启用）；layer collection 与社区
  meta-rockchip 同名（rockchip），二者不能同时入 BBLAYERS；
- boot provider 定义权部分让渡：JeffyCN 改配方结构时本层 append 可能
  静默失效，K7_BOOT_STACK 的双方案隔离机制被打破；
- 对已完成 ①② 自研的本项目，收益只剩"复用厂商打包任务"，换来的却是
  外部依赖与版本失控。

结论：真正的适用场景是**项目冷启动**（从零快速拿到能启动的厂商栈）。
本仓库 ① 已存在且经过验证，回头引入 ④ 属负收益；若未来评估，先重读
meta-rockchip-comparison.md 并重新核对 JeffyCN 当时提交。

---

## 决策速查

| 项目诉求 | 推荐 |
| --- | --- |
| 沿用当前固定源码和固件的厂商启动流程 | ①（当前默认） |
| 上游学习 / 减少厂商分叉 | ②，或迁移至 ③ |
| 教学与跟踪上游、白拿 CVE 补丁 | ③（加 PV 白名单闸门后） |
| 从零冷启动、快速拿厂商栈 | ④（仅新项目冷启动场景） |

当前两条路线都使用 rkbin DDR/BL31 和 K7 板级参数，
DDR 升级（如 v1.09）不因换路线而变简单；任何路线换 U-Boot/固件版本后，
都需重走"构建 → 尺寸校验 → 冷启串口 → 存储/外设实测"的硬件验证闭环
（AGENTS.md：build 与硬件结果分开报告）。
