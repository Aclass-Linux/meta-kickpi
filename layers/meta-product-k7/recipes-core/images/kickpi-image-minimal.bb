SUMMARY = "KICKPI K7 mainline U-Boot serial/MMC bring-up image"
LICENSE = "MIT"
require recipes-core/images/core-image-minimal.bb
require conf/include/k7-boot-layout.inc
COMPATIBLE_MACHINE = "^kickpi-k7$"
IMAGE_INSTALL:append = " kernel-modules k7-boot-files"
IMAGE_FSTYPES = "ext4 wic"
WKS_FILE = "kickpi-k7-mainline.wks.in"
WKS_FILE_DEPENDS = "${WKS_FILE_DEPENDS_DEFAULT}"
WKS_SEARCH_PATH:append = ":${K7_PRODUCT_LAYERDIR}/wic"
# This image deliberately does not inherit rockchip-image: its Android-style
# boot.img/uboot.img/update.img layout is incompatible with this boot flow.
do_image_wic[depends] += "virtual/bootloader:do_deploy"
