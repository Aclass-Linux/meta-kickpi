# 从零学习 K7 Linux 系统开发

将本仓库作为一个新项目，从理解组件开始，逐步建立自己的 Linux 系统。
不以已有代码、配置或构建产物判断学习进度，不要求了解历史迁移过程。
本文替代旧学习路径，是唯一学习主线：

**rootfs → Kernel → 完整启动链 → 系统集成 → SDK**

学习顺序不等于 BitBake 的依赖执行顺序。构建 rootfs 也可能带出内核等依赖；
不必强行删除依赖，只需在对应阶段再深入研究。
所有验收项从未勾选开始，表示需要亲自理解和验证，并不要求删除现有代码或缓存。

## 学习方法与基础准备

每一阶段都按“组件是什么 → 源码和配置在哪里 → 如何构建 → 产物在哪里 → 如何验证”推进。
一次只改变一个因素，把可复用的修改保存在 layer 中，记录版本、日志和恢复方法。

不熟悉工程结构时，按需查阅 [工程入门教材](docs/getting-started.md)，理解
Git/repo、manifest、layer、MACHINE、DISTRO、image 和 BitBake。
这是辅助教材，不是另一条学习路线，也不要求先逐项完成仓库迁移。

实验前确认源码版本、主机条件、空间和配置。K7 目标使用 `MACHINE = "kickpi-k7"`，
发行版先使用 `poky`。配置存在不代表硬件已经适配，需要逐阶段验证。

在工作区初始化当前终端，随后进入 build 目录：

```bash
cd ~/Documents/yocto
source sources/poky/oe-init-build-env build
```

后面的命令是各阶段实验入口，不要求一次全部执行。首次构建可能下载大量源码。
`bitbake -e` 只用于观察解析环境，不代表编译成功。

## 阶段 1：rootfs——理解 Linux 用户空间

### 核心问题

- rootfs 与内核、文件系统格式、分区、整盘镜像有什么区别？
- BusyBox、Shell、C 库、动态链接器和 PID 1 分别做什么？
- image 如何选择软件包？MACHINE 和 DISTRO 如何影响结果？

### 学习与实验

1. 从最小用户空间开始：程序、必要的库、init、配置及运行时挂载点。
   认识 `/bin`、`/sbin`、`/etc`、`/usr`、`/lib`，区分预置目录与启动后挂载的
   `/proc`、`/sys`、`/dev` 内容。
2. 阅读 `core-image-minimal`，理解 `IMAGE_INSTALL`、包组和 `IMAGE_FEATURES`。
   区分“发行版允许某项功能”与“镜像安装某个软件”。
3. 跟踪简单程序从源码到 rootfs：编译 → `do_install` 的 `${D}` → 软件包 →
   `do_rootfs`。软件配方的安装任务不会直接安装到板子。
4. 构建 rootfs，找到 `${IMAGE_ROOTFS}`，观察 init、Shell、库和配置文件。
5. 在自己的 layer 中创建学习镜像配方，添加一个工具并比较结果。
6. 编写最小应用配方，将自己的程序加入镜像；不要依靠直接修改生成的 rootfs
   保存长期定制。
7. 比较 rootfs 目录、包清单和文件系统镜像，理解 `do_image` 的作用。

实验入口：

```bash
MACHINE=kickpi-k7 bitbake -e core-image-minimal | \
    grep -E '^(IMAGE_ROOTFS|IMAGE_FSTYPES|DEPLOY_DIR_IMAGE|PACKAGE_CLASSES|VIRTUAL-RUNTIME_init_manager)='
MACHINE=kickpi-k7 bitbake -c rootfs core-image-minimal
```

该命令仍会执行必要依赖。若 K7 基础依赖尚不可用，可以先做静态阅读，或明确
建立独立的 QEMU 学习构建目录；QEMU 结果不等于 K7 验证结果。
本阶段不要求板卡启动，ARM64 程序也不能直接当作 x86 主机程序运行。

### 验收

- [ ] 能解释内核和 rootfs 的边界。
- [ ] 能识别 rootfs 中的 init、Shell、库和动态链接器。
- [ ] 能说明 do_install、do_package、do_rootfs、do_image 的区别。
- [ ] 能通过镜像配方添加软件，并检查安装结果。
- [ ] 能将自己的程序通过配方放进 rootfs。

## 阶段 2：Kernel——硬件如何进入 Linux

### 核心问题

- Image、DTS、DTB、模块是什么？设备树怎样关联驱动？
- Kconfig 的 `=y` 和 `=m` 有什么区别？
- 内核 provider、配方、bbappend、配置片段如何配合？

