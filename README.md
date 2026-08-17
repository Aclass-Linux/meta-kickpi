# KickPi Armbian → Yocto 迁移手册

本文用于把 `kickpi-armbian` 中的 KickPi 板级支持逐步迁移到当前 Yocto 工程。迁移按可验证的小步骤进行，先完成 **KickPi K7（RK3576）**，再复用相同结构支持 K7C/K8/K1。

> 原则：不要把整个 Armbian 构建系统复制进 Yocto。只迁移板级设备树、内核补丁、U-Boot 配置、固件和确实需要的用户空间配置。

## 0. 当前目录

```text
yocto/
├── sources/
│   ├── poky/
│   ├── meta-openembedded/
│   ├── meta-rockchip/       # 已有 RK3568/RK3576/RK3588 BSP
│   └── meta-kickpi/         # KickPi layer，独立 Git 仓库并由 repo 管理
├── build/                   # 本地构建输出，不进入 repo
└── kickpi-armbian/          # 迁移完成后可从正式 manifest 中排除
```

主要来源：

- K7 板级配置：`kickpi-armbian/config/boards/kickpi-k7.csc`
- K7 Linux DTS：`kickpi-armbian/patch/kernel/rk35xx-vendor-6.1/dt/rk3576-kickpi-k7*.dts*`
- K7 U-Boot：`kickpi-armbian/patch/u-boot/legacy/u-boot-radxa-rk35xx/`
- 通用 BSP 文件：`kickpi-armbian/packages/bsp/kickpi/`
- Rockchip 6.1 补丁：`kickpi-armbian/patch/kernel/rk35xx-vendor-6.1/`

## 1. 迁移总进度

- [x] 阶段 A：建立 `meta-kickpi` layer
- [x] 阶段 B：建立 K7 machine 配置
- [ ] 阶段 C：迁移 K7 Linux 设备树
- [ ] 阶段 D：启动内核并验证串口、存储和网络
- [ ] 阶段 E：迁移 K7 U-Boot
- [ ] 阶段 F：迁移 Wi-Fi/蓝牙固件与服务
- [ ] 阶段 G：迁移音频、4G 和其他外设
- [ ] 阶段 H：整理许可证、固定版本并做可复现构建
- [ ] 阶段 I：复用成果支持 K7C/K8/K1

每完成一个阶段都应提交一次 Git，避免多个问题混在一起。

## 2. 阶段 A：建立并纳管 meta-kickpi

远端仓库已经创建：

```text
https://github.com/Aclass-Linux/meta-kickpi.git
```

`meta-kickpi` 放在 `sources/` 下，与 `poky`、`meta-openembedded` 和
`meta-rockchip` 保持一致。它是独立 Git 仓库，同时由 repo manifest 纳管。

以下步骤假定远端仓库是空仓库。如果仓库中已经存在提交，先停止操作并将远端内容
克隆下来，不要使用 `git push --force` 覆盖远端。

### A1. 确认远端状态

```bash
git ls-remote https://github.com/Aclass-Linux/meta-kickpi.git
```

- 没有输出：远端是空仓库，可以继续 A2。
- 有 commit/hash 输出：远端已有内容，应先 clone 并确认内容后再继续。

完成标准：

- [x] 已确认远端仓库是否为空
- [x] 确认自己具有该仓库的 push 权限

### A2. 在 sources 下创建 layer

从 Yocto 工作区根目录执行：

```bash
cd ~/Documents/yocto
source sources/poky/oe-init-build-env build
bitbake-layers create-layer ../sources/meta-kickpi
rm -rf ../sources/meta-kickpi/recipes-example

bitbake-layers add-layer ../sources/meta-openembedded/meta-oe
bitbake-layers add-layer ../sources/meta-rockchip
bitbake-layers add-layer ../sources/meta-kickpi
bitbake-layers show-layers
```

`recipes-example` 只是 `create-layer` 生成的示例，检查无误后删除。若
`meta-openembedded/meta-oe` 或 `meta-rockchip` 已经出现在
`build/conf/bblayers.conf` 中，重复执行 `add-layer` 通常不需要。

完成标准：

- [x] `sources/meta-kickpi/conf/layer.conf` 存在
- [x] `bitbake-layers show-layers` 能看到 `meta-kickpi`
- [x] 没有 layer series compatibility 错误

建议最终结构：

```text
sources/meta-kickpi/
├── conf/machine/
├── recipes-bsp/u-boot/
├── recipes-bsp/rkbin/
├── recipes-kernel/linux/
├── recipes-kernel/firmware/
├── recipes-connectivity/kickpi-wifibt/
├── recipes-multimedia/alsa/
└── recipes-core/kickpi-hardware/
```

### A3. 调整 layer 元数据

先检查 `meta-rockchip` 声明的 collection。当前命令在 `build` 目录执行：

```bash
grep -E 'BBFILE_COLLECTIONS|LAYERSERIES_COMPAT' \
    ../sources/meta-rockchip/conf/layer.conf
```

当前仓库的实际输出为：

```bitbake
BBFILE_COLLECTIONS += "rockchip"
LAYERSERIES_COMPAT_rockchip = "scarthgap"
```

因此 `meta-rockchip` 的 collection 名是 `rockchip`。collection 是 BitBake
识别 layer 时使用的内部名称，不一定等于目录名或 Git 仓库名。

然后确认 `sources/meta-kickpi/conf/layer.conf` 至少包含：

