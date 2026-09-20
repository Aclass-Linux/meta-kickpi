# K7 Yocto 与 Linux 学习计划

本仓库用于学习和实验，以理解构建过程、启动交接及用户空间为目标。
本计划是唯一的学习顺序；详细命令放在 docs 中，避免同时维护多套计划。

## 起点与执行方式

已有记录：layer/repo 纳管、machine/provider 解析已通过；四个 K7 DTS/DTSI
已保存，SRC_URI 解析已通过。fetch、do_patch、编译和板上启动按实际日志验收，
不能因为时间经过或上一步通过就自动勾选。

建议从第 2 章继续实践，第 1 章作为复习。各章按“理解问题→动手实验→解释
结果”的顺序完成，不设硬性天数。首次下载和 native 工具构建可能较长。

## 1. 认识工程：Git、repo 与 Yocto 配置

入门教材：[从零认识当前 K7 Yocto 工程](docs/getting-started.md)。先完整阅读，
再做文末的集中练习，无需逐个文件等待讲解，也无需删除已有成果。

学习目标：区分 manifest、layer、machine、recipe、bbappend 和本地 build 配置。

实验：查看 repo list、Git 分支和远端；追踪 BBLAYERS、BBPATH、collection、
LAYERDEPENDS；解释 kickpi-k7.conf 如何 require meta-rockchip 中的 rk3576.inc。

参考：[Layer](docs/layer.md)、[Machine](docs/machine.md)。

- [x] 已有验证记录：layer 被识别，依赖和兼容系列正确。
- [x] 已有验证记录：K7 选择 linux-rockchip 6.1 和 K7 DTB。
- [ ] 能独立解释各类文件的职责，并从 detached HEAD 建立开发分支。

## 2. 理解任务：从 fetch 到 deploy

学习目标：分清配置解析、源码下载、解包、补丁、配置、编译与部署。

在工作区根目录初始化环境：

```bash
cd ~/Documents/yocto
source sources/poky/oe-init-build-env build
MACHINE=kickpi-k7 bitbake -e virtual/kernel | \
    grep -E '^(PN|PV|SRCREV|SRC_URI|WORKDIR|S|B|DEPLOY_DIR_IMAGE)='
MACHINE=kickpi-k7 bitbake -c fetch virtual/kernel
MACHINE=kickpi-k7 bitbake -c patch virtual/kernel
```

`-e` 输出解析环境；`-c fetch` 选择 do_fetch；`-c patch` 执行 do_patch 及必要
前置任务。后者在当前 bbappend 中还会复制 K7 DTS。无需每次强制执行或 clean。

- [ ] fetch 成功，能找到下载日志和缓存目录。
- [ ] do_patch 成功，能解释 unpack、patch 与内核编译的边界。
- [ ] 记录 WORKDIR、S、B 和部署目录的实际值。

## 3. 学习设备树：files 与 patch 两条路径

学习目标：理解节点、属性、label、phandle、include、compatible、status，
并掌握两种把 DTS 放入内核源码的方法。

先完成 files 实验：查看 UART 和存储节点，比较 layer 中四个文件与 S 目录中
对应文件，确认 DTS Makefile 的 K7 DTB 条目只出现一次，检查公共 DTSI 是否存在。

再按 [设备树教程](docs/device-tree.md) 独立学习 Git 提交和 format-patch。
生成 patch 前确认开发工作树基线未预先包含 K7 改动。patch 路径可以稍后完成，
不阻塞第 4 章；当前 KICKPI_DTS_MODE 开关尚未实现。

两套实验允许不同内容，不要求相同 DTB。启用选择开关后，每次构建只能使用
一个模式；切换时重新准备干净源码，防止上一次注入内容残留。

- [ ] files：四个文件注入正确，Makefile 目标唯一，依赖文件存在。
- [ ] 能解释一个真实设备节点如何与驱动关联。
- [ ] patch（可稍后做）：生成并在正确基线上成功应用。
- [ ] 双模式（可稍后做）：实现互斥选择，并分别记录实验结果。

## 4. 学习内核：配置、驱动与构建产物

学习目标：区分 DTS 硬件描述与 Kconfig 驱动选择，理解 =y 和 =m。

```bash
MACHINE=kickpi-k7 bitbake virtual/kernel
MACHINE=kickpi-k7 bitbake -e virtual/kernel | \
    grep -E '^(B|DEPLOY_DIR_IMAGE|KERNEL_DEVICETREE)='
```

按实际输出定位 Image、DTB 和构建目录中的 .config；记录 SRCREV 和构建日志。
Armbian 与当前内核基线不同，缺失节点或驱动接口须逐项分析。

- [ ] 内核构建成功，找到 Image 和 K7 DTB。
- [ ] 能解释启动存储驱动为什么可能必须内建，以及模块何时才能加载。
- [ ] 理解并处理 DTS 编译错误，记录剩余 warning。

## 5. 学习 rootfs：最小镜像、库和 init

学习目标：理解镜像 recipe 如何组合软件包，以及 /sbin/init、Shell、库和
模块在 rootfs 中的位置。

