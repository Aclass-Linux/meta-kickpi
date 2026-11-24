# K7 BSP 与产品层

仓库提供两个 Yocto layer。BSP 提供独立可构建的板级验证镜像，产品层在 BSP 上定义出厂镜像。两层分别在 `BBLAYERS` 启用。

| 层 | 构建目标 | 职责 |
| --- | --- | --- |
| `meta-kickpi` | `kickpi-image-minimal` | 提供 Linux、U-Boot、固件和默认启动布局。验证镜像安装完整内核模块集，便于检查外设。 |
| `meta-product-k7` | `kickpi-image-product` | 通过 `packagegroup-k7-product` 定义产品装机包和所需内核模块。产品镜像过滤开发配置里的免密登录选项。 |

## 独立构建 BSP 镜像

从 `workspace` 创建与 `sources` 同级的新构建目录。模板只启用 OE-Core 和 K7 BSP。

```bash
TEMPLATECONF="$PWD/sources/meta-kickpi/conf/templates/k7-bsp" \
    source sources/openembedded-core/oe-init-build-env build-k7-bsp
bitbake-layers show-layers
bitbake kickpi-image-minimal
```

开发模板允许空密码登录，适用于板级验证环境。BSP 镜像继续使用原有构建目标，已启用两层的 `build-k7` 也可直接构建它。

## 构建产品镜像

在启用了 `meta-product-k7` 的 `build-k7` 中运行 `bitbake kickpi-image-product`。新建产品构建目录时使用 `layers/meta-product-k7/conf/templates/k7-upstream`。需要交叉开发 SDK 时，运行 `bitbake kickpi-image-product -c populate_sdk`。模板只初始化缺失的配置。

产品软件和运行库添加到产品包组。首次接入外设时可先用 BSP 镜像验证硬件，再把对应 `kernel-module-*` 软件包装入产品镜像。

## 共用启动布局与独立烧录产物

BSP 的 `recipes-core/images/k7-image-layout.inc` 提供默认 WIC 布局和烧录导出任务。两张镜像都使用 BSP 的 `k7-boot-files` 安装 extlinux 配置、Linux DTB 和 initramfs，并由 `kernel-image` 包提供内核。WIC 和 extlinux 共用 `conf/include/k7-boot-layout.inc` 中的根分区 PARTUUID。使用同一默认 PARTUUID 的 SD 卡和 eMMC 同时在线时，应核对板子实际选用的根分区。

在各自构建目录的 `tmp/deploy/images/kickpi-k7/` 下，BSP 镜像导出到 `usb-flash-k7/`，产品镜像导出到 `usb-flash-k7-product/`。两个目录都使用固定文件名 `k7-rockchip-loader.bin` 和 `kickpi-k7-emmc.img`。

验收时先确认对应镜像成功构建，再检查冷启动串口日志、根分区以及所需外设。