```bitbake
BBPATH .= ":${LAYERDIR}"

BBFILES += "${LAYERDIR}/recipes-*/*/*.bb \
            ${LAYERDIR}/recipes-*/*/*.bbappend"

BBFILE_COLLECTIONS += "kickpi"
BBFILE_PATTERN_kickpi = "^${LAYERDIR}/"
BBFILE_PRIORITY_kickpi = "6"

LAYERDEPENDS_kickpi = "core rockchip"
LAYERSERIES_COMPAT_kickpi = "scarthgap"
```

#### `${LAYERDIR}`：当前 layer 根目录

`${LAYERDIR}` 由 BitBake 自动设置，表示 `layer.conf` 所在 layer 的根目录。
这里相当于 `/home/ryan/Documents/yocto/sources/meta-kickpi`。不要改成固定绝对
路径，否则移动工作区后配置将失效。

#### `BBPATH`：配置和 class 搜索路径

```bitbake
BBPATH .= ":${LAYERDIR}"
```

将当前 layer 加入 BitBake 的搜索路径，用于查找 `.conf`、`.bbclass` 以及被
`include` 或 `require` 的文件。它不负责发现普通 recipe。

#### `BBFILES`：recipe 和 bbappend 的位置

```bitbake
BBFILES += "${LAYERDIR}/recipes-*/*/*.bb \
            ${LAYERDIR}/recipes-*/*/*.bbappend"
```

告诉 BitBake 在 `recipes-*` 目录中查找完整 recipe（`.bb`）和对其他 recipe
进行追加修改的文件（`.bbappend`），例如：

```text
recipes-bsp/u-boot/u-boot_%.bbappend
recipes-kernel/linux/linux-rockchip_%.bbappend
recipes-core/images/kickpi-image.bb
```

#### `BBFILE_COLLECTIONS`：layer 的内部名称

```bitbake
BBFILE_COLLECTIONS += "kickpi"
```

将当前 layer 注册为 `kickpi` collection。以下变量的 `_kickpi` 后缀必须与
该名称完全一致：

```bitbake
BBFILE_PATTERN_kickpi
BBFILE_PRIORITY_kickpi
LAYERDEPENDS_kickpi
LAYERSERIES_COMPAT_kickpi
```

#### `BBFILE_PATTERN_kickpi`：collection 的文件范围

```bitbake
BBFILE_PATTERN_kickpi = "^${LAYERDIR}/"
```

这是正则表达式：`^` 表示从路径开头匹配，`${LAYERDIR}/` 表示文件必须位于
当前 layer 目录内。匹配的文件会被归入 `kickpi` collection。

#### `BBFILE_PRIORITY_kickpi`：layer 优先级

```bitbake
BBFILE_PRIORITY_kickpi = "6"
```

多个 layer 提供同名或相互竞争的 recipe 时，BitBake 会参考该优先级，数值
较高者优先。它不能替代 recipe 版本和 provider 选择；板级修改应优先采用
`.bbappend`，必要时再配置 `PREFERRED_VERSION` 或 `PREFERRED_PROVIDER`。

#### `LAYERDEPENDS_kickpi`：layer 依赖关系

```bitbake
LAYERDEPENDS_kickpi = "core rockchip"
```

声明 `meta-kickpi` 依赖 `core` 和 `rockchip` collection。这里必须填写
collection 名，不能填写目录名 `meta-rockchip`。缺少任意依赖时，BitBake
会在配置解析阶段报错。因此应先添加依赖层：

```bash
bitbake-layers add-layer ../sources/meta-rockchip
bitbake-layers add-layer ../sources/meta-kickpi
```

#### `LAYERSERIES_COMPAT_kickpi`：Yocto 系列兼容性

```bitbake
LAYERSERIES_COMPAT_kickpi = "scarthgap"
```

声明该 layer 已适配 Yocto Project 5.0 Scarthgap。这只是兼容性声明，不会
自动切换 Poky 或 `meta-rockchip` 分支；各仓库仍需由 repo manifest 固定到
经过验证的分支或 revision。

#### 常用赋值符号

- `=`：设置变量值。
- `+=`：以空格分隔并追加，适合列表变量。
- `.=`：直接连接字符串，不自动增加空格；这里用于向 `BBPATH` 追加 `:路径`。

名称对应关系：

| Git 仓库或目录 | BitBake collection |
| --- | --- |
| `meta-rockchip` | `rockchip` |
| `meta-kickpi` | `kickpi` |

完成标准：

- [x] `LAYERSERIES_COMPAT_kickpi` 包含 `scarthgap`
- [x] `LAYERDEPENDS_kickpi` 使用正确的 Rockchip collection 名称
- [x] `bitbake-layers show-layers` 无错误

### A4. 把 README 移入受管理仓库

Yocto 工作区根目录不是 Git 仓库，根目录中的普通 README 不会被 repo 保存。
迁移文档的真实文件放在 `meta-kickpi` 仓库中，并在工作区根目录创建相对软链接：

```bash
cd ~/Documents/yocto
mv README.md sources/meta-kickpi/README.md
ln -s sources/meta-kickpi/README.md README.md
```

如果文档已经位于 `sources/meta-kickpi`，只需要执行 `ln -s`。此后真实文件和
便捷入口分别为：

```text
sources/meta-kickpi/README.md  # 真实文件，由 meta-kickpi Git 仓库管理
README.md                      # 指向上述文件的相对软链接
```

