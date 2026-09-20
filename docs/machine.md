# Machine 配置与环境解析

返回 [学习计划](../LEARNING.md)。

## 以 RK3576 EVB 为模板

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

## 检查 machine 是否被识别

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
   保留本实验需要检查的变量。

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

machine 解析实验 先使用 `-e` 检查 machine、provider 和 DTB 变量，避免把 BitBake 配置
错误带到耗时更长的内核编译阶段。设备树编译实验 把 K7 DTS 加入内核源码后，再运行
真正的内核构建。
