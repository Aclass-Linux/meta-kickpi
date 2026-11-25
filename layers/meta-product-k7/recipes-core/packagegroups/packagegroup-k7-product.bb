SUMMARY = "Base runtime packages for the KICKPI K7 product image"
LICENSE = "MIT"

# 此包组包含 K7 专用启动包，按机器架构打包，且仅在 kickpi-k7 上启用。
PACKAGE_ARCH = "${MACHINE_ARCH}"
COMPATIBLE_MACHINE = "^kickpi-k7$"

# 包组通过包依赖组织装机内容，自身无需安装文件。
inherit packagegroup

# 产品镜像通过 IMAGE_INSTALL 选中此包组。生成该镜像的 SDK 时，
# 此包组及其运行依赖也参与目标 sysroot 的安装。
#
# packagegroup-core-boot 提供 OE-Core 的基础用户空间，包括 init、设备管理和基本命令。
# k7-boot-files 安装 extlinux.conf、Linux DTB 和 initramfs，并依赖 kernel-image。
#
# 在 RDEPENDS 中逐行加入产品必需的服务、运行库和已验证的 kernel-module-* 包。
# 允许缺省的推荐包可用 RRECOMMENDS 声明。仅供开发的库或工具可在产品镜像配方中配置 SDK。
RDEPENDS:${PN} = " \
    packagegroup-core-boot \
    k7-boot-files \
"