使用相对链接而不是绝对链接，这样整个 Yocto 工作区移动到其他路径后链接仍然
有效。这里不使用硬链接：Git 和文件复制工具不容易体现硬链接关系，后续也更难
判断哪个路径是真实文件。

完成标准：

- [x] 迁移文档位于 `sources/meta-kickpi/README.md`
- [x] 根目录 `README.md` 是指向受管理文档的相对软链接
- [x] 工作区根目录不再保留一份无人管理的重复 README

### A5. 初始化 Git 并推送 scarthgap 分支

```bash
cd ~/Documents/yocto/sources/meta-kickpi
git init -b scarthgap
git remote add origin https://github.com/Aclass-Linux/meta-kickpi.git
git add .
git status
git commit -m "meta-kickpi: create initial Yocto layer"
git push -u origin scarthgap
```

如果当前 Git 版本不支持 `git init -b`，使用：

```bash
git init
git switch -c scarthgap
```

完成标准：

- [x] Git 远端指向 `Aclass-Linux/meta-kickpi.git`
- [x] `scarthgap` 远端分支包含最新提交
- [x] repo 接管前 `git status` 干净
- [x] GitHub 上能看到包含最新 layer 配置和迁移文档的提交

repo 接管后，项目通常以 detached HEAD 方式检出，而不是停留在本地
`scarthgap` 分支；只要 HEAD 对应 manifest 指定的 `scarthgap` revision，这属于
正常状态。当前验证提交为 `3fc3b8e`。

### A6. 用 local manifest 验证 repo 纳管

在正式修改 `SDK-Yocto` manifest 前，创建：

```text
.repo/local_manifests/meta-kickpi.xml
```

内容：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <project
    name="Aclass-Linux/meta-kickpi.git"
    path="sources/meta-kickpi"
    remote="github"
    revision="scarthgap" />
</manifest>
```

因为 `sources/meta-kickpi` 是手工初始化的，而 repo 还没有为它建立内部项目记录，
先把已推送且状态干净的工作树移作临时备份，再让 repo 重新检出。不要在存在未提交修改时执行：

```bash
cd ~/Documents/yocto
git -C sources/meta-kickpi status
mv sources/meta-kickpi /tmp/meta-kickpi-before-repo
repo list | grep meta-kickpi
repo sync sources/meta-kickpi
repo status
```

确认新检出的内容正确后，可以删除 `/tmp/meta-kickpi-before-repo`；出现异常则保留它用于恢复。

完成标准：

- [x] `repo list` 显示 `sources/meta-kickpi`
- [x] 单项目 `repo sync` 成功
- [x] `repo status` 没有意外修改

### A7. 加入正式 SDK-Yocto manifest

在 `Aclass-Linux/SDK-Yocto` manifest 仓库增加：

```text
custom/kickpi/kickpi-common.xml
```

内容与 A6 的 `<project>` 相同。K7 产品 manifest 为：

```text
custom/kickpi/kickpi-k7.xml
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <include name="common/base.xml" />
  <include name="vendors/rockchip/rockchip-soc.xml" />
  <include name="custom/kickpi/kickpi-common.xml" />
</manifest>
```

`default.xml` 指向 K7：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <include name="custom/kickpi/kickpi-k7.xml" />
</manifest>
```

提交并推送 manifest 后，用正式 manifest 重新初始化：

```bash
cd ~/Documents/yocto
repo init \
    -u https://github.com/Aclass-Linux/SDK-Yocto.git \
    -b main \
    -m default.xml
repo sync -j8
```

确认正式 manifest 已生效后，删除临时文件：

```bash
rm .repo/local_manifests/meta-kickpi.xml
repo list | grep meta-kickpi
```

完成标准：

- [x] `SDK-Yocto` 中存在正式 KickPi manifest
- [x] 新工作区可通过 `repo init` 和 `repo sync` 取得 `meta-kickpi`
- [x] 已删除重复的 local manifest
- [x] `meta-kickpi` layer 已创建并推送 `scarthgap` 分支
- [x] 正式 K7 manifest 已提交、推送并在当前工作区通过 `repo sync` 验证

## 3. 阶段 B：建立 K7 machine

### B1. 以 RK3576 EVB 为模板

参考：

```text
sources/meta-rockchip/conf/machine/rockchip-rk3576-evb.conf
sources/meta-rockchip/conf/machine/include/rk3576.inc
```

新建：

```text
sources/meta-kickpi/conf/machine/kickpi-k7.conf
```

初始内容建议：

```bitbake
#@TYPE: Machine
#@NAME: KickPi K7
#@DESCRIPTION: KickPi K7 based on Rockchip RK3576

require conf/machine/include/rk3576.inc

KERNEL_DEVICETREE = "rockchip/rk3576-kickpi-k7.dtb"
UBOOT_MACHINE = "kickpi-k7-rk3576_defconfig"

MACHINE_FEATURES:append = " wifi bluetooth pci screen touchscreen"
```

U-Boot 尚未迁移完成时，可以暂时沿用现有 RK3576 配置，先验证 Linux 编译：

```bitbake
UBOOT_MACHINE = "rk3576_defconfig"
```

### B2. 检查 machine 是否被识别

```bash
MACHINE=kickpi-k7 bitbake -e virtual/kernel | \
    grep -E '^(MACHINE|KERNEL_DEVICETREE|PREFERRED_PROVIDER_virtual/kernel|PN|PV)='
```

