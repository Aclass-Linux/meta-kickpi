# K7 BSP 与产品层

本仓库同时提供两个独立 Yocto layer。Git 仓库负责版本管理，layer 负责构建职责；
两者不要求一一对应。当前保留 BSP 的已有路径，产品层放在 layers/meta-product-k7，
避免为首次分层新增远端仓库和迁移 BSP 的所有文件。

## 职责

| 路径 | 内容 | 修改时机 |
| --- | --- | --- |
| 仓库根 conf/machine | K7 硬件、CPU、串口、provider | 板型或硬件支持变化 |
| 仓库根 recipes-bsp / recipes-kernel | U-Boot、固件输入、Linux 配置和 DT | BSP 适配或升级 |
| layers/meta-product-k7/recipes-core/images | 产品镜像的软件选择、格式和 SDK 构建入口 | 产品内容变化 |
| layers/meta-product-k7/recipes-core/boot-config | k7-boot-files 包，安装 DTB、boot.scr、uInitrd 和 armbianEnv，依赖 kernel-image | 启动配置变化 |
| layers/meta-product-k7/conf/include | GPT 与 armbianEnv 共用的根分区布局 | 分区方案变化 |
| layers/meta-product-k7/wic | WIC 布局 | 存储交付方式变化 |
| layers/meta-product-k7/conf/templates | 开发构建模板 | 团队标准构建配置变化 |

产品 layer 依赖 core 和 kickpi；BSP 本身不依赖产品 layer。
应用配方和 packagegroup 按实际需求增加，本轮不创建空包组或专用发行版。
rkbin/DDR 仍由原 U-Boot 配方使用，后续再独立成固件配方。

## 已有工作区

其他已有构建目录应确认 BBLAYERS 包含：

```bitbake
BBLAYERS ?= " \
  ${TOPDIR}/../sources/openembedded-core/meta \
  ${TOPDIR}/../sources/meta-openembedded/meta-oe \
  ${TOPDIR}/../sources/meta-kickpi \
  ${TOPDIR}/../sources/meta-kickpi/layers/meta-product-k7 \
  "
```

保留个人 local.conf 的缓存、并发等配置，不用新模板覆盖已有文件。不需要清缓存。

```bash
cd /home/ryan/Yocto/workspace
source sources/openembedded-core/oe-init-build-env build-k7
bitbake-layers show-layers
bitbake-layers show-recipes kickpi-image-minimal k7-boot-files
bitbake kickpi-image-minimal
bitbake kickpi-image-minimal -c populate_sdk
```

镜像 target 与产物目录保持原名称。两个 layer 都必须启用；只启用 BSP 时没有产品镜像配方。

## 新建构建目录

在新的终端，从同步完成的工作区根目录执行：

```bash
cd /home/ryan/Yocto/workspace
export TEMPLATECONF="$PWD/sources/meta-kickpi/layers/meta-product-k7/conf/templates/k7-upstream"
source sources/openembedded-core/oe-init-build-env build-k7-new
unset TEMPLATECONF
```

模板只用于初始化缺失的配置，不会自动重写旧 build/conf。
模板明确选择 kickpi-k7 和 x86_64 SDK 主机，并允许开发串口使用空 root 密码；
它不是发布认证策略，正式产品需要单独设计账号。SDKMACHINE 与目标 ARM64 架构不同。
按需在 local.conf 设置 DL_DIR / SSTATE_DIR；不把个人绝对路径存入团队模板。

## 启动文件与任务依赖

k7-boot-files 的 do_install 显式依赖 virtual/kernel:do_deploy，复制配套 DTB，
将官方 boot.cmd 编译为 boot.scr，配套生成 uInitrd 和 armbianEnv 后打包进 rootfs。Image 由其显式依赖的标准 kernel-image 包提供，
避免多个包拥有同一个 /boot/Image。原镜像 rootfs postprocess 不再直接复制文件。
WIC 任务依赖 virtual/bootloader:do_deploy。这样文件归属、安装内容和依赖可以分别检查。

WKS 与启动文件配方共同 require conf/include/k7-boot-layout.inc。
串口设备与波特率来自 machine；根分区 PARTUUID、文件系统、loader 偏移和根分区对齐值来自产品布局。
本轮保持原有 32KiB loader、16MiB 根分区起点与 ext4，不同时改变存储协议。
固定 PARTUUID 的多个介质不能据此自动区分，真机验证仍需核对实际启动介质。

## 验收

当前镜像已由用户验证进入 Linux/OpenEmbedded 用户态。版本、布局、已知告警和后续迁移边界统一见 [当前可启动方案](k7-official-boot.md)。