### 学习与实验

1. 从 `virtual/kernel` 追踪实际 provider、配方、源码地址和精确版本。
2. 观察 fetch、unpack、patch，记录 `${WORKDIR}`、`${S}`、`${B}` 和日志。
3. 阅读真实设备节点，学习 include、label、phandle、compatible、status，
   追踪驱动匹配；设备树编译通过不等于硬件工作正常。
4. 独立学习 files 注入与 patch 两种修改方式。可以保留不同内容的实验，
   每次构建只应用选定方案，检查上次修改残留。
5. 学习配置片段、内建驱动和模块，把修改保存在 layer，而不是只改临时源码。
6. 构建内核与 DTB，检查 `.config`、日志、产物和版本。
7. 理解模块版本匹配，以及根存储驱动在挂载 rootfs 前可用的条件。

实验入口：

```bash
MACHINE=kickpi-k7 bitbake -e virtual/kernel | \
    grep -E '^(PN|PV|SRCREV|WORKDIR|S|B|KERNEL_DEVICETREE|DEPLOY_DIR_IMAGE)='
MACHINE=kickpi-k7 bitbake -c fetch virtual/kernel
MACHINE=kickpi-k7 bitbake -c patch virtual/kernel
MACHINE=kickpi-k7 bitbake virtual/kernel
```

参考 [Machine 配置](docs/machine.md)、[设备树教程](docs/device-tree.md)。
参考文件是学习材料，不自动构成本阶段完成证明。

### 验收

- [ ] 能追踪内核的精确源码版本和追加修改。
- [ ] 能解释一个设备节点与驱动的关系。
- [ ] 能保存并验证一个设备树修改和一个配置修改。
- [ ] 能构建并定位内核、K7 DTB 和相关模块，解释用途。
- [ ] 能说明直接挂载根与使用 initramfs 时驱动可用条件的区别。

## 阶段 3：完整启动链——从上电到交给内核

包含所有相关启动组件，不仅是 U-Boot。先识别 K7 实际方案，再区分源码编译
和预编译固件；不能把所有可能的组件一律串接成启动顺序。

### 核心问题

- 上电后谁先执行？DDR 何时可用？
- 启动组件从哪里获取、如何构建、如何打包、放在哪里？
- 各阶段向下一阶段交接什么？启动失败怎样恢复？

### 学习与实验

1. **BootROM 与启动源**：学习芯片固有代码、启动介质选择和恢复模式。
   BootROM 不是本工程编译出来的文件。
2. **DDR 与早期加载**：识别 DDR 初始化代码或二进制，分别核实 SPL、TPL、
   厂商 loader 的实际角色，不假定三者全部存在。
3. **可信固件**：理解安全世界与非安全世界，确认 BL31 的来源，以及是否使用
   OP-TEE/BL32。它们不一定来自 U-Boot 仓库。
4. **U-Boot 本体**：学习 defconfig、自己的设备树、驱动、命令和环境变量。
   U-Boot 与 Linux 的同名 DTS 需要分别核对。
5. **构建与打包**：追踪 bootloader provider、配方、依赖和打包脚本，区分
   单个二进制、组合启动产物、分区镜像和整盘镜像。
6. **布局与交接**：记录介质位置、加载地址、入口和参数来源。所有偏移和地址
   以 K7 实际方案为准，不照抄其他板卡。
7. **观察与恢复**：确认串口电平、接线和恢复方法，先记录已知可用启动日志，
   再在可恢复测试介质上验证自己的组件。

先检查配置，再根据确认的 provider 决定构建命令：

```bash
MACHINE=kickpi-k7 bitbake -e virtual/bootloader | \
    grep -E '^(PN|PV|SRCREV|SRC_URI|UBOOT_MACHINE|S|B|DEPLOY_DIR_IMAGE)='
```

若 provider 不存在或解析失败，先追踪 BSP 配置，不猜配方名。
通用 RK3576 配置能被选中，不等于已经适配 K7。

为 BootROM、DDR 初始化、TPL/SPL/厂商 loader、BL31、可选 BL32、U-Boot
分别建立记录：是否使用、来源、版本、源码或二进制、输入输出、存放位置、交接对象。

### 验收

- [ ] 能画出 K7 实际启动链，标注未采用或可选组件。
- [ ] 能解释 DDR 初始化的位置和必要性。
- [ ] 完成所有实际启动组件的来源、版本、构建方式与布局记录。
- [ ] 能构建可编译组件，并追踪预编译固件的来源和限制。
- [ ] 在确认恢复方案后验证启动组件，进入 U-Boot 查看设备和环境。