这条命令用于验证 BitBake 是否能识别 KickPi K7，以及它最终选择的内核、版本
和设备树。它只解析配置，不会真正编译内核。

命令可以拆成三部分：

1. `MACHINE=kickpi-k7`

   只为本次命令临时选择 `kickpi-k7`。BitBake 会在所有已加载的 layer 中查找
   `conf/machine/kickpi-k7.conf`，本工程对应：

   ```text
   sources/meta-kickpi/conf/machine/kickpi-k7.conf
   ```

   该写法不会修改 `build/conf/local.conf`。如果希望这个 build 目录以后默认
   构建 K7，可以在 `local.conf` 中设置：

   ```bitbake
   MACHINE = "kickpi-k7"
   ```

2. `bitbake -e`

   `-e` 表示解析 layer、machine、include、recipe、class 和 override 后，输出
   最终生效的 BitBake 环境变量。完整输出通常有数万行，因此使用 `grep` 只
   保留本阶段需要检查的变量。

3. `virtual/kernel`

   这是 BitBake 的虚拟内核目标，不是具体 recipe 名。多个内核 recipe 都可以
   提供它，`PREFERRED_PROVIDER_virtual/kernel` 决定最终采用哪一个。K7 通过
   `rk3576.inc` 继承 `rockchip-common.inc`，最终选择 `linux-rockchip`。

当前验证输出为：

```text
KERNEL_DEVICETREE="rockchip/rk3576-kickpi-k7.dtb"
MACHINE="kickpi-k7"
PN="linux-rockchip"
PREFERRED_PROVIDER_virtual/kernel="linux-rockchip"
PV="6.1"
```

各字段含义：

| 变量 | 验证内容 |
| --- | --- |
| `MACHINE` | `kickpi-k7.conf` 已被正确识别 |
| `KERNEL_DEVICETREE` | K7 的目标 DTB 名称正确 |
| `PN` | 实际解析的内核 recipe 是 `linux-rockchip` |
| `PREFERRED_PROVIDER_virtual/kernel` | 虚拟内核目标的首选 provider 正确 |
| `PV` | 当前选择的内核 recipe 版本为 6.1 |

解析配置和真正编译的区别：

```bash
# 只解析配置并输出变量，不编译
MACHINE=kickpi-k7 bitbake -e virtual/kernel

# 下载、配置并真正编译内核
MACHINE=kickpi-k7 bitbake virtual/kernel
```

阶段 B 先使用 `-e` 检查 machine、provider 和 DTB 变量，避免把 BitBake 配置
错误带到耗时更长的内核编译阶段。阶段 C 把 K7 DTS 加入内核源码后，再运行
真正的内核构建。

完成标准：

- [x] BitBake 能识别 `kickpi-k7`
- [x] kernel provider 为 `linux-rockchip` 6.1
- [x] `KERNEL_DEVICETREE` 指向 `rockchip/rk3576-kickpi-k7.dtb`

## 4. 阶段 C：迁移 K7 Linux 设备树

### C1. 收集 K7 DTS

K7 Linux 设备树来源为：

```text
kickpi-armbian/patch/kernel/rk35xx-vendor-6.1/dt/
```

为了便于阅读、学习和长期维护，四个板级源文件直接保存在 `meta-kickpi`，并
作为正式构建输入，而不是另存一份不参与构建的参考副本：

```text
sources/meta-kickpi/recipes-kernel/linux/files/kickpi-k7/
├── rk3576-kickpi-k7.dts
├── rk3576-kickpi-k7-cam0.dtsi
├── rk3576-kickpi-k7-cam1.dtsi
└── rk3576-kickpi-k7-cam3.dtsi
```

从 Yocto 工作区根目录执行的收集步骤：

```bash
mkdir -p sources/meta-kickpi/recipes-kernel/linux/files/kickpi-k7

cp \
    kickpi-armbian/patch/kernel/rk35xx-vendor-6.1/dt/rk3576-kickpi-k7.dts \
    kickpi-armbian/patch/kernel/rk35xx-vendor-6.1/dt/rk3576-kickpi-k7-cam0.dtsi \
    kickpi-armbian/patch/kernel/rk35xx-vendor-6.1/dt/rk3576-kickpi-k7-cam1.dtsi \
    kickpi-armbian/patch/kernel/rk35xx-vendor-6.1/dt/rk3576-kickpi-k7-cam3.dtsi \
    sources/meta-kickpi/recipes-kernel/linux/files/kickpi-k7/
```

目录不能通过 `bitbake` 命令自动生成；它是 layer 的源码结构，应由开发者创建
并提交到 Git。BitBake 的职责是在构建阶段读取这些文件，并放入临时内核源码
树。`bitbake-layers create-layer` 只能生成新 layer 骨架，不用于创建 recipe
内部的板级目录。

不要复制编译后的 DTB，也不要混入 `rk3576-kickpi-k7c*` 或 U-Boot 目录中的
同名 DTS。保留源文件原有 SPDX 和版权头。

主 DTS 还依赖 `rk3576.dtsi`、`rk3576-evb.dtsi`、`rk3576-rk806.dtsi` 和
`rk3576-linux.dtsi`；这些 SoC 公共文件应由 `linux-rockchip` 源码提供，不在
`meta-kickpi` 中重复保存。

