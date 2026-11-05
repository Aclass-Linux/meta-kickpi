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
| layers/meta-product-k7/recipes-core/boot-config | k7-boot-files 包，安装 DTB/extlinux，依赖 kernel-image | 启动配置变化 |
| layers/meta-product-k7/conf/include | GPT 与 extlinux 共用的根分区布局 | 分区方案变化 |
| layers/meta-product-k7/wic | WIC 布局 | 存储交付方式变化 |
| layers/meta-product-k7/conf/templates | 开发构建模板 | 团队标准构建配置变化 |

产品 layer 依赖 core 和 kickpi；BSP 本身不依赖产品 layer。
应用配方和 packagegroup 按实际需求增加，本轮不创建空包组或专用发行版。
rkbin/DDR 仍由原 U-Boot 配方使用，后续再独立成固件配方。

## 已有工作区

本轮已更新本机 build-k7/conf/bblayers.conf。其他已有构建目录应确认 BBLAYERS 包含：

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
cd ~/Yocto/workspace-k7
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
cd ~/Yocto/workspace-k7
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
从模板生成 extlinux 后打包进 rootfs。Image 由其显式依赖的标准 kernel-image 包提供，
避免多个包拥有同一个 /boot/Image。原镜像 rootfs postprocess 不再直接复制文件。
WIC 任务依赖 virtual/bootloader:do_deploy。这样文件归属、安装内容和依赖可以分别检查。

WKS 与启动文件配方共同 require conf/include/k7-boot-layout.inc。
串口设备与波特率来自 machine；根分区 PARTUUID、文件系统、loader 偏移和根分区对齐值来自产品布局。
本轮保持原有 32KiB loader、16MiB 根分区起点与 ext4，不同时改变存储协议。
固定 PARTUUID 的多个介质不能据此自动区分，真机验证仍需核对实际启动介质。

## 版本发布和同步顺序

先推送 meta-kickpi 的 wrynose 分支，再推送指向该提交的 SDK-Yocto main。
manifest 使用完整 SHA；远端尚无该 SHA 时，其他机器无法同步它。
本轮认证失败的处理与实际推送结果以执行记录为准，不能把本地 commit 当作已经发布。

两次推送成功后，其他已有工作区执行：

```bash
cd ~/Yocto/workspace-k7
repo status
# 确认没有需保留的未提交修改后再同步；不使用 --force-sync 或 git reset --hard。
repo sync
python3 .repo/manifests/ci/verify-checkout.py --entry products/kickpi-k7/integration.xml
```

同步后按上面的说明添加产品层，或用模板创建新构建目录。
SDK 安装包、WIC、tmp 和缓存保留在制品/构建目录，不提交到 Git。
硬件启动、SDK 安装和应用运行的状态与编译结果分别记录。

## 本轮验证结果

2026-11-05：最终镜像 4758 项任务全部成功，4727 项复用；配套 SDK 7167 项任务全部成功。
实际 WIC 的 GPT CRC、32KiB loader、16MiB 根分区起点、PARTUUID、Image 和 DTB 已核对。
新模板生成、layer 路径、启动文件包归属与 kernel-image 运行依赖检查通过。
摘要与安装器 SHA256 见 validation/2026-11-05/product-split/validation.json。
构建主机 glibc 2.44 高于 uninative 2.43，构建系统禁用了 uninative；本轮构建仍成功。
SDK 安装/应用运行和真机启动未执行，Git 推送仍需可用的远端认证。
