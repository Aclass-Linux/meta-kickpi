# 从零认识当前 K7 Yocto 工程

这是一份可以独立阅读的入门教材，不是新的迁移进度表。学习顺序和实验验收仍由
[LEARNING.md](../LEARNING.md) 维护。本篇结合当前工作区解释文件之间的关系，
不要求删除已有代码，也不要求先运行一次完整编译。

阅读目标：能解释“源码从哪里来、哪些 layer 被启用、选择哪块板子、构建什么”。
文中路径以 `/home/ryan/Documents/yocto` 为例；换电脑后应使用自己的实际路径。

## 1. 先建立整体认识

Yocto Project 提供构建定制 Linux 系统的方法和工具生态；它不是一个直接安装到
板子上的现成系统。当前工程使用 Poky 提供的集成环境，其中包含 BitBake 和
OpenEmbedded-Core 等内容。

各工具的分工是：

| 名称 | 在本工程中的职责 | 不负责什么 |
| --- | --- | --- |
| Git | 管理单个仓库的文件、提交和分支 | 不自动协调全部源码仓库 |
| repo | 按 XML 清单管理多个 Git 仓库 | 不编译内核或镜像 |
| BitBake | 解析配置和配方，按依赖执行任务 | 不凭空知道板卡硬件细节 |
| layer | 组织配方、配置和定制内容 | 不是一个独立的编译程序 |
| machine 配置 | 描述目标板卡相关的构建选择 | 不等同于设备树 |
| image 配方 | 定义要组合成什么系统镜像 | 不等同于 Linux 内核 |

完整关系可以先记成：

```text
manifest → repo 下载多个仓库
                    ↓
bblayers.conf → 启用其中的 layer → layer.conf 提供搜索规则等
                    ↓
local.conf → MACHINE / DISTRO 等构建选择
                    ↓
BitBake 解析目标配方和依赖 → 执行任务 → 内核、DTB、软件包、rootfs 等
```

这是理解关系的示意，不是 BitBake 内部精确的逐文件解析顺序。

## 2. 工作区、仓库和 layer 不是一回事

当前目录结构：

```text
yocto/
├── .repo/                     repo 管理数据和清单仓库
├── README.md                  指向 sources/meta-kickpi/README.md 的软链接
├── sources/
│   ├── poky/                  一个 Git 仓库，包含多个 layer
│   ├── meta-openembedded/     一个 Git 仓库，包含多个 layer
│   ├── meta-rockchip/         Rockchip 支持仓库
│   └── meta-kickpi/           我们维护的 K7 支持仓库和 layer
├── build/                     本地构建目录
└── kickpi-armbian/             参考工程，不在当前 repo list 中
```

工作区是容纳这些内容的目录，不代表它本身就是一个统一的 Git 仓库。
仓库按版本管理边界划分，layer 按构建元数据的组织方式划分：一个仓库可以有多个
layer；当前 `meta-kickpi` 则既是一个仓库，也是一个 layer。

`README.md` 的软链接不是文件副本。通过它编辑，修改的是 layer 仓库里的 README。
软链接被删除不等于目标文件被删除；本教程不要求删除任何文件。

查看命令：

```bash
cd /home/ryan/Documents/yocto
pwd
ls -la
repo list
git -C sources/meta-kickpi status -sb
```

`repo list` 的每行是“本地路径 : 项目名称”。项目名称还需要与 remote 的地址组合，
才能确定完整下载地址。repo 的启动器更新提示不等于仓库列表读取失败。

## 3. manifest：决定有哪些源码仓库

当前包含关系：

```text
.repo/manifest.xml
└── default.xml
    └── custom/kickpi/kickpi-k7.xml
        ├── common/base.xml
        ├── vendors/rockchip/rockchip-soc.xml
        └── custom/kickpi/kickpi-common.xml
```

入口 `.repo/manifest.xml` 在当前工作区是 repo 自动生成的普通文件，不要直接修改。
其余这些文件位于 `.repo/manifests/` 清单仓库中。

例如：

```xml
<include name="common/base.xml" />
```

`include` 包含另一份清单，路径相对于清单仓库根目录，不是相对于当前 XML
所在的子目录。`common`、`vendors`、`custom` 是本工程的组织约定，不是 repo
要求必须使用的目录名。

基础清单中的关键内容：

```xml
<remote name="github" fetch="https://github.com/" />
<default remote="github" revision="scarthgap" sync-j="8" />
<project name="yoctoproject/poky.git"
         path="sources/poky"
         remote="github"
         revision="scarthgap" />
```