#### 直接保存 DTS 与使用 patch 的区别

在 Yocto layer 中迁移设备树通常有两种方案。两种方案最终都会把文件放入
内核源码树再编译，区别在于 layer 中保存的“源文件形式”。

| 对比项 | 直接保存 DTS/DTSI | 把 DTS 放在 patch 中 |
| --- | --- | --- |
| layer 中保存的内容 | 可直接打开的 `.dts`、`.dtsi` | 包含新增文件和修改内容的 `.patch` |
| 接入方式 | bbappend 在任务中复制到 `${S}` | `SRC_URI` 在 `do_patch` 自动应用补丁 |
| 阅读体验 | 文件干净，适合学习节点、属性和 include | 每行通常带 `+`，阅读完整设备树较不方便 |
| 修改方式 | 直接编辑 DTS，Git 显示普通文件差异 | 在对应内核源码基线上修改后重新生成 patch |
| 基线依赖 | 文件本身较独立，但仍依赖公共 DTSI 接口 | patch 的上下文与目标内核提交紧密相关 |
| 上游提交 | 需要之后再从内核树生成正式提交 | patch 本身接近内核邮件或 Git 提交格式 |
| 原子性 | DTS 和 Makefile 接入逻辑可能分开维护 | 新文件与 Makefile 修改可以放在同一提交中 |
| 多内核维护 | 可以复用文件，但必须逐版本验证公共节点 | 通常每个内核版本维护一套可以应用的 patch |

使用 patch 的典型结构：

```text
recipes-kernel/linux/
├── linux-rockchip_6.1.bbappend
└── linux-rockchip/
    └── 0001-arm64-dts-rockchip-add-kickpi-k7.patch
```

patch 可以一次完成：

```text
新增 rk3576-kickpi-k7.dts
新增三个 camera dtsi
修改 arch/arm64/boot/dts/rockchip/Makefile
```

这种方式最符合 Linux 内核的提交习惯，也便于未来把改动提交到内核仓库。但
patch 必须基于 Yocto 实际使用的内核提交生成；内核基线变化后，可能出现补丁
上下文无法应用，需要重新 rebase 或生成补丁。

直接保存 DTS 的典型结构：

```text
recipes-kernel/linux/files/kickpi-k7/
├── rk3576-kickpi-k7.dts
├── rk3576-kickpi-k7-cam0.dtsi
├── rk3576-kickpi-k7-cam1.dtsi
└── rk3576-kickpi-k7-cam3.dtsi
```

这种方式适合当前项目，因为现阶段需要长期阅读、学习并逐项调整 K7 设备树。
文件同时是 BitBake 的正式输入，不是仅供参考的副本。修改一个节点时直接编辑
对应 DTS/DTSI，Git 也能清楚显示变化。

当前 `meta-kickpi` 采用“可读 DTS + bbappend 接入”的方案：

```text
meta-kickpi 中可读的 DTS/DTSI
        │
        │ SRC_URI + do_patch:append:kickpi-k7
        ▼
${S}/arch/arm64/boot/dts/rockchip/
        │
        ▼
编译 rk3576-kickpi-k7.dtb
```

不要同时维护一套可读 DTS 和一份包含相同 DTS 全文的 patch，否则会出现两个
修改来源并逐渐不一致。当前方案只让 bbappend 负责复制文件和登记 Makefile
目标；四个可读 DTS/DTSI 是唯一可信的板级设备树源码。

将来设备树稳定并准备提交到 Linux/Rockchip 内核仓库时，可以在实际内核 Git
工作树中提交这些文件和 Makefile 修改，再用 `git format-patch` 生成一个正式
上游 patch；届时应选择“继续维护可读 DTS”或“切换为 patch”之一，而不是长期
并存两套相同内容。

#### 在实际 linux-rockchip 基线上生成 patch

不要手写包含 DTS 的大 patch。标准做法是在 Yocto 实际选择的内核 Git 基线上
形成一次提交，再使用 `git format-patch`，从而保证 Makefile 上下文、文件路径
和提交元数据正确。

1. 初始化环境并确认内核版本：

   ```bash
   cd ~/Documents/yocto
   source sources/poky/oe-init-build-env build

   MACHINE=kickpi-k7 bitbake -e virtual/kernel | \
       grep -E '^(PN|PV|SRCREV)='
   ```

   应确认 `PN` 为 `linux-rockchip`、`PV` 为 `6.1`。patch 必须基于 recipe 当前
   固定的 `SRCREV` 生成，不能随意使用其他 Linux 6.1 源码。

2. 使用 devtool 创建内核开发工作区：

   ```bash
   MACHINE=kickpi-k7 devtool modify linux-rockchip
   cd ~/Documents/yocto/build/workspace/sources/linux-rockchip
   git status
   git log -1 --oneline
   ```

   首次执行会下载较大的 Rockchip vendor 内核仓库。源码通常位于
   `build/workspace/sources/linux-rockchip/`。

3. 确认 K7 依赖的公共 RK3576 文件存在：

   ```bash
   ls arch/arm64/boot/dts/rockchip/rk3576.dtsi
   ls arch/arm64/boot/dts/rockchip/rk3576-evb.dtsi
   ls arch/arm64/boot/dts/rockchip/rk3576-rk806.dtsi
   ls arch/arm64/boot/dts/rockchip/rk3576-linux.dtsi
   ```

   任一文件缺失都表示内核基线与 K7 DTS 不兼容，应先解决基线问题，不能直接
   生成 patch。

