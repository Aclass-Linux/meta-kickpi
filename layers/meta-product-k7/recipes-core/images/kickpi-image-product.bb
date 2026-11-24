SUMMARY = "KICKPI K7 product image"
LICENSE = "MIT"

# a. 复用 OE-Core 镜像构建任务，由产品包组明确指定装机内容。
inherit core-image
IMAGE_INSTALL = "packagegroup-k7-product"
IMAGE_LINGUAS = ""
IMAGE_ROOTFS_SIZE ?= "8192"

# b. 产品镜像使用显式功能清单，并过滤共享 local.conf 中的免密调试选项。
IMAGE_FEATURES = ""
IMAGE_FEATURES:remove = "empty-root-password allow-empty-password allow-root-login serial-autologin-root"

# BSP 建议安装的整套内核模块只用于验证镜像。产品按需在包组中列出模块。
BAD_RECOMMENDATIONS:append = " kernel-modules"

# c. 共用 K7 启动布局。独立目录中的两个烧录文件沿用 RKDevTool 固定文件名。
K7_USB_FLASH_DIR = "usb-flash-k7-product"
require recipes-core/images/k7-image-layout.inc