| 字段 | 含义 |
| --- | --- |
| `remote name` | 给下载来源起一个可引用的名字 |
| `fetch` | 下载来源的地址前缀 |
| `default` | 为没有显式设置对应字段的项目提供默认值 |
| `project name` | 远端项目名称；本例与地址前缀组合成 Poky 的 GitHub 地址 |
| `path` | 仓库放在工作区的哪个位置 |
| `revision` | 要跟踪的分支、标签或提交；本例为 scarthgap 分支 |
| `sync-j` | repo 同步的默认并发设置，不是编译线程数 |

当前四个项目都显式指定 `scarthgap`，所以只修改 default 的 revision 不会改变
它们的选择。分支会移动；使用分支名不等于锁定精确提交。
基础清单还定义了 `yocto` remote，但这四个项目目前都使用 `github`。

`repo sync` 会实际同步仓库，可能更新检出的版本，不是只读检查命令。
学习当前结构时不必反复执行。清单默认包含 K7，只选择了仓库组合，
**不会自动替你设置 BitBake 的 MACHINE**。

## 4. bblayers.conf：决定启用哪些 layer

当前 `build/conf/bblayers.conf` 的核心配置是：

```bitbake
POKY_BBLAYERS_CONF_VERSION = "2"
BBPATH = "${TOPDIR}"
BBFILES ?= ""

BBLAYERS ?= " \
    /home/ryan/Documents/yocto/sources/poky/meta \
    /home/ryan/Documents/yocto/sources/poky/meta-poky \
    /home/ryan/Documents/yocto/sources/poky/meta-yocto-bsp \
    /home/ryan/Documents/yocto/sources/meta-openembedded/meta-oe \
    /home/ryan/Documents/yocto/sources/meta-rockchip \
    /home/ryan/Documents/yocto/sources/meta-kickpi \
"
```

| 变量 | 作用 |
| --- | --- |
| `POKY_BBLAYERS_CONF_VERSION` | Poky 检查配置格式兼容性的标记；不是 Yocto 版本或 layer 数量 |
| `TOPDIR` | 当前构建目录，本例为工作区下的 build |
| `BBPATH` | 配置文件和类文件等的搜索路径；各 layer 通常会继续添加自身路径 |
| `BBFILES` | 配方及追加文件的匹配规则；此处默认置空，随后由 layer 添加 |
| `BBLAYERS` | 本次构建启用的 layer 目录列表 |

六个 layer 的分工：

| layer | 作用 |
| --- | --- |
| `poky/meta` | OpenEmbedded-Core，基础配方和构建机制 |
| `poky/meta-poky` | Poky 发行版配置 |
| `poky/meta-yocto-bsp` | Yocto 参考板卡支持，不是 K7 专用支持 |
| `meta-openembedded/meta-oe` | 额外软件配方 |
| `meta-rockchip` | Rockchip 平台支持 |
| `meta-kickpi` | 本工程的 K7 配置和定制 |

所以 repo 管理四个仓库，而这里启用了六个 layer，并不矛盾。
BitBake 会读取启用 layer 中的 `conf/layer.conf`。
启用 layer 只是让它提供的元数据可被使用，不会编译它包含的全部软件。
列表的上下顺序也不能简单解释成“越靠后优先级越高”。

## 5. 先看懂 BitBake 配置语法

这些文件不是 shell 脚本，不要直接用 bash 执行或 source 它们。

| 写法 | 含义 | 注意事项 |
| --- | --- | --- |
| `A = "value"` | 赋值，引用的变量通常延迟展开 | 不等于值从此无法被其他配置修改 |
| `A ?= "value"` | 尚未定义时提供默认值 | 已定义为空也算已定义 |
| `A ??= "value"` | 提供弱默认值 | 优先级弱于普通赋值和 `?=` |
| `A := "${B}"` | 在此处立即展开右侧变量 | 与 `=` 的展开时机不同 |
| `A += "value"` | 追加并自动加分隔空格 | 常用于空格分隔的列表 |
| `A .= "value"` | 追加，不自动加空格 | 常用于自行控制分隔符的路径 |
| `A:append = " value"` | 展开时追加内容 | 不自动加空格，通常需要自己写前导空格 |
| `A:prepend = "value "` | 展开时前置内容 | 同样要自己处理分隔符 |
| `${A}` | 引用变量 | 不是 shell 的命令替换 |

行尾 `\` 用来续行；`#` 开始注释。
例如 `MACHINE_FEATURES:append = " wifi"` 中的空格是有意义的，避免与原值连在一起。

## 6. layer.conf：一个 layer 如何接入构建系统

当前 `sources/meta-kickpi/conf/layer.conf`：

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