4. 如果 bbappend 尚未自动放入 DTS，则从 `meta-kickpi` 复制四个文件：

   ```bash
   cp \
       ~/Documents/yocto/sources/meta-kickpi/recipes-kernel/linux/files/kickpi-k7/rk3576-kickpi-k7.dts \
       ~/Documents/yocto/sources/meta-kickpi/recipes-kernel/linux/files/kickpi-k7/rk3576-kickpi-k7-cam0.dtsi \
       ~/Documents/yocto/sources/meta-kickpi/recipes-kernel/linux/files/kickpi-k7/rk3576-kickpi-k7-cam1.dtsi \
       ~/Documents/yocto/sources/meta-kickpi/recipes-kernel/linux/files/kickpi-k7/rk3576-kickpi-k7-cam3.dtsi \
       arch/arm64/boot/dts/rockchip/
   ```

5. 在 `arch/arm64/boot/dts/rockchip/Makefile` 的其他 RK3576 条目附近加入：

   ```makefile
   dtb-$(CONFIG_ARCH_ROCKCHIP) += rk3576-kickpi-k7.dtb
   ```

   确认目标只出现一次：

   ```bash
   grep -n 'rk3576-kickpi-k7.dtb' \
       arch/arm64/boot/dts/rockchip/Makefile
   ```

6. 只暂存 K7 相关文件并检查改动：

   ```bash
   git add \
       arch/arm64/boot/dts/rockchip/Makefile \
       arch/arm64/boot/dts/rockchip/rk3576-kickpi-k7.dts \
       arch/arm64/boot/dts/rockchip/rk3576-kickpi-k7-cam0.dtsi \
       arch/arm64/boot/dts/rockchip/rk3576-kickpi-k7-cam1.dtsi \
       arch/arm64/boot/dts/rockchip/rk3576-kickpi-k7-cam3.dtsi

   git diff --cached --stat
   git diff --cached --check
   ```

   `git diff --cached --check` 必须没有空白错误。还应确认没有暂存其他内核或
   devtool 生成的无关修改。

7. 创建带签名的内核提交：

   ```bash
   git commit -s
   ```

   建议提交信息：

   ```text
   arm64: dts: rockchip: add KickPi K7

   Add the board device tree and camera descriptions for the
   Rockchip RK3576-based KickPi K7.

   Upstream-Status: Pending
   ```

   `git commit -s` 会自动添加 `Signed-off-by`。提交后检查：

   ```bash
   git show --stat --oneline HEAD
   git show --check HEAD
   ```

8. 生成并保存 patch：

   ```bash
   mkdir -p \
       ~/Documents/yocto/sources/meta-kickpi/recipes-kernel/linux/files/patches

   git format-patch -1 HEAD \
       -o ~/Documents/yocto/sources/meta-kickpi/recipes-kernel/linux/files/patches
   ```

   统一后的目标文件名建议为：

   ```text
   recipes-kernel/linux/files/patches/
   └── 0001-arm64-dts-rockchip-add-kickpi-k7.patch
   ```

9. 检查 patch 元数据和文件范围：

   ```bash
   grep -E '^Subject:|^Upstream-Status:|^Signed-off-by:' \
       ~/Documents/yocto/sources/meta-kickpi/recipes-kernel/linux/files/patches/0001-*.patch

   grep '^diff --git' \
       ~/Documents/yocto/sources/meta-kickpi/recipes-kernel/linux/files/patches/0001-*.patch
   ```

   patch 应只包含 DTS Makefile、K7 主 DTS 和三个 camera DTSI。

10. 确认 patch 已保存在 `meta-kickpi` 后退出 devtool 工作区：

    ```bash
    cd ~/Documents/yocto/build
    devtool reset linux-rockchip
    ```

11. 将 bbappend 切换到 patch 方案后验证：

    ```bash
    MACHINE=kickpi-k7 bitbake -c clean virtual/kernel
    MACHINE=kickpi-k7 bitbake -c patch virtual/kernel
    ```

    `do_patch` 成功后，再检查临时内核源码中是否存在四个 K7 文件，以及 Makefile
    是否包含 `rk3576-kickpi-k7.dtb`。

当前仓库尚未生成上述 patch，因此现在只能使用已经接入的可读 DTS 方案。只有
patch 文件生成、bbappend 增加互斥选择逻辑并通过 `do_patch` 验证后，才能启用
patch 模式。

### C2. 把保存的 DTS 接入 linux-rockchip 6.1

K7 文件来自 Rockchip vendor 6.1 内核，因此使用版本明确的 bbappend，不假定
它能直接兼容其他内核版本：

```text
sources/meta-kickpi/recipes-kernel/linux/
├── linux-rockchip_6.1.bbappend
└── files/kickpi-k7/
    ├── rk3576-kickpi-k7.dts
    ├── rk3576-kickpi-k7-cam0.dtsi
    ├── rk3576-kickpi-k7-cam1.dtsi
    └── rk3576-kickpi-k7-cam3.dtsi
```

`linux-rockchip_6.1.bbappend` 内容：