## 阶段 4：系统集成——连接成可启动系统

### 核心问题

- U-Boot 如何加载 Image 和 DTB？内核如何找到并挂载 rootfs？
- 镜像格式、分区布局、启动参数如何保持一致？
- PID 1 如何启动服务与 Shell？如何按阶段定位启动故障？

### 学习与实验

1. 汇总前三阶段的产物和版本，确认内核、DTB、模块和 rootfs 匹配。
2. 学习 `IMAGE_FSTYPES`、分区布局和打包，不假定必然输出 WIC。
3. 连接启动组件、内核、DTB、rootfs，核对加载地址、大小和交接参数。
4. 学习 `chosen`、`console=`、`root=`、PARTUUID、`rootwait`、`rootfstype`、`ro/rw`。
5. 先打通块设备 rootfs 路径，观察根设备发现、根挂载、init 执行与登录。
6. 再学习 initramfs：构建带 `/init` 的早期用户空间，进入 Shell，挂载最终根，
   实验 `switch_root`。initramfs 也可以作为最终根，不一定切换。
7. 在可恢复介质或独立 QEMU 环境中，一次引入一个错误，例如错误 `root=` 或
   `init=`，记录、定位并恢复。临时 U-Boot 实验不必立即 `saveenv`。

完整镜像入口（使用自定义镜像时替换目标）：

```bash
MACHINE=kickpi-k7 bitbake core-image-minimal
```

进入目标系统后，按工具可用情况观察：

```bash
cat /proc/cmdline
cat /proc/mounts
cat /proc/1/comm
readlink /proc/1/exe
uname -r
ls /lib/modules
```

烧录前核实镜像类型、介质和精确目标，保存可恢复备份。文件系统镜像不等于
整盘镜像，本教程不提供默认指向某块磁盘的写入命令。

### 验收

- [ ] 能解释各产物在介质中的位置和加载关系。
- [ ] 保存从上电到登录 Shell 的完整日志，标出交接点。
- [ ] 能确认实际根设备、PID 1、内核和模块版本。
- [ ] 完成 initramfs 实验，解释 `/init` 与最终 init 的关系。
- [ ] 完成至少两次受控故障定位和恢复。

## 阶段 5：SDK——独立开发目标应用

### 核心问题

- SDK 与构建系统内部工具链有什么区别？
- 编译器、链接器、头文件、库和 sysroot 如何配合？
- SDK 怎样与目标 rootfs 的版本、ABI 和库匹配？

### 学习与实验

1. 先学习标准 SDK，区分主机工具与目标开发文件。
2. 为确定内容的镜像生成 SDK，找到实际安装器。
3. 安装到独立目录，在新终端加载生成的环境脚本；不猜脚本名称，不混用
   BitBake 环境或其他交叉工具链。
4. 使用环境提供的编译器变量编译最小 C 程序，检查 ELF 架构和动态链接器。
5. 部署到板子运行，再增加外部库依赖，理解开发文件与运行库的区别。
6. 对比 SDK 外部编译部署与 recipe 随镜像构建两条路径。
7. 最后选学扩展 SDK（eSDK）和 devtool，不作为标准 SDK 入门前提。

实验入口：

```bash
MACHINE=kickpi-k7 bitbake core-image-minimal -c populate_sdk
MACHINE=kickpi-k7 bitbake -e core-image-minimal | grep '^SDK_DEPLOY='
```

使用自定义镜像时，应针对同一个镜像生成 SDK。安装器通常在 `tmp/deploy/sdk/`，
以实际变量为准。缺少目标开发库时应调整 SDK 内容，不用主机库替代。

### 验收

- [ ] 能生成并安装与目标镜像对应的标准 SDK。
- [ ] 能说明环境脚本、交叉编译器和 sysroot 的作用。
- [ ] 不调用 BitBake 编译应用，生成 ARM64 程序并在 K7 运行。
- [ ] 能区分缺少头文件、链接库和目标运行库三类错误。
- [ ] 能将外部开发完成的应用重新纳入 recipe 和镜像。

## 实验记录模板

```text
阶段与问题：
预期理解或验证的内容：
源码版本 / MACHINE / DISTRO / image：
输入文件与配置：
执行命令：
日志与产物位置：
观察结果及原因：
修改如何保存、如何恢复：
仍未理解的问题：
```

从阶段 1 的“rootfs 是什么、最小用户空间需要什么”开始，不从历史进度续接。
外设扩展、性能优化和发布工程化可在主线之后另设专题。
