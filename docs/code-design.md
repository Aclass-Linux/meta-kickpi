# K7 上游优先工程：代码设计方案

日期：2026-11-05。状态：设计基线；目录中标注“规划”的内容尚未实现。
本方案承接当前已编译的主线 U-Boot 和已完成配置的 linux-yocto，不能当作整板验收记录。

## 1. 目标与第一版范围

采用 Yocto/OE-Core 维护的构建机制和 Linux，采用官方 U-Boot；自有代码只承载
K7 板级差异与产品策略。厂商 SDK 用于核对引脚、供电、固件选择与硬件行为。
复用已有上游驱动，不重新实现通用内核、文件系统或构建系统。

第一版目标：DDR/SPL/U-Boot → Linux → SD/eMMC 根分区 → 串口登录 → 基础用户态 → SDK。
以可恢复测试 SD 卡为首个启动介质，eMMC 后续单独验收。
网口作为下一阶段，显示、摄像头、NPU、Wi-Fi/蓝牙、音频和量产 OTA 不计入第一版已支持功能。

## 2. 架构决策

| 决策 | 当前选择 | 理由与边界 |
| --- | --- | --- |
| Yocto | Wrynose，保留现有固定提交 | 避免同时更换发行基线和硬件栈 |
| Linux | OE-Core linux-yocto 6.18.52 | 配方、源码、kernel-cache 由 Yocto 维护 |
| U-Boot | 官方 v2026.10，固定 Git 提交 | 复用主线 RK3576 支持，在自有 layer 补 K7 |
| BSP | meta-kickpi 维护 K7 差异 | 不修改 OE-Core 或整套复制厂商 BSP |
| 社区 meta-rockchip | 通用设计/代码复用目标，暂不强依赖 | 本次 Wrynose 提交未提供 RK3576/K7 配置 |
| JeffyCN BSP | 参考/对照，当前不进 BBLAYERS | 避免重新引入厂商 kernel/header/图形 provider 策略 |
| 固件 | 固定 rkbin + 厂商 DDR，单独审查 | 当前没有以开源实现替代 DDR/BL31 的承诺 |
| 启动协议 | 主线合并 loader + GPT/ext4 + extlinux | 与厂商 boot.img/uboot.img/update.img 流程分开维护 |
| 配置方式 | 版本化模板与 layer 为主 | local.conf 仅记录开发机/缓存/并发等本地选择 |

社区与 JeffyCN 的比较见 [选型报告](meta-rockchip-comparison.md)。
企业方案的核心是明确支持范围、固定输入、分层定制和验证责任，并非选择仓库名字即可达成。

## 3. 仓库和 layer 的职责

当前 BSP 根目录保留，产品层已实现为同仓库的 layers/meta-product-k7。
下面的平台独立仓库、包组和产品专用发行版仍为后续规划；不要求一个 layer 对应一个 Git 仓库。

```text
yocto-manifests/                  # 已有：repo XML、来源和固定 SHA
meta-kickpi/                      # 已有：K7 BSP
  conf/machine/                  # CPU tune、硬件特性、内核/启动器选择
  recipes-bsp/                    # U-Boot、启动固件适配
  recipes-kernel/                 # Yocto 内核追加、K7 DTS、配置
  docs/                          # 构建、设计、验证、历史资料
meta-kickpi/layers/meta-product-k7/ # 已实现：产品镜像、启动配置与开发模板
  conf/include/                  # 已实现：产品启动布局
  conf/templates/k7-upstream/     # 已实现：开发构建模板
  recipes-core/images/           # 产品镜像
  recipes-core/packagegroups/    # 规划：按实际应用需求添加
  recipes-core/boot-config/      # 版本化 extlinux 配置与安装配方
  wic/                           # 产品分区布局
  文档见仓库根 docs/product-layer.md
yocto-platform/                  # 规划：CI、发布清单和操作文档
  conf/templates/k7-upstream/    # local.conf.sample、bblayers.conf.sample
  ci/                            # 构建与检查定义
```

先采用 BSP/产品两层。出现第二个产品需要共享发行策略时，再抽出 meta-company，
不为将来可能出现的需求提前创建大量空 layers。

manifest 只管理源仓库和固定版本，不混入 MACHINE 的运行策略。
layer 不包含本机绝对路径，不读取 /home/ryan/Yocto/kickpi-armbian 作为隐含构建依赖。
厂商文件经审查后复制进自己的 Git 仓库，记录来源、修改和许可；参考 SDK 不必存在于 CI 主机。