```bitbake
FILESEXTRAPATHS:prepend := "${THISDIR}/files/kickpi-k7:"

SRC_URI:append = " \
    file://rk3576-kickpi-k7.dts;subdir=kickpi-k7 \
    file://rk3576-kickpi-k7-cam0.dtsi;subdir=kickpi-k7 \
    file://rk3576-kickpi-k7-cam1.dtsi;subdir=kickpi-k7 \
    file://rk3576-kickpi-k7-cam3.dtsi;subdir=kickpi-k7 \
"

do_patch:append:kickpi-k7() {
    dts_dir="${S}/arch/arm64/boot/dts/rockchip"

    install -m 0644 \
        "${WORKDIR}/kickpi-k7/rk3576-kickpi-k7.dts" \
        "${WORKDIR}/kickpi-k7/rk3576-kickpi-k7-cam0.dtsi" \
        "${WORKDIR}/kickpi-k7/rk3576-kickpi-k7-cam1.dtsi" \
        "${WORKDIR}/kickpi-k7/rk3576-kickpi-k7-cam3.dtsi" \
        "${dts_dir}/"

    if ! grep -q 'rk3576-kickpi-k7\.dtb' "${dts_dir}/Makefile"; then
        echo 'dtb-$(CONFIG_ARCH_ROCKCHIP) += rk3576-kickpi-k7.dtb' \
            >> "${dts_dir}/Makefile"
    fi
}
```

这里的处理流程为：

1. `FILESEXTRAPATHS` 让 BitBake 能找到 layer 中保存的 DTS/DTSI。
2. `SRC_URI` 将四个文件放入 `${WORKDIR}/kickpi-k7/`。
3. `do_patch:append:kickpi-k7` 只对 K7 machine 生效，把文件复制到实际内核
   源码的 `arch/arm64/boot/dts/rockchip/`。
4. 如果 Makefile 尚未包含 K7，则追加 K7 DTB 构建目标。

最终内核构建应生成：

```text
rockchip/rk3576-kickpi-k7.dtb
```

验证 bbappend 是否被识别：

```bash
MACHINE=kickpi-k7 bitbake-layers show-appends | grep linux-rockchip
MACHINE=kickpi-k7 bitbake -e virtual/kernel | \
    grep '^SRC_URI=' | grep rk3576-kickpi-k7
```

### C3. 只编译设备树/内核

```bash
MACHINE=kickpi-k7 bitbake virtual/kernel -c clean
MACHINE=kickpi-k7 bitbake virtual/kernel
find build/tmp/deploy/images/kickpi-k7 -name '*kickpi*k7*.dtb' -o -name 'Image*'
```

完成标准：

- [ ] 补丁能干净应用
- [ ] DTS 没有缺失 label/include
- [ ] 生成 `rk3576-kickpi-k7.dtb`
- [ ] `dtc` 没有新的严重 warning

> 注意：Armbian 使用 `armbian/linux-rockchip` 的 `rk-6.1-rkr5.1`，当前 `meta-rockchip` 使用 JeffyCN 的 6.1 固定提交。DTS 节点名、phandle 和驱动接口可能不同，不能假定原文件可直接编译。

## 5. 阶段 D：最小镜像启动验证

先构建无桌面的最小镜像：

```bash
MACHINE=kickpi-k7 bitbake core-image-minimal
```

按实际输出格式写入 SD 卡。写盘前必须通过 `lsblk` 确认目标设备，禁止猜测设备名。

首次启动依次验证：

- [ ] U-Boot 能加载 `Image` 和 K7 DTB
- [ ] 串口控制台可登录
- [ ] CPU/内存容量正确
- [ ] SD 卡可读写
- [ ] eMMC 被识别
- [ ] 有线网卡被识别并能 DHCP
- [ ] USB Host 可识别设备
- [ ] 重启和关机正常

目标板收集日志：

```bash
cat /proc/device-tree/model
tr '\0' '\n' </proc/device-tree/compatible
uname -a
dmesg > /tmp/kickpi-k7-dmesg.txt
lsblk
ip link
lspci -nn
lsusb
```

在以上项目完成前，不要开始桌面、摄像头或 NPU 迁移。

## 6. 阶段 E：迁移 K7 U-Boot

Armbian K7 使用 Radxa `next-dev-v2024.10` vendor U-Boot，而当前 `meta-rockchip` 使用较旧的 Rockchip vendor U-Boot。推荐新增独立 recipe，例如：

```text
sources/meta-kickpi/recipes-bsp/u-boot/u-boot-kickpi-radxa.bb
```

需要迁移：

```text
kickpi-armbian/patch/u-boot/legacy/u-boot-radxa-rk35xx/defconfig/kickpi-k7-rk3576_defconfig
kickpi-armbian/patch/u-boot/legacy/u-boot-radxa-rk35xx/dt/rk3576-kickpi-k7.dts
```

同时确认：

- BL31：RK3576 对应版本
- DDR/TPL blob：优先使用 K7 指定的 `rk3576_ddr_lp4_1560MHz_lp5_2736MHz_v1.08.bin`
- `u-boot-rockchip.bin` 的生成方式
- Armbian 写盘偏移为 `bs=32k seek=1`，即从 32 KiB 写入
- Yocto WIC 布局不能覆盖 GPT 和 U-Boot

完成标准：

- [ ] Radxa vendor U-Boot 能稳定启动
- [ ] SD 和 eMMC 都能加载系统
- [ ] DTB 选择正确
- [ ] Ethernet MAC 地址稳定
- [ ] `saveenv` 不破坏分区
- [ ] Yocto WIC 镜像可直接启动

## 7. 阶段 F：Wi-Fi 和蓝牙

