# K7 设备树：文件注入与补丁实验

返回 [学习计划](../LEARNING.md)。

## 收集 K7 DTS

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

### 直接保存 DTS 与使用 patch 的区别

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

本学习仓库允许可读 DTS 和 patch 两套实验长期并存，内容可以不同，无需同步。
记录每次实验使用的模式、内核基线与产物即可；一次构建必须只启用一条路径。
当前已实现的 files 路径由 bbappend 复制四个文件并登记 Makefile 目标。

将来设备树稳定并准备提交到 Linux/Rockchip 内核仓库时，可以在实际内核 Git
工作树中提交这些文件和 Makefile 修改，再用 `git format-patch` 生成一个正式
上游 patch，作为独立的 patch 实验。两种模式无需生成相同的 DTB；如要比较
构建方式本身的影响，再有意使用相同输入做一次对照实验。

### 在实际 linux-rockchip 基线上生成 patch

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

   生成补丁前须临时停用当前 K7 files 注入逻辑，并记录这项本地改动；否则
   `devtool modify` 可能将已经注入的 K7 文件纳入初始基线，导致之后的提交不含
   新增 DTS。检查初始 Git 树确认没有 K7 文件，生成完成后恢复 bbappend。


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

### DTS 与 patch 模式的选择方法

计划使用下面的变量作为唯一选择入口：

```bitbake
KICKPI_DTS_MODE = "files"
```

允许值为：

| 值 | 作用 | 当前状态 |
| --- | --- | --- |
| `files` | 从 `files/kickpi-k7/` 复制可读 DTS/DTSI | 已实现，当前默认方案 |
| `patch` | 应用 `files/patches/` 中的标准内核 patch | 未实现，必须先生成 patch |

patch 模式实现并验证后，可以在 `build/conf/local.conf` 中临时选择：

```bitbake
# 使用可读 DTS/DTSI，适合学习和开发
KICKPI_DTS_MODE = "files"
```

或者：

```bitbake
# 使用标准内核 patch，适合验证上游提交形式
KICKPI_DTS_MODE = "patch"
```

模式选择优先写入 `build/conf/local.conf`，然后查询最终变量确认生效。

检查最终选择值：

```bash
MACHINE=kickpi-k7 bitbake -e virtual/kernel | \
    grep '^KICKPI_DTS_MODE='
```

检查 `SRC_URI` 实际取用了 DTS 还是 patch：

```bash
MACHINE=kickpi-k7 bitbake -e virtual/kernel | \
    grep '^SRC_URI=' | grep -E 'rk3576-kickpi-k7|add-kickpi-k7\.patch'
```

切换模式时，为避免旧源码修改残留，先确认临时源码中没有需要保存的手工改动，
再重新准备源码：

```bash
MACHINE=kickpi-k7 bitbake -c clean virtual/kernel
MACHINE=kickpi-k7 bitbake -c patch virtual/kernel
```

bbappend 最终必须验证 `KICKPI_DTS_MODE` 只能是 `files` 或 `patch`，并保证两套
输入互斥，不能在一次构建中既复制 DTS 又应用包含相同 DTS 的 patch。

> 当前状态：`linux-rockchip_6.1.bbappend` 尚未定义 `KICKPI_DTS_MODE`，实际
> 始终使用可读 DTS/DTSI。现在即使在 `local.conf` 中写成 `patch` 也不会切换；
> 等 patch 文件生成后，再实现并验证选择逻辑。

## 把保存的 DTS 接入 linux-rockchip 6.1

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
