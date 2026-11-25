SUMMARY = "KICKPI K7 product image"
LICENSE = "MIT"

# a. 复用 OE-Core 镜像构建任务，由产品包组明确指定装机内容。
# SDK 入口：core-image 继承 image，image 继承 populate_sdk_base。
# 执行 bitbake kickpi-image-product -c populate_sdk 即可生成标准 SDK。
inherit core-image
# SDK 目标端包：IMAGE_INSTALL 进入 PACKAGE_INSTALL，再加入 TOOLCHAIN_TARGET_TASK。
# 因此产品包组及其运行依赖会参与目标 sysroot 的安装。
IMAGE_INSTALL = "packagegroup-k7-product"
# 产品镜像设置：根文件系统语言包和容量。SDK 主机语言包由 SDKIMAGE_LINGUAS 控制。
IMAGE_LINGUAS = ""
IMAGE_ROOTFS_SIZE ?= "8192"

# b. 产品镜像使用显式功能清单，并过滤共享 local.conf 中的免密调试选项。
# 能引入软件包的 IMAGE_FEATURES 也会影响 SDK 目标端包清单。
# 下方移除的是镜像的登录策略。SDK 的开发包和调试包由 SDKIMAGE_FEATURES 控制。
IMAGE_FEATURES = ""
require recipes-core/images/k7-image-features.inc
IMAGE_FEATURES:remove = "empty-root-password allow-empty-password allow-root-login serial-autologin-root"
# OpenSSH 产品镜像在首次 root 串口登录时配置远程维护公钥。
IMAGE_INSTALL:append = "${@bb.utils.contains('IMAGE_FEATURES', 'ssh-server-openssh', ' k7-serial-ssh', '', d)}"

require recipes-core/images/k7-network-config.inc

# 学习镜像的 root 串口密码，可在 local.conf 中覆盖。空值跳过设置。
# 下划线开头的变量不会写入 OE-Core 导出的 testdata.json。
_K7_ROOT_PASSWORD ?= "199612"
do_rootfs[depends] += "shadow-native:do_populate_sysroot"
ROOTFS_POSTPROCESS_COMMAND += "k7_set_root_password"

python k7_set_root_password() {
    import os
    import subprocess

    password = d.getVar("_K7_ROOT_PASSWORD")
    if password:
        chpasswd = os.path.join(d.getVar("STAGING_SBINDIR_NATIVE"), "chpasswd")
        subprocess.run(
            [chpasswd, "-c", "SHA512", "-P", d.getVar("IMAGE_ROOTFS")],
            input=f"root:{password}\n",
            text=True,
            check=True,
        )
}

# BSP 建议安装的整套内核模块只用于验证镜像。产品按需在包组中列出模块。
# 当前 IPK 包管理器在构建镜像和 SDK 时都应用此推荐包过滤规则，强依赖仍会安装。
BAD_RECOMMENDATIONS:append = " kernel-modules"

# c. 共用 K7 启动布局。独立目录中的两个烧录文件沿用 RKDevTool 固定文件名。
# WIC 组盘和 USB 烧录导出由镜像任务完成，populate_sdk 无需生成这些烧录产物。
K7_USB_FLASH_DIR = "usb-flash-k7-product"
require recipes-core/images/k7-image-layout.inc

# d. 产品 SDK 常用配置，按需取消下面示例的注释。
# SDKMACHINE 在 build-k7/conf/local.conf 中设置，当前为 x86_64。
# 目标端开发包放入 AArch64 sysroot。curl-dev 提供 libcurl 的头文件和链接文件。
#TOOLCHAIN_TARGET_TASK:append = " curl-dev"
# 主机端工具使用 nativesdk-* 包。默认已包含 CMake 和 pkg-config，可按需增加 ccache。
#TOOLCHAIN_HOST_TASK:append = " nativesdk-ccache"
# 默认自动补充 dev-pkgs（开发文件）、dbg-pkgs（调试符号）和 src-pkgs（调试源码）。
# 启用发行版 api-documentation 功能时也会补充 doc-pkgs（文档）。
# 只需编译和链接时，可取消自动补充调试符号和源码，保留默认的开发包。
#SDKIMAGE_FEATURES:remove = "dbg-pkgs src-pkgs"
# SDK 主机语言环境默认打包 all，可按需缩小为指定的 locale 列表。
#SDKIMAGE_LINGUAS = "en-us"
# SDK 安装器版本和默认安装目录。外层发布包版本由打包脚本的 --version 单独指定。
#SDK_VERSION = "0.1.0"
#SDKPATHINSTALL = "/opt/kickpi-k7-sdk/${SDK_VERSION}"