```bash
MACHINE=kickpi-k7 bitbake core-image-minimal
MACHINE=kickpi-k7 bitbake -e core-image-minimal | \
    grep -E '^(IMAGE_ROOTFS|IMAGE_FSTYPES|DEPLOY_DIR_IMAGE|VIRTUAL-RUNTIME_init_manager)='
```

检查实际 rootfs 目录与镜像 manifest。不要假定生成 WIC 或默认使用 systemd。
若镜像构建依赖尚未可用的 U-Boot 配置，先解决该依赖或提前进行第 9 章相关实验。

- [ ] 找到最小镜像、软件包清单、init 和 Shell。
- [ ] 能解释 ELF 架构、动态链接器缺失与共享库缺失的区别。
- [ ] 理解内核版本、配置与模块之间的兼容关系。

## 6. 学习启动交接：现有 U-Boot 加载内核

学习目标：了解 BootROM、DDR 初始化、SPL/loader、可信固件和 U-Boot 的分工，
重点观察 U-Boot 向 Linux 交接的 Image、DTB、bootargs。

优先使用已经确认可启动 K7 的现有 bootloader。记录实际串口、启动介质、
分区布局、加载地址和启动命令；这些参数从板上获取，不套用其他板卡数值。
如果没有可用 bootloader，先完成第 9 章中启动所必需的部分。

- [ ] 能画出本板实际启动链，而不是只背通用顺序。
- [ ] U-Boot 成功加载 Image/DTB，串口出现 Linux 日志。
- [ ] 能解释 chosen、console、root= 和加载地址的作用。

## 7. 打通 rootfs → PID 1 → Shell

学习目标：理解根设备发现、文件系统挂载和用户空间执行是不同环节。

先验证直接挂载块设备 rootfs：核对 root=、PARTUUID、rootwait、rootfstype、
ro/rw；确认访问根存储的驱动及其依赖、文件系统支持在挂载前可用。

进入目标板后收集（按镜像工具可用情况执行）：

```bash
cat /proc/cmdline
cat /proc/mounts
cat /proc/1/comm
readlink /proc/1/exe
uname -r
ls /lib/modules
```

- [ ] 保存从 U-Boot 到登录 Shell 的完整串口日志。
- [ ] 在日志中标出存储探测、根挂载、init 执行和登录提示。
- [ ] 能确认实际 PID 1；不把 /sbin/init 固定等同于 systemd。

## 8. initramfs 与启动故障实验

学习目标：对比直接挂载根与早期用户空间路径，练习按日志定位故障。

先构建带 /init 的最小 initramfs，进入其 Shell；再实验挂载最终根并执行
switch_root。initramfs 也可以作为最终根，不一定切换。通过 exec 进入最终
init 时 PID 1 保持不变。

在可恢复测试介质或 QEMU 中一次改变一个参数：错误 root=、错误 init=。
记录原配置并恢复；U-Boot 临时参数实验不必 saveenv。

| 现象 | 检查方向 |
| --- | --- |
| Kernel 没有串口输出 | 启动交接、镜像/DTB、console |
| 等待根设备或无法挂载 | 分区标识、存储驱动及依赖、文件系统 |
| No working init found | 路径、权限、架构、动态链接器与库 |
| Shell 正常但服务异常 | init 实现、服务配置和应用依赖 |

- [ ] 能说明 /init 与最终 /sbin/init 的关系。
- [ ] 完成 initramfs Shell 与 switch_root 两个实验。
- [ ] 至少记录两种故障及其恢复过程。

## 9. 深入 U-Boot 与镜像布局

学习目标：学习 K7 defconfig、U-Boot DTS、DDR blob、SPL/loader 和可信固件。

从实际选择的 bootloader recipe 和 Armbian K7 配置追踪来源，分别核对 defconfig
是否存在、组件格式、打包偏移和分区布局。通用 rk3576_defconfig 能被选择，不
代表生成的 bootloader 已兼容 K7。

- [ ] 记录每个启动组件的来源、版本、用途和放置位置。
- [ ] 构建并在可恢复介质验证自己的启动组件。
- [ ] 能解释镜像文件、分区镜像与整盘镜像的区别。

## 10. 外设与用户空间扩展（选修）

按兴趣选择一项：有线网络、USB、Wi-Fi/蓝牙、音频、显示/触摸、摄像头或
GPU/NPU。先从已有驱动支持的简单设备开始，每项追踪 DTS→驱动→固件→
设备节点/接口→应用或服务。多板卡迁移和发布工程化留作进一步练习。

- [ ] 完成一个外设闭环，并解释故障可能出现的层次。
- [ ] 写一个简单软件包或服务 recipe，验证其进入镜像并运行。

## 实验记录模板

```text
日期与学习问题：
machine / 内核 SRCREV / 实验模式：
初始条件与假设：
修改文件与命令：
实际日志与产物位置：
结果解释：
恢复方法：
下一步：
```

下一项实际任务：第 2 章 fetch/do_patch，加上第 3 章 files 注入结果检查。
只有记录到成功结果才勾选；本次文档重组不代表执行了这些构建实验。