来源：

```text
kickpi-armbian/packages/bsp/kickpi/rtl_bt/
kickpi-armbian/packages/bsp/kickpi/usr/lib/firmware/
kickpi-armbian/packages/bsp/kickpi/bin/wifibt-*.sh
kickpi-armbian/packages/bsp/kickpi/bin/rtk_hciattach
```

拆分为两个 recipe：

1. `kickpi-firmware.bb`：只安装固件和配置文件。
2. `kickpi-wifibt.bb`：安装初始化程序和 systemd service。

不要直接照搬脚本中的以下行为：

- systemd 服务中调用 `sudo`
- 无条件 `rmmod`
- 使用 `ifconfig`/`ifup` 管理网络
- 后台启动进程后立刻让 oneshot service 成功退出
- 假定 rfkill 一定是 `rfkill0`

二进制 `rtk_hciattach` 是预编译 AArch64 ELF。迁移前记录许可证和来源；如果能取得源码，应改为 Yocto 交叉编译。

完成标准：

- [ ] 固件安装到 `${nonarch_base_libdir}/firmware`
- [ ] Wi-Fi 驱动正常加载
- [ ] `ip link` 出现 WLAN 接口
- [ ] 能扫描和连接 AP
- [ ] `bluetoothctl list` 能看到控制器
- [ ] Wi-Fi 与蓝牙可同时工作
- [ ] 重启 10 次初始化均成功

## 8. 阶段 G：其他 BSP 功能

按以下顺序逐项迁移，每项独立 recipe 或补丁：

### G1. 音频

- [ ] 安装 ES8388 UCM2 配置
- [ ] 添加音频 udev 命名规则
- [ ] 验证 HDMI Audio
- [ ] 验证板载 ES8388 播放/录音

来源：`kickpi-armbian/packages/bsp/kickpi/usr/share/alsa/ucm2/`

### G2. 4G Modem

- [ ] 确认 USB 串口枚举
- [ ] 优先使用 ModemManager/NetworkManager
- [ ] 如必须使用 `quectel-CM`，单独建立 recipe 和 service
- [ ] 确认 `quectel-CM` 的源码、许可证及 glibc 兼容性

### G3. 显示和触摸

- [ ] HDMI
- [ ] MIPI-DSI LCD
- [ ] FT8756/GT9xx 触摸
- [ ] 背光和休眠唤醒

### G4. 摄像头和多媒体

- [ ] Sensor 探测
- [ ] MIPI CSI 链路
- [ ] ISP
- [ ] GStreamer 采集

### G5. GPU/NPU

- [ ] Mali 驱动和用户空间版本匹配
- [ ] EGL/OpenGL ES 验证
- [ ] RKNPU 驱动与 runtime 版本匹配

## 9. 阶段 H：工程化整理

- [ ] 所有 Git 源固定 `SRCREV`
- [ ] 所有下载文件填写正确 checksum
- [ ] 每个 recipe 设置准确 `LICENSE` 和 `LIC_FILES_CHKSUM`
- [ ] 闭源固件确认再分发权限
- [ ] 删除 root/root、kickpi/kickpi 等默认密码
- [ ] 禁止在镜像中保留无必要的调试服务
- [ ] 执行 `bitbake -c cleansstate` 后完整重建
- [ ] 保存串口启动日志和测试结果
- [ ] 生成 SBOM 或 license manifest

## 10. 阶段 I：K7C/K8/K1

K7 稳定后复用 `meta-kickpi`：

| Machine | SoC include | DTB |
|---|---|---|
| `kickpi-k7c` | `rk3576.inc` | `rockchip/rk3576-kickpi-k7c.dtb` |
| `kickpi-k8` | `rk3588.inc` | `rockchip/rk3588-kickpi-k8.dtb` |
| `kickpi-k1` | `rk356x.inc` | `rockchip/rk3568-kickpi-k1.dtb` |

额外注意：

- K7/K7C 需要匹配的 RK3576 DDR blob 和 BL31。
- K7/K7C 包含多个摄像头与 LCD overlay，应在 K7 基础启动稳定后逐个启用。
- K8 的 HDMI/DP/HDMI-In/多屏配置复杂，应拆分验证。
- K8 Armbian 配置基于主线 U-Boot v2024.04，不要与 K7 的 Radxa vendor U-Boot 配置混用。
- K1 使用 RK3568 和主线 U-Boot v2025.04，应作为后续独立 machine 迁移。
- TX6S 是 Allwinner H618，不属于 `meta-rockchip`，应作为独立项目使用 Sunxi layer 迁移。

## 11. 每一步的记录模板

完成任务后在提交信息或单独日志中记录：

```text
任务：
板卡：
源码版本/SRCREV：
修改文件：
构建命令：
构建结果：
硬件验证：
已知问题：
下一步：
```

## 12. 第一条实际任务

从这里开始：

- [x] 执行阶段 A，创建 `meta-kickpi` 并推送 `scarthgap` 分支
- [x] 提交并推送正式 K7 manifest，然后执行 `repo sync`
- [x] 创建 `sources/meta-kickpi/conf/machine/kickpi-k7.conf`
- [x] 运行 `MACHINE=kickpi-k7 bitbake -e virtual/kernel`

这三项通过后，再开始修改 Linux 设备树。这样能先确认 layer、machine 和 provider 关系正确，避免把 BitBake 配置问题误判成内核问题。