## 4. 当前代码到目标代码的映射

| 当前文件/做法 | 设计后的归属 | 实施方式 |
| --- | --- | --- |
| conf/machine/kickpi-k7.conf | 保留 BSP | 只声明硬件与经过验证的启动选择 |
| linux-yocto_6.18.bbappend | 保留 BSP | 保持 OE-Core SRCREV，不引入厂商全量配置 |
| files/yocto-k7/defconfig | BSP 基础配置 | 先核对实际 .config，再拆最小基础和功能片段 |
| files/yocto-k7/rk3576-kickpi-k7.dts | Linux 板级 DT | 使用当前内核的上游绑定，不 include 旧厂商 DTSI |
| files/mainline/* | U-Boot 板级文件 | 与 Linux DTS 分别演进，逐项核对共有硬件信息 |
| u-boot-k7-mainline_2026.10.bb | BSP 引导器配方 | 继续复用 OE-Core u-boot.inc，不保留厂商 make.sh 路径 |
| U-Boot 配方内获取 rkbin | 规划：独立启动固件配方 | 提供已验证 DDR/BL31 部署产物，U-Boot 通过任务依赖消费 |
| kickpi-image-minimal.bb | 已移入 layers/meta-product-k7 | 保留用户可用 target 名称，移动时避免两个同名 recipe |
| 镜像内 shell 生成 extlinux | 已改为 k7-boot-files 配方 | 使用模板安装，输出内容可单独审查与打包 |
| WKS | 已移入 layers/meta-product-k7/wic | 单一启动布局变量来源 |
| docs/legacy 与旧厂商副本 | 参考资料 | 不匹配活动 BBFILES，不自动应用 |
| build-k7/conf | 由模板生成的本地配置 | 保留本机参数，产品策略逐步迁回 layer |

不在一次提交中同时移动所有目录、升级源码、修改电压和增加功能。每个阶段保持可验证。

## 5. 社区 BSP 的接入规则

当前社区 Wrynose 提交 9d02575bfd9ca5e87c394f593f2bbdbdee914d4e 没有 RK3576/K7。
因此先参考它的 firmware、kernel metadata、WIC 和 U-Boot 组织，再评估复用：

1. 对需要复用的功能检查 SoC、版本和接口，不能仅复制 rk3588 的配置。
2. 若加入社区 layer，先补全 RK3576 通用适配，并在 meta-kickpi 保留 K7 差异。
3. 验证社区层对 kernel provider、FIT、固件和环境的默认值，与现有启动协议逐项对齐。
4. 在独立构建目录验收后，再更新默认配置与 manifest。
5. 两个同名 rockchip collection 不同时进入默认构建。

BSP 复用与内核源码选择是两个决策。可以使用社区 BSP 配置 linux-yocto，也可以
在当前阶段直接用 OE-Core + 自有 K7 BSP；不以“必须依赖某个 layer”替代功能验证。

## 6. Linux 的设计

### 源码与配置

保留 OE-Core 当前 linux-yocto SRCREV_machine/SRCREV_meta，不用 AUTOREV。
当前 v6.18/standard/base 提交为 364c4b44ab2dcc7dfff16076522b62827bcb0e7d，
kernel-cache 提交为 2184786cc3deed04926e1cca6c320ed9314da9da。
升级由独立变更完成，并重新验证配置、DT、Image 和模块，不能只改 LINUX_VERSION 字符串。

配置分两阶段：先以当前自有 defconfig 建立可启动基线，再按真实需求拆分 serial、storage、
network、diagnostics 等片段。先确认 fragment 合并结果，避免在未验证的阶段同时改变配置生成方式。
UART、MMC、GPT、ext4、devtmpfs 等根分区启动依赖必须内建，不能依赖尚未挂载根分区中的模块。

核对 Kconfig 的完整依赖。例如 USB2 PHY 还依赖 EXTCON，SRC_URI 里写了一个 CONFIG
并不代表最终生效。以最终 .config、配置审计和实测结果为准。

### 设备树和驱动

DTS 描述硬件连接；Kconfig 决定是否构建驱动，两者必须同时满足。
Linux DTS 与 U-Boot DTS 分开保存，不自动复制一份覆盖另一份。
板卡型号、修订版、内存类型、原理图来源、引脚、GPIO 电平、regulator 和供电延时应记录。

第一版 DTS 只启用确认的节点，先使用保守 SD/eMMC 速率；PMIC、DVFS、UHS/HS200/HS400
需专门验证供电和时序。BootROM/固件已上电不代表完整 Linux 电源管理设计已完成。
网口后续按当前内核能力加入 GMAC、MDIO、PHY、复位和时钟配置；不整包带入旧 Maxio 补丁，
尤其不能携带与网口无关的栈保护默认值修改。

自有文件注入任务是目前可工作的开发方式。准备上游提交或版本发布时，可转为
带作者、适用基线、Upstream-Status 和来源记录的板级补丁；同一内容只选择一种注入方式。

## 7. U-Boot 与固件的设计

固定官方 v2026.10 提交 5508406582f6f7120dce0c4b3059819a41788db2。
复用上游 RK3576 SPL/binman 支持，只维护 K7 DTS/defconfig 及配方集成。
不迁入充电动画、显示、厂商 Android 引导和旧 Python 兼容补丁。

固件配方规划输出 k7-ddr.bin 和 k7-bl31.elf，以及来源、版本、SHA256/许可证记录。
U-Boot 的 do_compile 显式依赖其 do_deploy，通过 BL31/ROCKCHIP_TPL 使用产物。
DDR 当前使用厂商 v1.08；BL31 使用固定 rkbin 的 v1.25。该组合已打包，但没有真机认证。
不能仅因“有更新文件”就更新 DDR，也不凭文件名称认定适合所有 K7 内存版本。

编译器警告按具体代码处理；不以全局关闭 QA、Werror 或忽略 patch 失败作为默认策略。
保持 ENV_IS_NOWHERE，第一版不持久化环境，不提前设计 fw_setenv 写入区域。

## 8. 产品镜像与启动契约

当前协议保留：GPT；合并 loader 在 32KiB；根分区从 16MiB 对齐开始；ext4；
根分区内 /boot/Image、K7 Linux DTB 和 /boot/extlinux/extlinux.conf。

已将以下内容设为唯一变量来源，由 WKS 和 extlinux 模板共同消费：

| 变量（已实现） | 归属 | 当前值/约束 |
| --- | --- | --- |
| K7_CONSOLE_DEVICE / K7_CONSOLE_BAUD | BSP | ttyS0 / 1500000，并派生 SERIAL_CONSOLES |
| K7_ROOT_PARTUUID | 产品布局 include | 当前 614e0000-0000-4b53-8000-1d28000054a9 |
| K7_LOADER_OFFSET_KIB | 产品布局 include | 32 |
| K7_ROOT_ALIGNMENT_KIB | 产品布局 include | 16384 |
| K7_ROOT_FSTYPE | 产品布局 include | ext4 |
| KERNEL_DEVICETREE | BSP | rockchip/rk3576-kickpi-k7.dtb |

extlinux 使用 PARTUUID，不能混淆文件系统 UUID。连接多个具有相同 PARTUUID 的介质时
不能保证选择符合预期；测试和量产流程必须核对实际介质，不硬编码 Linux mmcblk 编号。

第一版不混入厂商 parameter/update.img 协议，也不实施 A/B。需要 OTA 时另行设计根分区、
启动确认、回滚、环境存储和断电行为，然后评估 RAUC/SWUpdate，避免先选工具再临时拼分区。

开发账号、远程访问和 root 密码属于产品策略。串口 bring-up 不自动启用空密码 SSH。

## 9. SDK 和应用开发接口

由实际产品镜像执行 populate_sdk，SDK 与镜像/配置/manifest 同版本发布。
SDKMACHINE 是主机架构，与 AArch64 目标分开；用户安装后只使用该 SDK 的环境脚本。
标准 SDK 为第一版交付项，先完成 hello 的交叉编译和板上运行。

产品层维护 SDKIMAGE_FEATURES 和 TOOLCHAIN_*_TASK 的附加包，不覆盖默认工具链包组。
CMake、Ninja、具体库开发包按应用需求加入，不默认生成全功能开发环境。
外部内核模块通过 inherit module 配方或经验证的内核开发包构建，匹配版本、配置与符号。
eSDK 在标准 SDK 稳定后独立验证，不作为第一轮启动阻塞项。

## 10. 构建配置、CI 和发布

版本管理覆盖三类输入：manifest 仓库 SHA、配方下载源码/固件 SHA、构建配置与环境版本。
固定 layer SHA 不会冻结 AUTOREV，也不会保存未提交的本地文件。
平台仓库将提供配置模板和 CI 定义；当前用户要求操作文档，不以一键 shell 脚本作为交付前提。

local.conf 保存 DL_DIR、SSTATE_DIR、并发、开发主机 SDKMACHINE 等本地选择；
MACHINE/DISTRO 的标准组合由版本化模板选择。开发者和 CI 使用同一套已审查模板。
下载与 sstate 可共享，tmp 只属于一个构建配置。首次 CI 应进行干净 checkout 验证，
保留 source mirror/制品以降低公开上游失效的影响。

CI 分级，不为文档修改重复全量编译：

| 检查 | 触发条件 | 证据 |
| --- | --- | --- |
| 文档、模板、补丁静态检查 | 相关改动 | diff、链接、路径与来源信息 |
| layer/配方解析 | 配方或配置变更 | bitbake -p、provider 与展开值 |
| 内核配置和审计 | defconfig/fragments/版本变更 | 最终 .config、必需驱动为 y、配置差异 |
| U-Boot/内核编译 | 对应源码、配置、DT 变更 | 日志、ELF/DTB、版本、部署产物 |
| WIC 实际生成与布局检查 | boot-config/WKS/镜像变更 | 分区表、/boot 文件、PARTUUID 一致性 |
| 真机基础验收 | 发布候选/启动相关变更 | 冷启动、重启、SD/eMMC、串口日志 |
| SDK 验收 | SDK/应用 ABI/版本发布 | 干净电脑安装、目标 ELF、板上输出 |

只保存真正完成的状态：规划 → 实现 → 配置/编译通过 → 镜像生成 → 真机验收。
不以 -n 预演通过代替编译或 WIC 实际生成。

## 11. 分阶段实施清单

| 阶段 | 具体工作 | 完成条件 |
| --- | --- | --- |
| P0 当前基线确认 | 完整编译 linux-yocto，核对驱动依赖，编译 K7 DTB | Image/DTB/modules 成功部署，配置核对通过 |
| P1 首个可启动镜像 | 实际生成当前 WIC，核对布局和文件，测试 SD 启动 | Linux 6.18 串口登录、根分区可写、重启正常 |
| P2 配置集中与分层 | boot 变量唯一来源，固件独立配方，产品层/模板拆分 | 干净配置重复构建得到一致的启动协议 |
| P3 SDK | 标准 SDK 构建安装和 hello 实验 | 干净主机编译，K7 实际运行 |
| P4 板级完善 | PMIC/温控/网口优先，其他外设按需求 | 每项独立编译与硬件验收记录 |
| P5 社区复用/上游提交 | 补 RK3576 通用支持，复用/贡献社区层 | 独立构建回归通过，默认配置明确 |
| P6 产品发布 | 固定版本、更新 manifest、恢复方案、制品和测试报告 | 另一台电脑可复现，发布候选真机通过 |

P0/P1 先于大规模目录移动；不把尚未启动的开发代码直接标成企业量产基线。

## 12. 已有证据与当前缺口

- U-Boot 2026.10：真实构建/部署通过，K7 DT 标识和 IDB/FIT 组合已核对。
- linux-yocto 6.18.52：内核真实构建通过，UART/MMC/ext4 等关键选项已核对。
- 独立 K7 BSP：已在当前 build-k7 去掉 JeffyCN layer；不是社区 BSP 已接入。
- WIC：Linux 切换后已真实生成，GPT、loader、根分区与启动文件已离线核对。
- 标准 SDK：安装器已生成；安装、交叉编译应用和板上运行尚未验收。真机启动、PMIC、电压/温控、网络仍待验收。
- 已知配置检查点：USB2 PHY 的 EXTCON 依赖需在 P0 核对，不能将 USB 写入已验证功能列表。
- 拆分前基线已提交为 f5698b4；当前推送受 GitHub 认证阻塞。只有先推送 layer、再推送更新后的 manifest，其他机器才能 repo sync。

操作入口：[Yocto 内核](k7-linux-yocto.md)、[主线 U-Boot](k7-mainline-uboot.md)。
参考：[BSP 选型比较](meta-rockchip-comparison.md)、[历史迁移/SDK 教材](k7-yocto-build.md)。

## 13. 当前分层实现

见 [产品层操作文档](product-layer.md)。本轮只拆分镜像、启动文件包、WIC 和构建模板；
固件独立配方、业务包组、产品 DISTRO、CI 和量产策略仍属后续工作。