| 变量 | 当前配置的含义 |
| --- | --- |
| `LAYERDIR` | 解析该 layer 配置时的 layer 目录 |
| `BBPATH` | 将该目录加入搜索路径，使用冒号分隔 |
| `BBFILES` | 搜索 recipes-* 下指定层级的 `.bb` 和 `.bbappend` 文件 |
| `BBFILE_COLLECTIONS` | 给 layer 的配方集合声明标识，这里叫 `kickpi` |
| `BBFILE_PATTERN_kickpi` | 用正则表达式将路径归入 kickpi 集合；`^` 表示路径开头 |
| `BBFILE_PRIORITY_kickpi` | 配方集合优先级；存在重复配方时影响选择，不是“所有变量都覆盖”的开关 |
| `LAYERDEPENDS_kickpi` | 声明依赖 core、rockchip 两个 collection，缺少依赖会报错，不会自动下载 |
| `LAYERSERIES_COMPAT_kickpi` | 声明维护者认为此 layer 兼容 scarthgap 系列；不等于全部硬件已验证 |

collection 是内部标识，不必与目录名相同：目录叫 `meta-kickpi`，当前 collection
叫 `kickpi`。上述带后缀的变量必须使用同一个标识。
`core` 对应 `poky/meta` 的 collection，`rockchip` 对应 meta-rockchip 的 collection。

`LAYERVERSION_kickpi` 可以用来声明 layer 版本，配合带版本约束的依赖使用，
但不是每个 layer 都必须填写；也不要把它与 scarthgap 这样的兼容系列混为一谈。

创建 layer 的工具可以生成模板，但模板不知道你的真实依赖、板卡和兼容情况。
自动生成的配置仍需要维护者检查。

## 7. local.conf 与 machine：决定为谁构建

`build/conf/local.conf` 保存本构建目录的本地选择，当前包含：

```bitbake
MACHINE ??= "kickpi-k7"
DISTRO ?= "poky"
```

`MACHINE` 选择板卡配置；`DISTRO` 选择发行版策略，二者不同。
镜像目标则由类似 `bitbake core-image-minimal` 的命令指定，不是由 MACHINE 指定。

K7 对应的 `sources/meta-kickpi/conf/machine/kickpi-k7.conf`：

```bitbake
require conf/machine/include/rk3576.inc

KERNEL_DEVICETREE = "rockchip/rk3576-kickpi-k7.dtb"
UBOOT_MACHINE = "rk3576_defconfig"
RK_UBOOT_SPL = "1"

MACHINE_FEATURES:append = " wifi bluetooth pci screen touchscreen"
```

这里的 `require` 通过配置搜索路径找到公共 RK3576 配置，可以跨 layer 查找，
不是只能在 meta-kickpi 内查找；找不到会报错。

- `KERNEL_DEVICETREE`：指定需要构建和部署的 DTB，不能仅靠这一行创建对应 DTS。
- `UBOOT_MACHINE`：指定 U-Boot 的 defconfig。能选中通用配置不代表已验证 K7 启动。
- `RK_UBOOT_SPL`：Rockchip BSP 特有的启动组件配置开关，具体效果由使用它的 BSP 配方决定。
- `MACHINE_FEATURES`：声明机器能力，供配方作条件选择；写上 wifi 不会自动补齐驱动、固件或硬件连接。

## 8. recipe 与 bbappend：决定怎么构建

`.bb` 是配方，描述某个组件的来源、版本、依赖和构建方法。
`.bbappend` 是对匹配配方的追加或修改，不是一份独立替代配方。

本工程使用 meta-rockchip 的 `linux-rockchip_6.1.bb`，并通过本 layer 中的
`linux-rockchip_6.1.bbappend` 添加 K7 设备树。

当前追加文件主要做三件事：

1. `FILESEXTRAPATHS:prepend` 添加本地设备树文件的搜索目录。
2. `SRC_URI:append` 加入四个 `file://` DTS/DTSI，交由获取、解包流程处理。
3. `do_patch:append:kickpi-k7()` 在 K7 的补丁任务后半部分复制文件到内核源码，
   并补充 Makefile 中的 K7 DTB 目标。

`SRC_URI` 可以同时包含远端源码、本地文件和补丁。文件进入 SRC_URI 仅说明构建系统
知道该获取它，不等于它已经进入正确的内核目录，更不等于设备树能够编译。
当前没有实现 files/patch 自动切换开关；patch 方法另见
[设备树教程](device-tree.md)。

## 9. BitBake 命令：查看配置与执行任务要分清

先在当前终端初始化环境：

```bash
cd /home/ryan/Documents/yocto
source sources/poky/oe-init-build-env build
```

这个 shell 脚本设置环境，并进入 build 目录；新终端通常需要重新初始化。
这里 source 的是环境脚本，不是前面的 `.conf` 文件。

先运行查看命令，不急着编译：

```bash
bitbake-layers show-layers
bitbake-layers show-appends
MACHINE=kickpi-k7 bitbake -e virtual/kernel | \
    grep -E '^(MACHINE|PN|PV|S|B|WORKDIR|KERNEL_DEVICETREE)='
```

`show-layers` 查看已启用 layer；`show-appends` 查看追加文件的匹配情况。
`-e` 输出解析后的环境，不执行目标的编译任务，但仍需正常解析配置，可能生成缓存。
`grep` 只过滤输出内容，不改变 BitBake 的行为。

拆解最后一个命令：

| 部分 | 含义 |
| --- | --- |
| `MACHINE=kickpi-k7` | 为本次命令提供机器选择，不修改 local.conf；最终值仍受配置赋值规则影响 |
| `bitbake` | 调用构建工具 |
| `-e` | 查看解析环境 |
| `virtual/kernel` | 内核这一抽象构建目标，由配置选择具体 provider；当前为 linux-rockchip |

去掉 `-e` 不是“减少输出”，而是开始执行目标的默认构建任务。
`fetch virtual/kernel` 本身也不是完整的 BitBake 命令，任务应通过 `-c` 指定：

| 命令 | 用途 |
| --- | --- |
| `bitbake -c fetch virtual/kernel` | 获取内核配方需要的源码和文件 |
| `bitbake -c unpack virtual/kernel` | 解包或准备获取到的源码 |
| `bitbake -c patch virtual/kernel` | 执行补丁阶段及必要前置任务；当前还会注入 K7 DTS |
| `bitbake virtual/kernel` | 执行内核默认构建流程，包含编译等任务 |
| `bitbake core-image-minimal` | 构建最小系统镜像及其依赖，范围比内核目标更大 |

这些执行任务的命令会修改构建目录，可能下载大量内容。本篇阅读阶段不要求执行。
BitBake 按任务依赖和完成记录决定哪些任务需要运行，不会每次全部从头开始。

几个常见路径变量：

| 变量 | 含义 |
| --- | --- |
| `WORKDIR` | 当前配方的工作目录，通常包括 temp 日志目录等 |
| `S` | 任务使用的源码目录；内核可能位于 work-shared，不要硬猜路径 |
| `B` | 构建目录，可以与源码目录分离 |
| `DEPLOY_DIR_IMAGE` | 面向目标机器的部署产物目录，如内核、DTB、镜像 |

## 10. 如何判断自己真的完成了一步

不要把下面几种结果混为一谈：

| 看到的结果 | 能证明什么 | 不能证明什么 |
| --- | --- | --- |
| repo list 有 meta-kickpi | 清单管理这个项目 | BitBake 已启用该 layer |
| show-layers 有 meta-kickpi | layer 已被加载 | K7 内核已编译 |
| `-e` 中有 K7 的 SRC_URI | 解析结果包含文件 | 文件已注入内核源码 |
| do_patch 成功且源码中有文件 | 源码准备和注入已完成 | DTS 编译通过 |
| 有本次构建的 Image、DTB | 已有对应构建产物 | 板子一定能启动 |
| 板上进入 Shell | 启动路径已基本打通 | 所有外设都正常 |

构建目录中的文件可能属于旧构建，检查结果时要结合日志、配置和时间，不能只看文件名。

## 11. 一次完成的入门练习

不改配置、不重新下载、不启动编译，完成以下检查即可：

```bash
cd /home/ryan/Documents/yocto
repo list
cat .repo/manifests/common/base.xml
cat build/conf/bblayers.conf
cat sources/meta-kickpi/conf/layer.conf
cat sources/meta-kickpi/conf/machine/kickpi-k7.conf

source sources/poky/oe-init-build-env build
bitbake-layers show-layers
bitbake-layers show-appends
MACHINE=kickpi-k7 bitbake -e virtual/kernel | \
    grep -E '^(MACHINE|PN|PV|S|B|WORKDIR|KERNEL_DEVICETREE)='
```

读完后用自己的话回答：

1. 为什么四个仓库可以提供六个已启用的 layer？
2. 添加 manifest 的 project 后，为什么还可能需要修改 BBLAYERS？
3. `meta-kickpi` 目录名和 `kickpi` collection 分别有什么用途？
4. MACHINE、DISTRO、image 目标分别决定什么？
5. 为什么 `bitbake -e virtual/kernel` 成功不代表内核编译成功？
6. K7 DTS 从 layer 进入内核源码，是哪一段配置和任务完成的？

能解释这六点，就可以按 [学习计划第 2 章](../LEARNING.md#2-理解任务从-fetch-到-deploy)
继续学习任务和日志。已有构建结果可以用于观察，不必删除重做。
